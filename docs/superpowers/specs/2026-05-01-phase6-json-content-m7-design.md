# Phase 6 — JSON Content Extraction (M7)

**Date:** 2026-05-01
**Issue:** [#24 — Extract hardcoded static data into JSON assets](https://github.com/tahuffman1s/RealmsAndroid/issues/24)
**Scope:** Milestone M7 — Final cleanup + epic-level seeded-output parity
**Builds on:** M1+M2, M3, M4, M5a, M5b, M6

## Goal

Close out the epic. Delete the value-less forwarder facades that earlier milestones left as bridges, ship the seeded-output parity test promised since M2, and update docs to reflect that JSON content extraction is complete.

## What gets deleted

### `LoreGen.kt` — 17 private forwarder vals

```kotlin
private val FACTION_TYPES: List<String> get() = ContentRepository.factionTypes
private val NPC_FIRSTS: List<String>   get() = ContentRepository.npcFirsts
// ... 15 more
```

These are pure passthroughs. Inline call sites in `LoreGen.generate()` use `ContentRepository.X` directly.

### `LoreGen.kt` — 15 public list-exposure methods

```kotlin
fun npcFirstNames(): List<String> = NPC_FIRSTS
fun factionAdjs(): List<String>   = FACTION_ADJS
// ... 13 more
```

Their only caller is `Prompts.kt` (the system-prompt builder). After M7, Prompts.kt calls `ContentRepository.X` directly. No semantic change — the methods existed only to forward.

### `data/PlayerNamePool.kt` — entire file

```kotlin
internal object PlayerNamePool {
    val NAMES: List<String> get() = ContentRepository.playerNames
}
```

Two callers (`RandomCharacter.kt`, `RandomCharacterTest.kt`) switch to `ContentRepository.playerNames` directly.

### `Races.list`, `Classes.list`, `Spells.list`, `Mutations.list`

Pure-forwarder accessors of the form `val list: List<X> get() = ContentRepository.X`. Deleted. External callers — `RandomCharacter.kt`, `CharacterCreationScreen.kt`, `ClassStartingClothesTest.kt` — switch to `ContentRepository.X` directly. The objects' helper methods (`find`, `rollHp`, `knownFor`, `pickForWorld`, `grantStartingSpells`) keep working by reading `ContentRepository.X` inside their bodies.

## What does NOT get deleted

| Symbol | Reason |
|---|---|
| `Feats.list` | Does **registry-join work** — combines `ContentRepository.featMetas` with the apply-lambda registry to produce `List<Feat>`. Not a pure forwarder. |
| `Scenarios.list` | Same — joins `ContentRepository.scenarioMetas` with the modify-lambda registry to produce `List<Scenario>`. |
| `Races.find`, `Classes.find/rollHp`, `Spells.find/knownFor/grantStartingSpells`, `Mutations.find/pickForWorld`, `Scenarios.random`, `Scenario.renderPrompt` | Useful helpers — each reads `ContentRepository.X` once, then does work specific to its caller. Move the read inside the body, drop the `list` alias. |
| `data class RaceDef`, `ClassDef`, `Spell`, `Mutation`, `Feat`, `Scenario` | Runtime types. Their `@Serializable` declarations stay where they live. |
| `applyClassStart` (top-level fn) | Not a facade. |
| `ContentRepository.initializeFrom` | Test hook. |

## What gets added

### `LoreSeededParityTest`

A Robolectric test that:

1. Builds a deterministic `WorldMap` via `WorldGen.generate(seed = 42)`.
2. Calls `LoreGen.generate(wm, seed = 42)`.
3. Asserts a fingerprint of the resulting `WorldLore`:
   - `worldName`, `era`
   - `factions.size` and `factions[0].name`, `factions[0].type`
   - `npcs.size`
   - `mutations.size` and `mutationIds`
   - `history.size` and a sample of `history[0].text` (rendered, not template)
   - `rumors.size` and `rumors[0]`

The fingerprint is a tight set of values rather than a multi-KB JSON snapshot — easy to read, narrow blast radius if intentional content edits happen later. The point is **regression detection**: any future change to JSON content or LoreGen logic that affects seeded output trips the test, forcing the contributor to look.

This test was promised by the M2 design ("Epic-level seeded-output parity tests are deferred to M7"). M7 finally delivers it.

### `CLAUDE.md` update

Add a one-line note under `## Layout`: `data/content/` is the canonical source of all authored static content; runtime accesses via `ContentRepository`. Helps future agents avoid re-introducing inline literals.

## Implementation notes

- **`Races.find` body change**: was `list.firstOrNull { it.name.equals(name, true) }`. Becomes `ContentRepository.races.firstOrNull { it.name.equals(name, true) }`. Same pattern for the other helpers.
- **`Spells.grantStartingSpells`** reads the list twice — `eligible.filter { ... }` against `ContentRepository.spells` once is enough.
- **`Mutations.pickForWorld`** body becomes `ContentRepository.mutations.shuffled(rng).take(count)`.
- **No public API changes** for `Races`/`Classes`/`Spells`/`Mutations` — only the `.list` accessor disappears, and only three production files plus one test reference it externally.
- Helper test `M3ContentParityTest.feats list resolves all apply lambdas` and `M5aContentParityTest.scenario list resolves all modifiers` still work — they exercise `Feats.list` / `Scenarios.list`, both of which we are keeping.

## Tests

| Test | Purpose |
|---|---|
| `LoreSeededParityTest.world generation produces stable fingerprint` | New — pins `LoreGen.generate(seed=42)` output. |
| `gradle test` (existing) | All previous parity / repo / hot-reload tests still pass. |

## Acceptance

- [ ] `LoreGen.kt` no longer contains any `private val ... get() = ContentRepository.X` declarations.
- [ ] `LoreGen.npcFirstNames()` and the other 14 list-exposure methods are gone; `Prompts.kt` reads `ContentRepository.X` directly.
- [ ] `data/PlayerNamePool.kt` is deleted; both callers reference `ContentRepository.playerNames`.
- [ ] `Races.list`, `Classes.list`, `Spells.list`, `Mutations.list` are gone; helper methods still work; external callers updated.
- [ ] `Feats.list` and `Scenarios.list` remain (registry-join helpers, not facades).
- [ ] `LoreSeededParityTest` passes with the captured fingerprint.
- [ ] `gradle test` and `gradle assembleDebug` pass.
- [ ] App boots; P0+P1 verification clean.
- [ ] CLAUDE.md mentions `data/content/` as the authored-content source of truth.

## Issue #24

After this PR merges, the epic is **complete** — every checkbox in #24's milestone list is checked, every authored static-content collection is JSON-backed, hot-reload works, and a regression-catching parity test is in place. PR description should call out that #24 can close.

## Out of scope (intentionally)

- `WORLD_NAME_PATTERNS` in `LoreGen.kt`. The 8 lambda generators with inline word lists are a different shape from everything else in this epic — keeping them in code is the right tradeoff. Worth a follow-up issue if writers want them editable later, but **not** holding up #24.
- Issue #28 (auto-update system). Was always a separate phase.
