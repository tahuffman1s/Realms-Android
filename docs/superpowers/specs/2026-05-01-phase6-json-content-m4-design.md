# Phase 6 — JSON Content Extraction (M4)

**Date:** 2026-05-01
**Issue:** [#24 — Extract hardcoded static data into JSON assets](https://github.com/tahuffman1s/RealmsAndroid/issues/24)
**Scope:** Milestone M4 — Spells / class abilities (34-entry `Spell` list)
**Builds on:** M1+M2 (`2026-04-30-phase6-json-content-m1-m2-design.md`), M3 (`2026-05-01-phase6-json-content-m3-design.md`)

## Goal

Move `Spells.list` (the 34-entry spell + class-ability database in `Spells.kt`) out of Kotlin source and into a JSON asset, using the same `@Serializable`-as-DTO pattern M3 established for `Races` / `Classes`.

`SpellSlots` (the per-class slot-progression tables) **stays in Kotlin**. It is mechanical progression logic keyed by class name, not authored content — writers tweak spell descriptions, not D&D 5e slot math.

`Spells.find`, `Spells.knownFor`, `Spells.grantStartingSpells` are unchanged — they're behavior over the data.

## Decisions

| Decision | Choice |
|---|---|
| `Spell` shape | Mark `@Serializable` directly. DTO **is** the runtime type. No mapping layer. |
| Default field handling | `damage = "-"`, `unlockLevel = 1`, `restCharged = false` are Kotlin-side defaults; JSON entries omit these fields when they take the default value, keeping the file lean. |
| `SpellSlots` | Stays as object literal in `Spells.kt`. Out of scope for M4. |
| Schema version | Bump `CONTENT_SCHEMA_VERSION` 2 → 3. `schema-version.json` becomes `{"version": 3}`. |
| Asset location | `assets/content/spells/spells.json` (single file under a new `spells/` folder for headroom — M5 may later add scenario/mutation companions, but M4 introduces no new neighbors here). |

## What moves

### `Spells.kt`
- `Spells.list: List<Spell>` (34 entries) → `assets/content/spells/spells.json`.
- `data class Spell(...)` becomes `@Serializable`.
- `object Spells` keeps `find`, `knownFor`, `grantStartingSpells`. `list` collapses to `get() = ContentRepository.spells`.
- `object SpellSlots` and the `FULL_CASTER` / `HALF_CASTER` tables stay verbatim.

## New / modified files

```
docs/superpowers/specs/2026-05-01-phase6-json-content-m4-design.md   (this file)

app/src/main/assets/content/
├── schema-version.json                  (bump to {"version": 3})
└── spells/
    └── spells.json                      (NEW — 34 entries)

app/src/main/kotlin/com/realmsoffate/game/
├── data/content/
│   ├── ContentSchema.kt                 (bump version, add Spell DTOs)
│   ├── ContentLoader.kt                 (parse spells.json, extend Bundle)
│   └── ContentRepository.kt             (expose spells)
└── game/
    └── Spells.kt                        (Spell @Serializable; Spells.list facade)

app/src/test/kotlin/com/realmsoffate/game/data/content/
├── ContentRepositoryTest.kt             (assert spells non-empty)
└── M4ContentParityTest.kt               (NEW — pin spells to historical literal)
```

## Schema additions (`ContentSchema.kt`)

```kotlin
internal const val CONTENT_SCHEMA_VERSION = 3

@Serializable
internal data class SpellsDto(val list: List<Spell>)
```

## `ContentRepository` additions

```kotlin
val spells: List<Spell> get() = bundle.spells.list
```

## Failure contract

Unchanged from M1: `MissingFile`, `MalformedJson`, `SchemaVersionMismatch`. Crash at startup, no fallback.

## Tests

| Test | Purpose |
|---|---|
| `ContentRepositoryTest.parsesAllShippedAssets` | Add non-empty assert for `spells`. |
| `M4ContentParityTest.spells match historical` | `ContentRepository.spells` equals the 34-entry literal from old `Spells.list`. |

## Acceptance

- [ ] `ContentRepository.spells` exposed and non-empty.
- [ ] `assets/content/spells/spells.json` ships with all 34 entries.
- [ ] `schema-version.json` is `{"version": 3}`; mismatch crashes with diagnostic.
- [ ] `Spells.list` is a one-line facade.
- [ ] `M4ContentParityTest` pins every entry byte-for-byte against the pre-migration Kotlin literal.
- [ ] `gradle test` and `gradle assembleDebug` pass.
- [ ] App boots; `applyClassStart` for a caster grants the same starting spells as before (P0+P1 verification, plus a `/macro/new-game` with a wizard or cleric to confirm the spell-slot path still works).
- [ ] APK delta ≤ 50 KB (epic budget).

## Out of scope (M5+)

- Scenarios, Mutations, World Events (M5) — includes the lambda-based pools deferred from M2.
- Hot-reload (M6).
- Deleting facades + epic-level seeded-output parity (M7).
