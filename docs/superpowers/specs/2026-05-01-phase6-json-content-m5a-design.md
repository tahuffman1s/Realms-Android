# Phase 6 — JSON Content Extraction (M5a)

**Date:** 2026-05-01
**Issue:** [#24 — Extract hardcoded static data into JSON assets](https://github.com/tahuffman1s/RealmsAndroid/issues/24)
**Scope:** Milestone M5a — Mutations + Scenarios
**Builds on:** M1+M2, M3, M4

## Why split M5

The original M5 scope is "Scenarios + Mutations + World Events". WorldEvents and the LoreGen historical-event lambdas need a richer template engine (per-event runtime context: pick-a-faction, pick-2-distinct-factions, pick-a-typed-location). Splitting:

- **M5a** (this milestone) — Mutations and Scenarios. Both are simple: Mutations are pure data; Scenarios have one prompt-template lambda with six fixed placeholders (`race`, `class`, `loc.icon`, `loc.name`, `nearby`, `lore`) and an optional character-modifier lambda used by 5 of 24 entries.
- **M5b** (next milestone) — WorldEvents (19 weighted templates with random-faction / random-NPC / random-location pickers) and LoreGen historical events (PRIMORDIAL / ANCIENT / MEDIEVAL / DARK_AGE / RECENT lambda lists, simpler `(f, n, loc)` interpolation).

`WORLD_NAME_PATTERNS` in `LoreGen.kt` stays in code for now — 8 generators each with multiple inline word lists. Different shape, smaller payoff. Defer to M5c or fold into M6.

## Decisions (M5a)

| Decision | Choice |
|---|---|
| `Mutation` shape | Mark `@Serializable`, DTO == runtime type. Same as M3 races/classes pattern. |
| `Scenario.promptTemplate` | Change from `(Character, MapLocation, String, String) -> String` lambda to a JSON-side `String` with `{race}`, `{class}`, `{loc.icon}`, `{loc.name}`, `{nearby}`, `{lore}` placeholders. Add a `Scenario.renderPrompt(ch, loc, nearby, lore): String` extension. One-call-site update. |
| `Scenario.modify` | Keep as `((Character) -> Unit)?` field on the runtime `Scenario`. Backed by a code-side `modifyRegistry: Map<String, (Character) -> Unit>` keyed by scenario id (mirrors the Feats apply-registry pattern). JSON tracks which scenario ids have modifiers via a `hasModifier: Boolean` field. |
| Templates utility | New tiny `Templates.kt` doing `String.replace("{key}", value)` for each entry in a `Map<String, String>`. M5b reuses this. |
| Schema version | Bump 3 → 4. |
| Asset paths | `assets/content/world/mutations.json` and `assets/content/world/scenarios.json` (the `world/` folder already houses `location-templates.json` from M2). |

## What moves

### `Mutations.kt`
- `Mutations.list` (16 entries) → `world/mutations.json`.
- `data class Mutation` becomes `@Serializable`. Same shape, no changes to fields.
- `Mutations.find` and `Mutations.pickForWorld` unchanged.
- `Mutations.list` becomes a one-line facade.

### `Scenarios.kt`
- `Scenarios.list` (24 entries) → `world/scenarios.json` with `{id, name, sceneHint, promptTemplate, hasModifier}`.
- `data class Scenario` keeps its shape but `promptTemplate: String` (was a lambda). Backwards-compat helper `Scenario.renderPrompt(ch, loc, nearby, lore)` does the interpolation.
- New private `modifyRegistry: Map<String, (Character) -> Unit>` in `Scenarios.kt` holds the 5 modifier lambdas (prison_break, shipwreck, ambush, heist_gone_wrong, debt_collector).
- `Scenarios.list` becomes a memoized facade that joins `ContentRepository.scenarioMetas` with `modifyRegistry`. Crashes loudly if a JSON entry has `hasModifier: true` but no registry entry, or vice versa.

### Call sites
- `GameViewModel.kt:720`: `scenario.promptTemplate(char, startLoc, nearby, loreBlurb)` → `scenario.renderPrompt(char, startLoc, nearby, loreBlurb)`. Single line.

## New / modified files

```
docs/superpowers/specs/2026-05-01-phase6-json-content-m5a-design.md   (this file)

app/src/main/assets/content/
├── schema-version.json                  (bump to {"version": 4})
└── world/
    ├── mutations.json                   (NEW — 16 entries)
    └── scenarios.json                   (NEW — 24 entries)

app/src/main/kotlin/com/realmsoffate/game/
├── data/content/
│   ├── ContentSchema.kt                 (bump version, add Mutation/Scenario DTOs)
│   ├── ContentLoader.kt                 (parse 2 new files, extend Bundle)
│   └── ContentRepository.kt             (expose mutations, scenarioMetas)
├── game/
│   ├── Mutations.kt                     (Mutation @Serializable; Mutations.list facade)
│   └── Scenarios.kt                     (Scenario.promptTemplate now String; modify registry; facade)
└── util/
    └── Templates.kt                     (NEW — tiny placeholder interpolator)

app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt   (one-line update)

app/src/test/kotlin/com/realmsoffate/game/data/content/
├── ContentRepositoryTest.kt             (add non-empty asserts for mutations + scenarioMetas)
└── M5aContentParityTest.kt              (NEW — pin both lists + render-prompt sample)
```

## Schema additions

```kotlin
internal const val CONTENT_SCHEMA_VERSION = 4

@Serializable
internal data class MutationsDto(val list: List<Mutation>)

@Serializable
data class ScenarioMeta(
    val id: String,
    val name: String,
    val sceneHint: String,
    val promptTemplate: String,
    val hasModifier: Boolean = false
)

@Serializable
internal data class ScenariosDto(val list: List<ScenarioMeta>)
```

## Tests

| Test | Purpose |
|---|---|
| `ContentRepositoryTest.parsesAllShippedAssets` | Add non-empty asserts for `mutations`, `scenarioMetas`. |
| `M5aContentParityTest.mutations match historical` | `ContentRepository.mutations` equals the 16-entry literal. |
| `M5aContentParityTest.scenario metas match historical` | `ContentRepository.scenarioMetas` equals the 24-entry literal (id, name, sceneHint, promptTemplate string, hasModifier). |
| `M5aContentParityTest.scenario list resolves all modifiers` | Every JSON `hasModifier=true` has a registry entry; no orphaned registry entries; `Scenarios.list` materializes without throwing. |
| `M5aContentParityTest.renderPrompt sample` | Render one scenario (e.g. `prison_break`) with a fixed test input; assert exact string match against the historical lambda output. |

## Acceptance

- [ ] `ContentRepository.mutations`, `.scenarioMetas` exposed and non-empty.
- [ ] `assets/content/world/{mutations,scenarios}.json` ship with full historical content.
- [ ] `schema-version.json` is `{"version": 4}`.
- [ ] `Mutations.list` and `Scenarios.list` are facades.
- [ ] `Scenario.promptTemplate` is now a JSON-loaded String; `Scenario.renderPrompt` produces the same output the old lambda did.
- [ ] All M5a parity tests pass.
- [ ] `gradle test` and `gradle assembleDebug` pass.
- [ ] App boots; `/macro/new-game` works (does not exercise scenarios because debug bypasses `applyClassStart` flow, but the asset load must not crash).
- [ ] APK delta ≤ 50 KB.

## Out of scope (M5b+)

- WorldEvents lambdas (M5b — title/prompt templates + code-side context-picker registry).
- LoreGen historical-event lambda lists (M5b).
- `WORLD_NAME_PATTERNS` (deferred — different shape).
- Hot-reload (M6).
- Facade deletion + epic seeded-output parity (M7).
