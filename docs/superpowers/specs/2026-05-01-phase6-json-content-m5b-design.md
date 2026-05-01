# Phase 6 — JSON Content Extraction (M5b)

**Date:** 2026-05-01
**Issue:** [#24 — Extract hardcoded static data into JSON assets](https://github.com/tahuffman1s/RealmsAndroid/issues/24)
**Scope:** Milestone M5b — WorldEvents + LoreGen historical events
**Builds on:** M1+M2, M3, M4, M5a

## Goal

Finish the second half of the original M5 scope: move the 19-entry `WorldEvents` weighted template pool and the five LoreGen historical-event lambda lists (`PRIMORDIAL_EVENTS`, `ANCIENT_EVENTS`, `MEDIEVAL_EVENTS`, `DARK_AGE_EVENTS`, `RECENT_EVENTS`) out of Kotlin source into JSON.

These are the lambda-based pools deferred all the way back from M2. The split between M5a (Mutations + Scenarios) and M5b (this) lets each PR stay reviewable.

`WORLD_NAME_PATTERNS` in `LoreGen.kt` (eight inline-list-driven generators) is **out of scope** here — different shape, smaller payoff. It can fold into M6 or stay as code.

## Decisions

### WorldEvents

The runtime template carries two lambdas:
- `needs: (WorldLore, WorldMap) -> Boolean` — eligibility predicate
- `gen: (WorldLore, WorldMap, Int) -> Triple<icon, title, prompt>` — picks random objects (faction/NPC/loc) and produces a `(icon, title, prompt)` triple.

Approach: split each template into JSON-side **content** (icon string, title template, prompt template, weight) plus **context-tag** strings naming the eligibility predicate and the random-context picker. Both registries live in `WorldEvents.kt` keyed on the tag strings. Runtime joins them.

| JSON field | Type | Purpose |
|---|---|---|
| `id` | String | Stable identifier; lets parity tests pin entries by id rather than ordinal. |
| `weight` | Int | Forwarded to weighted pick. |
| `needs` | String | Tag like `factions:>=2`, `npcs:>=1`, `loc:typed-cave`, `always`. Resolved via a code-side `needsRegistry`. |
| `context` | String | Tag like `none`, `faction`, `faction-pair`, `faction-with-loc`, `faction-with-typed-loc`, `faction-pair-with-typed-loc`, `faction-with-discovered-loc`, `npc`, `loc`, `loc-typed-cave`. Resolved via a code-side `contextRegistry`. The picker returns a `Map<String, String>` for interpolation. |
| `icon` | String | Static. Goes straight into the `WorldEvent.icon` field. |
| `title` | String | Template with `{f.name}`, `{n.name}`, `{loc.name}` etc. placeholders, interpolated with the context map. |
| `prompt` | String | Same as title — full text body. |

Both registries use a small fixed enum set; adding a JSON entry with an unknown tag fails fast at first access (mirrors M3/M5a registry-drift checks).

The 19 templates require:
- **needs tags:** `always`, `factions:>=1`, `factions:>=2`, `npcs:>=1`, `loc:>=1`, `loc:typed-cave-dungeon-ruins`.
- **context tags:** `none`, `faction`, `faction-pair`, `faction-with-loc`, `faction-with-loc-discovered-pref`, `faction-with-typed-town-city`, `faction-pair-with-typed-town-city`, `npc`, `loc`, `loc-typed-cave-dungeon-ruins`.

### LoreGen historical events

Five `List<(String, ...) -> String>` lambda lists. `PRIMORDIAL_EVENTS` is `(loc) -> String`; the other four are `(f, n, loc) -> String`. All five are simple template-string lists with `{loc}` / `{f}` / `{n}` placeholders.

Single asset file `world/historical-events.json`:

```json
{
  "primordial": [ "The gods shaped the land around **{loc}**.", ... ],
  "ancient":   [ "The **{f}** was founded by {n}...", ... ],
  "medieval":  [ ... ],
  "darkAge":   [ ... ],
  "recent":    [ ... ]
}
```

`LoreGen` swaps the five `private val *_EVENTS` lambda lists for top-level facade properties that read `ContentRepository.historicalEvents.<era>` and interpolate at the call site (the four call sites already pick a random template and apply it to `(f, n, loc)`).

### Schema

Bump `CONTENT_SCHEMA_VERSION` 4 → 5.

## What moves

```
app/src/main/assets/content/
├── schema-version.json                 (bump → {"version": 5})
└── world/
    ├── world-events.json               (NEW — 19 entries)
    └── historical-events.json          (NEW — primordial 6, ancient 8, medieval 10, dark_age 8, recent 12)
```

Source-side:

```
app/src/main/kotlin/com/realmsoffate/game/
├── data/content/
│   ├── ContentSchema.kt                (bump version, add WorldEventDto + HistoricalEventsDto)
│   ├── ContentLoader.kt                (parse 2 new files)
│   └── ContentRepository.kt            (expose worldEventTemplates, historicalEvents)
└── game/
    ├── WorldEvents.kt                  (gen lambda → registry; reads from ContentRepository)
    └── LoreGen.kt                      (5 *_EVENTS lambda lists → facades over ContentRepository.historicalEvents.<era>)
```

Tests:

```
app/src/test/kotlin/com/realmsoffate/game/data/content/
├── ContentRepositoryTest.kt            (add non-empty asserts for both)
└── M5bContentParityTest.kt             (NEW — pin both lists + render-template sample)
```

## Schema additions

```kotlin
internal const val CONTENT_SCHEMA_VERSION = 5

@Serializable
data class WorldEventTemplateDto(
    val id: String,
    val weight: Int,
    val needs: String,
    val context: String,
    val icon: String,
    val title: String,
    val prompt: String,
)

@Serializable
internal data class WorldEventsDto(val list: List<WorldEventTemplateDto>)

@Serializable
data class HistoricalEvents(
    val primordial: List<String>,
    val ancient: List<String>,
    val medieval: List<String>,
    val darkAge: List<String>,
    val recent: List<String>,
)
```

`ContentRepository`:

```kotlin
val worldEventTemplates: List<WorldEventTemplateDto> get() = bundle.worldEvents.list
val historicalEvents: HistoricalEvents get() = bundle.historicalEvents
```

## WorldEvents.kt rewrite sketch

```kotlin
private val needsRegistry: Map<String, (WorldLore, WorldMap) -> Boolean> = mapOf(
    "always"                          to { _, _ -> true },
    "factions:>=1"                    to { l, _ -> l.factions.isNotEmpty() },
    "factions:>=2"                    to { l, _ -> l.factions.size >= 2 },
    "npcs:>=1"                        to { l, _ -> l.npcs.isNotEmpty() },
    "loc:>=1"                         to { _, wm -> wm.locations.isNotEmpty() },
    "loc:typed-cave-dungeon-ruins"    to { _, wm -> wm.locations.any { it.type in setOf("dungeon", "cave", "ruins") } },
)

private val contextRegistry: Map<String, (WorldLore, WorldMap, Random) -> Map<String, String>> = mapOf(
    "none" to { _, _, _ -> emptyMap() },
    "faction" to { l, _, r ->
        val f = l.factions.random(r)
        mapOf("f.name" to f.name)
    },
    "faction-pair" to { l, _, r ->
        val f1 = l.factions.random(r)
        val f2 = l.factions.filter { it.name != f1.name }.randomOrNull(r) ?: f1
        mapOf("f1.name" to f1.name, "f2.name" to f2.name)
    },
    // ... etc for the other 7 context tags
)

fun maybeGenerate(...): WorldEvent? {
    // weighted-pick + cooldown unchanged
    val tpl = chosen
    val needs = needsRegistry[tpl.needs] ?: error("unknown needs tag '${tpl.needs}'")
    val context = contextRegistry[tpl.context] ?: error("unknown context tag '${tpl.context}'")
    val rng = Random
    val ctx = context(lore, wm, rng)
    val title = Templates.interpolate(tpl.title, ctx)
    val prompt = Templates.interpolate(tpl.prompt, ctx)
    return WorldEvent(icon = tpl.icon, title = title, text = prompt, prompt = prompt, turn = currentTurn)
}
```

## LoreGen.kt diffs

Replace each `private val PRIMORDIAL_EVENTS = listOf<(String) -> String>(...)` with a getter:

```kotlin
private val PRIMORDIAL_EVENTS: List<String> get() = ContentRepository.historicalEvents.primordial
```

Call sites change from `PRIMORDIAL_EVENTS.shuffled(rand).take(3).map { it(loc) }` to:

```kotlin
PRIMORDIAL_EVENTS.shuffled(rand).take(3).map { Templates.interpolate(it, mapOf("loc" to firstLoc)) }
```

Same shape for the other four. Three call sites in `LoreGen.generate` switch from `lambda(f.name, n, loc)` to `Templates.interpolate(template, mapOf("f" to f.name, "n" to n, "loc" to loc))`.

## Tests

| Test | Purpose |
|---|---|
| `ContentRepositoryTest.parsesAllShippedAssets` | Add non-empty asserts for `worldEventTemplates`, `historicalEvents.*`. |
| `M5bContentParityTest.world events match historical` | `ContentRepository.worldEventTemplates` equals expected list (19 entries). |
| `M5bContentParityTest.historical events match historical` | All five lists equal historical Kotlin literals (with `{}` placeholders). |
| `M5bContentParityTest.world event needs and context tags resolve` | Every template's needs/context tag is present in their respective registry. |
| `M5bContentParityTest.world event sample renders correctly` | Pin a known event id (e.g. `unexpected_alliance`) — fixed seed, fixed lore — and assert the rendered title + prompt match the historical lambda's output. |

## Acceptance

- [ ] `ContentRepository.worldEventTemplates`, `.historicalEvents.{primordial,ancient,medieval,darkAge,recent}` exposed and non-empty.
- [ ] Both new asset files ship with full historical content; schema version is `{"version": 5}`.
- [ ] `WorldEvents.maybeGenerate` returns the same shape `WorldEvent` it always did, driven by JSON templates and tag registries.
- [ ] `LoreGen.generate` produces lore where historical entries are interpolated from JSON templates instead of in-source lambdas.
- [ ] All M5b parity tests pass.
- [ ] `gradle test`, `gradle assembleDebug` pass.
- [ ] App boots; `/macro/new-game` works (does not exercise these lambdas directly because the macro short-circuits world-gen, but asset load must not crash).
- [ ] APK delta ≤ 50 KB (epic budget).

## Out of scope

- `WORLD_NAME_PATTERNS` in `LoreGen.kt`. Defer.
- Hot-reload (M6).
- Facade deletion + epic seeded-output parity (M7).
