# Phase 6 — JSON Content Extraction (M1 + M2)

**Date:** 2026-04-30
**Issue:** [#24 — Extract hardcoded static data into JSON assets](https://github.com/tahuffman1s/RealmsAndroid/issues/24)
**Scope:** Milestones M1 (infrastructure) and M2 (pure-string content)

## Goal

Stand up a `ContentRepository` that loads versioned JSON assets at app startup, then migrate the lowest-risk static content — pure-string pools — out of Kotlin source into JSON. Establish patterns and CI guarantees that later milestones (M3-M7) can lean on.

Saves are explicitly out of scope. Save state stays in its current ZIP/JSON format under `SaveRofZip.kt` / `SaveStore.kt`. This epic concerns *authored static content* only.

## Decisions (locked during brainstorming)

| Decision | Choice |
|---|---|
| Failure mode for malformed JSON | Fail fast / crash on startup. CI test parses every shipped file. No runtime fallback. |
| Load timing | Eager at app startup (`Application.onCreate`). Synchronous in-memory thereafter. |
| Migration pattern | Old symbols become thin facades that read from `ContentRepository`. M7 (later phase) deletes them. |
| Asset location | `app/src/main/assets/content/` — bundled in APK, accessible via `AssetManager`. |
| Schema versioning | Single integer in `schema-version.json`. Mismatch with app's expected version → fail fast with a diagnostic. Phase 6 ships `version: 1`. |

## M1 — Infrastructure

### New module: `data/content/`

```
app/src/main/kotlin/com/realmsoffate/game/data/content/
├── ContentRepository.kt    // singleton, eager load, public read API
├── ContentLoader.kt        // AssetManager wiring + kotlinx.serialization parse
├── ContentSchema.kt        // @Serializable data classes for every JSON file
└── ContentException.kt     // sealed: SchemaVersionMismatch, MalformedJson, MissingFile
```

### `ContentRepository` shape

```kotlin
object ContentRepository {
    fun initialize(context: Context)  // called from Application.onCreate

    // M2 read API — pure string pools
    val factionTypes: List<String>
    val factionAdjectives: List<String>
    val factionNouns: List<String>
    val npcFirsts: List<String>
    val npcTitles: List<String>
    val npcRoles: List<String>
    val eraLabels: List<String>
    val rumors: List<String>
    val economyStates: List<String>
    val exports: List<String>
    val imports: List<String>
    val governmentForms: List<String>
    val successionTypes: List<String>
    val rulerTraits: List<String>
    val moods: List<String>
    val goals: List<String>
    val dispositions: List<String>

    val backstoryOrigins: List<String>
    val backstoryMotivations: List<String>
    val backstoryDarkSecrets: List<String>
    val backstoryEnemyArchetypes: List<String>
    val backstoryLostItems: List<String>
    val backstoryBonds: List<String>
    val backstoryFlaws: List<String>
    val backstoryProphecies: List<String?>  // nullable preserved from source

    val locationTemplates: List<LocTemplate>  // type, emoji, names
    val playerNames: List<String>
}
```

Backing fields are `lateinit` populated once during `initialize`. Every getter returns the cached list. No locking — access is read-only after init.

### Failure contract

`ContentRepository.initialize` is the only function in the module that can throw. It throws `ContentException` subtypes, all of which are unrecoverable. Possible causes:

- **`MissingFile`** — an expected JSON file is not present in `assets/content/`. Indicates a packaging bug.
- **`MalformedJson`** — a file exists but doesn't parse, or doesn't match its `@Serializable` schema. Indicates a content/schema regression.
- **`SchemaVersionMismatch`** — `schema-version.json` doesn't match `ContentSchema.EXPECTED_VERSION`. Indicates an out-of-sync content drop.

Each exception includes the offending file path and the underlying parse error message.

`Application.onCreate` does not catch these. The crash terminates the app immediately. The CI test below ensures release builds never ship a broken bundle.

### CI test

`app/src/test/kotlin/com/realmsoffate/game/data/content/ContentRepositoryTest.kt` (Robolectric):

- Boot Robolectric application context, call `ContentRepository.initialize`.
- Assert each public list is non-empty.
- Assert `schema-version.json` parses to the expected integer.
- One file at a time, inject a deliberately malformed copy and assert the correct `ContentException` is raised.

This test runs as part of `gradle test` and is the only enforcement gate for shipped JSON quality.

### Asset layout (Phase 6)

```
app/src/main/assets/content/
├── schema-version.json
├── lore/
│   ├── factions.json          // types, adjectives, nouns
│   ├── npc-names.json         // firsts, titles, roles
│   ├── world.json             // eraLabels, rumors
│   └── descriptors.json       // econ states, exports, imports, gov forms,
│                              //   successions, ruler traits, moods, goals,
│                              //   dispositions
├── backstory/
│   └── fragments.json         // origins, motivations, dark secrets, enemy
│                              //   archetypes, lost items, bonds, flaws,
│                              //   prophecies
├── world/
│   └── location-templates.json
└── names/
    └── player.json
```

Bundling related lists into a single file (`factions.json` holds three lists) reduces parse calls without sacrificing editor friendliness — writers still see clean named sections.

### `Application` wiring

A new `RealmsApplication : android.app.Application` overrides `onCreate` to call `ContentRepository.initialize(this)` before any activity touches game logic. Manifest `android:name="com.realmsoffate.game.RealmsApplication"`.

If a `RealmsApplication` already exists, extend it. (Spot-check during implementation.)

## M2 — Pure-string content migration

### What moves

Every list below is currently a `private val ... = listOf(...)` of strings (or one nullable-string list). All move to JSON; their declarations stay as facades.

**`LoreGen.kt` (lines indicative, may shift):**
- `FACTION_TYPES` / `FACTION_ADJS` / `FACTION_NOUNS` → `lore/factions.json`
- `NPC_FIRSTS` / `NPC_TITLES` / `NPC_ROLES` → `lore/npc-names.json`
- `ERA_LABELS` / `RUMORS` → `lore/world.json`
- `ECON_STATES` / `EXPORTS` / `IMPORTS` / `GOV_FORMS` / `SUCCESSIONS` / `RULER_TRAITS` / `MOODS` / `GOALS` / `DISPOSITIONS` → `lore/descriptors.json`

**`BackstoryGen.kt`:**
- `ORIGINS` / `MOTIVATIONS` / `DARK_SECRETS` / `ENEMY_ARCHETYPES` / `LOST_ITEMS` / `BONDS` / `FLAWS` / `PROPHECIES` → `backstory/fragments.json`. `PROPHECIES` keeps its `List<String?>` shape; null entries serialize as JSON `null`.

**`WorldGen.kt`:**
- `locTemplates` (list of `LocTemplate(type, emoji, names)`) → `world/location-templates.json`. Slightly structured but trivially serializable.

**`data/PlayerNamePool.kt`:**
- `NAMES` → `names/player.json`. The whole file collapses to a one-line facade.

### What stays in Kotlin (out of scope for M2)

- **Lambda-based pools.** `WORLD_NAME_PATTERNS` and the `*_EVENTS` lists in `LoreGen.kt` are `List<(Random) -> String>` / `List<(String, ...) -> String>` — they are *behavior*, not data. They stay until later milestones decide whether to express them as JSON templates or leave them in code.
- **Inline `listOf("...", "...").random()` expressions** scattered through `LoreGen.kt` faction/ruler builders. These are local one-shots and will be addressed when M3 or a follow-up tackles the structured factions/rulers domain. M2 does not touch them.

### Facade pattern (example)

Before:
```kotlin
// LoreGen.kt
private val NPC_FIRSTS = listOf("Aenor", "Brisa", /* ... */)
```

After M2:
```kotlin
// LoreGen.kt
private val NPC_FIRSTS: List<String> get() = ContentRepository.npcFirsts
```

For top-level package-internal vals like `NPC_FIRSTS`, the conversion is a one-line getter. No call sites change. M7 later deletes the facades and rewires callers.

`PlayerNamePool.NAMES` is the simplest case — its body becomes `get() = ContentRepository.playerNames`.

`WorldGen.locTemplates` references the `LocTemplate` data class. Make `LocTemplate` itself `@Serializable` and use it as both the JSON DTO and the runtime type — no separate DTO, no mapping layer. (The data class is a plain POD; its package may shift if needed to satisfy module visibility.)

### Parity guarantees

Each migrated list ships with a unit test asserting `ContentRepository.<list>` equals the historical Kotlin literal it replaces. Concretely: copy the original literal into the test as the expected value, assert equality. This pins the migration down to exact-match content for Phase 6.

(Epic-level seeded-output parity tests are deferred to M7. M2's pinning tests are sufficient to prove no content was lost or reordered during the move.)

## Architecture

```
Application.onCreate
    └─> ContentRepository.initialize(context)
            ├─> ContentLoader.readAsset("content/schema-version.json") → assert version
            ├─> ContentLoader.parse<FactionsJson>("content/lore/factions.json")
            ├─> ... one parse per file ...
            └─> populate lateinit fields

LoreGen.NPC_FIRSTS  ──facade──>  ContentRepository.npcFirsts
BackstoryGen (uses ORIGINS, etc.) ──facade──>  ContentRepository.backstory*
WorldGen.locTemplates  ──facade──>  ContentRepository.locationTemplates
PlayerNamePool.NAMES  ──facade──>  ContentRepository.playerNames
```

No call site outside `data/content/` parses JSON directly. No code outside `Application.onCreate` calls `initialize`.

## Testing

| Test | Purpose |
|---|---|
| `ContentRepositoryTest.parsesAllShippedAssets` | Boot context, init repo, assert lists non-empty. |
| `ContentRepositoryTest.detectsSchemaVersionMismatch` | Inject mismatched version, assert `SchemaVersionMismatch`. |
| `ContentRepositoryTest.detectsMalformedJson` | Inject malformed file, assert `MalformedJson` with file path. |
| `ContentRepositoryTest.detectsMissingFile` | Remove file from override, assert `MissingFile`. |
| `LoreContentParityTest` | Each migrated list equals its historical Kotlin literal. |
| `BackstoryContentParityTest` | Same for `BackstoryGen` lists, including nullable prophecies. |
| `WorldContentParityTest` | `locationTemplates` equals historical `locTemplates`. |
| `PlayerNamesParityTest` | `playerNames` equals historical `PlayerNamePool.NAMES`. |

All run under `gradle test` (Robolectric for the repo tests, plain JVM for parity).

## Build / size

JSON content for M2 is on the order of single-digit KB per file, well under the epic's ≤50 KB regression budget.

## What this phase does NOT do

- Touch `Races.kt`, `Classes.kt`, `Spells.kt`, `Scenarios.kt`, `Mutations.kt`, `WorldEvents.kt`, `Feats.kt`. Those are M3-M5.
- Add hot-reload from external storage. That is M6.
- Delete the facade declarations. That is M7.
- Change save formats or any runtime game state. Out of epic scope entirely.
- Address Issue #28 (auto-update system). Separate phase.

## Acceptance criteria

- [ ] `ContentRepository` exists, populated by `RealmsApplication.onCreate`.
- [ ] All M2 lists live in JSON under `app/src/main/assets/content/`.
- [ ] Old Kotlin declarations remain as one-line facades that delegate to `ContentRepository`.
- [ ] `schema-version.json` ships `version: 1`. Mismatch crashes with a clear diagnostic.
- [ ] CI test parses every shipped JSON file and asserts non-empty.
- [ ] Parity tests assert each migrated list equals its historical literal.
- [ ] `gradle test` passes. `gradle assembleDebug` passes. App boots on emulator and a new game produces narrator output indistinguishable from pre-phase behavior (manual P0+P1 verification per debug-bridge-test-procedures).
- [ ] APK size delta ≤ 50 KB.

## Open follow-ups (not Phase 6)

- M3 — Races, Classes, Feats. Tighter schemas, character creation impact.
- M4 — Spells. Mechanical fields (damage, save DC, slot level).
- M5 — Scenarios, Mutations, World Events. Includes the lambda-based pools left in M2.
- M6 — Debug-only hot-reload from `Context.filesDir`.
- M7 — Delete facades, rewire callers, ship the seeded-output parity test.
- Issue #28 — Auto-update system, deferred to a later phase.
