# Phase 6 — JSON Content Extraction (M3)

**Date:** 2026-05-01
**Issue:** [#24 — Extract hardcoded static data into JSON assets](https://github.com/tahuffman1s/RealmsAndroid/issues/24)
**Scope:** Milestone M3 — Races, Classes, Feats
**Builds on:** M1+M2 (`2026-04-30-phase6-json-content-m1-m2-design.md`)

## Goal

Move the three character-creation tables — `Races.list`, `Classes.list`, `Feats.list` — out of Kotlin source and into JSON assets, using the `ContentRepository` infrastructure that M1 stood up. These are tighter schemas than M2 (structured records, not pure-string pools) but still authored content, not behavior.

The one wrinkle is `Feats`: each feat carries an `apply: (Character) -> Unit` lambda. Lambdas are behavior — they cannot serialize. M3 splits `Feat` into JSON-backed metadata (name, description, icon) and a code-side registry of apply lambdas keyed by feat name. The runtime `Feat` value is assembled by joining the two.

## Decisions

| Decision | Choice |
|---|---|
| `RaceDef` / `ClassDef` shape | Mark them `@Serializable` directly — DTO **is** the runtime type. No mapping layer. |
| `ClassDef.recommended` field type | Change `IntArray` → `List<Int>`. `IntArray` does not round-trip through `kotlinx.serialization` cleanly, and `List<Int>` is a superficial change at the 3 call sites. |
| `Feat` apply lambdas | Stay in Kotlin as a `featRegistry: Map<String, (Character) -> Unit>` keyed by name. JSON ships metadata only. `Feats.list` is built by joining `ContentRepository.featMetas` with the registry; mismatched names crash at first access (catches drift). |
| Item reuse in `ClassDef.startingItems` | The existing `data class Item` is already `@Serializable`. Use it as-is. |
| Schema version | Bump `CONTENT_SCHEMA_VERSION` from `1` to `2`. Old `schema-version.json` updated to `{"version": 2}`. |
| Asset layout | New folder `assets/content/characters/` holding `races.json`, `classes.json`, `feats.json`. |

## What moves

### `Races.kt`
- `Races.list` (11 `RaceDef` entries) → `characters/races.json`.
- `RaceDef` becomes `@Serializable`. The `applyTo(a: Abilities)` member function stays — it's a small mutator over the same data.
- Top-level helper `RaceDef.defaultBonusIndices()` is unchanged (operates on the data class).

### `Classes.kt`
- `Classes.list` (12 `ClassDef` entries) → `characters/classes.json`.
- `ClassDef` becomes `@Serializable`. Field change: `val recommended: IntArray` → `val recommended: List<Int>` (default `listOf(13, 13, 13, 13, 13, 13)`).
- Companion helpers `Classes.find`, `Classes.rollHp`, top-level `applyClassStart` are unchanged.

### `Feats.kt`
- `Feats.list` (12 `Feat` entries) → metadata in `characters/feats.json`; apply lambdas in a private `featRegistry: Map<String, (Character) -> Unit>` inside `Feats.kt`.
- A new `@Serializable data class FeatMeta(val name: String, val description: String, val icon: String)` lives in `ContentSchema.kt`.
- `Feats.list` is computed once on first access by walking `ContentRepository.featMetas` and pairing each with `featRegistry[meta.name]`. A missing key throws `IllegalStateException("Feat registry missing apply for '$name'")` so a content-only edit cannot silently drop behavior.

## Call sites that change

The `ClassDef.recommended` type change touches three places:

| File | Before | After |
|---|---|---|
| `Classes.kt:27` | `fun ClassDef.recommendedArray(): IntArray = recommended` | `fun ClassDef.recommendedArray(): IntArray = recommended.toIntArray()` |
| `RandomCharacter.kt:84` | `baseStats = cls.recommended.copyOf()` | `baseStats = cls.recommended.toIntArray()` |
| `CharacterCreationScreen.kt:225` | `baseStats.value = (def?.recommended ?: intArrayOf(...)).copyOf()` | `baseStats.value = (def?.recommended ?: listOf(13,13,13,13,13,13)).toIntArray()` |
| `RandomCharacterTest.kt:33` | `r.baseStats.contentEquals(def.recommended)` | `r.baseStats.toList() == def.recommended` |

No other public surface changes. `RaceDef` and `ClassDef` field names and types are otherwise unchanged.

## New / modified files

```
docs/superpowers/specs/2026-05-01-phase6-json-content-m3-design.md   (this file)

app/src/main/assets/content/
├── schema-version.json                  (bump to {"version": 2})
└── characters/
    ├── races.json                       (NEW — 11 entries)
    ├── classes.json                     (NEW — 12 entries)
    └── feats.json                       (NEW — 12 entries, metadata only)

app/src/main/kotlin/com/realmsoffate/game/
├── data/content/
│   ├── ContentSchema.kt                 (bump version, add Race/Class/Feat DTOs)
│   ├── ContentLoader.kt                 (parse 3 new files, extend Bundle)
│   └── ContentRepository.kt             (expose races, classes, featMetas)
└── game/
    ├── Races.kt                         (RaceDef @Serializable; Races.list facade)
    ├── Classes.kt                       (ClassDef @Serializable; recommended → List<Int>; Classes.list facade)
    └── Feats.kt                         (split metadata/registry; Feats.list facade)

app/src/main/kotlin/com/realmsoffate/game/game/RandomCharacter.kt   (one-line type fix)
app/src/main/kotlin/com/realmsoffate/game/ui/setup/CharacterCreationScreen.kt   (one-line type fix)

app/src/test/kotlin/com/realmsoffate/game/data/content/
├── ContentRepositoryTest.kt             (add non-empty asserts for races/classes/featMetas)
└── M3ContentParityTest.kt               (NEW — pin races/classes/feats to historical literals)

app/src/test/kotlin/com/realmsoffate/game/game/RandomCharacterTest.kt   (one-line assertion fix)
```

## Schema additions (`ContentSchema.kt`)

```kotlin
internal const val CONTENT_SCHEMA_VERSION = 2

@Serializable
internal data class RacesDto(val list: List<RaceDef>)

@Serializable
internal data class ClassesDto(val list: List<ClassDef>)

@Serializable
data class FeatMeta(val name: String, val description: String, val icon: String)

@Serializable
internal data class FeatsDto(val list: List<FeatMeta>)
```

`RaceDef` and `ClassDef` themselves get `@Serializable` annotations in their existing files (`Races.kt`, `Classes.kt`). `Item` already is.

## `ContentRepository` additions

```kotlin
val races: List<RaceDef> get() = bundle.races.list
val classes: List<ClassDef> get() = bundle.classes.list
val featMetas: List<FeatMeta> get() = bundle.feats.list
```

## Failure contract

Unchanged from M1: `MissingFile`, `MalformedJson`, `SchemaVersionMismatch`. Crash at startup, no fallback. The Feat name/registry mismatch is a *separate* runtime failure (`IllegalStateException`) raised on first `Feats.list` access — caught by the parity test.

## Tests

| Test | Purpose |
|---|---|
| `ContentRepositoryTest.parsesAllShippedAssets` | Extend with non-empty asserts for `races`, `classes`, `featMetas`. |
| `M3ContentParityTest.races match historical` | `ContentRepository.races` equals the 11-entry literal from old `Races.list`. |
| `M3ContentParityTest.classes match historical` | `ContentRepository.classes` equals the 12-entry literal from old `Classes.list`. |
| `M3ContentParityTest.feat metas match historical` | `ContentRepository.featMetas` is exactly the historical (name, description, icon) triples in order. |
| `M3ContentParityTest.feats list resolves all apply lambdas` | `Feats.list` materializes without throwing — registry has every name JSON declares. |

`RandomCharacterTest.kt` gets the `.contentEquals` → `.toList() ==` adjustment so it still pins recommended array equality.

## Acceptance

- [ ] `ContentRepository.races`, `.classes`, `.featMetas` exposed and non-empty.
- [ ] `assets/content/characters/{races,classes,feats}.json` ship with full historical content.
- [ ] `schema-version.json` is `{"version": 2}`; mismatch crashes with diagnostic.
- [ ] `Races.list`, `Classes.list`, `Feats.list` are facades — body collapses to a single `get()` (or memoized join, for Feats).
- [ ] `M3ContentParityTest` passes: every entry round-trips byte-for-byte with the pre-migration Kotlin literal.
- [ ] `gradle test` and `gradle assembleDebug` pass.
- [ ] App boots on emulator; new-game flow produces the same character-creation options (P0+P1 verification per debug-bridge-test-procedures).
- [ ] APK size delta ≤ 50 KB (epic-level budget).

## Out of scope (M4+)

- Spells (M4).
- Scenarios, Mutations, World Events (M5).
- Hot-reload (M6).
- Deleting facades + epic-level seeded-output parity (M7).
