# Equipment Effects Design

**Date:** 2026-04-24
**Branch:** envelope-repair-and-shop-removal (current)
**Goal:** Make equipped items matter — mechanically (code owns hard numbers: AC, max HP, ability scores) and narratively (LLM sees a structured summary of every equipped effect and honors it in checks, combat narration, and outcomes).

---

## Current state

- `Item` has `type`, `rarity`, `damage: String?`, `ac: Int?`, `equipped: Boolean`. No other effect data.
- Armor AC is applied exactly once in `applyClassStart` (`game/Classes.kt:199-201`). Swapping armor mid-game does not recompute AC.
- Character abilities are static `Abilities` scores with no layered modifiers.
- The LLM prompt includes `"Equipped: ..."` as a plain comma-joined name list at `GameViewModel.kt:427` and `1180`. No effect data reaches the model.
- `ItemSpec` (LLM-emitted loot) has only `name`, `desc`, `type`, `rarity`.

## Requirements

Support all of the following, with tests for each:

1. Weapon damage drives combat outcomes (LLM-informed via prompt)
2. AC / shield stack live
3. Ability bonuses (+X STR, etc.)
4. Skill / check bonuses (+X Stealth, etc.)
5. Damage resistances / immunities
6. Passive triggers / on-hit effects (flaming weapon, vorpal, cursed)
7. Max HP modifiers (Amulet of Health)

## Architecture

### Data model — typed effect list

Add to `data/Models.kt`:

```kotlin
@Serializable
sealed interface ItemEffect {
    @Serializable @SerialName("ability") data class AbilityBonus(val stat: String, val amount: Int) : ItemEffect
    @Serializable @SerialName("skill")   data class SkillBonus(val skill: String, val amount: Int) : ItemEffect
    @Serializable @SerialName("resist")  data class Resistance(val damageType: String) : ItemEffect
    @Serializable @SerialName("immune")  data class Immunity(val damageType: String) : ItemEffect
    @Serializable @SerialName("onhit")   data class OnHit(val dice: String, val damageType: String) : ItemEffect
    @Serializable @SerialName("maxhp")   data class MaxHpBonus(val amount: Int) : ItemEffect
    @Serializable @SerialName("trigger") data class PassiveTrigger(val text: String) : ItemEffect
}
```

`Item` and `ItemSpec` both gain `val effects: List<ItemEffect> = emptyList()`. Default-empty means existing saves and existing LLM outputs load without migration.

`stat` values: `STR`/`DEX`/`CON`/`INT`/`WIS`/`CHA`.
`damageType` values: free-form lowercase strings (fire, cold, lightning, poison, physical, psychic, …). Code doesn't validate — the LLM authors them and also consumes them.
`dice` values: D&D-style dice strings (`"1d4"`, `"2d6"`), optionally with a leading `+`.
`PassiveTrigger.text` is free-form narrative instruction consumed only by the LLM.

### Core — `EquipmentEffects` pure functions

New file `app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt`. No Android deps. All pure, testable on JVM.

```kotlin
object EquipmentEffects {
    fun activeEffects(ch: Character): List<ItemEffect>
    fun effectiveAbilities(ch: Character): Abilities
    fun effectiveAc(ch: Character): Int
    fun effectiveMaxHp(ch: Character): Int
    fun skillBonuses(ch: Character): Map<String, Int>
    fun resistances(ch: Character): Set<String>
    fun immunities(ch: Character): Set<String>
    fun onHitRiders(ch: Character): List<ItemEffect.OnHit>
    fun passiveTriggers(ch: Character): List<String>
    fun promptSummary(ch: Character): String
}
```

Rules:
- `activeEffects` returns effects flat-mapped only from items with `equipped = true`.
- `effectiveAbilities` takes `ch.abilities` and adds all `AbilityBonus` amounts per stat. Returns a new `Abilities` copy — original is untouched.
- `effectiveAc` replaces the Classes.kt logic: if an equipped item has non-null `ac`, start there; otherwise 10 + DEX mod. Heavy armor (Chain Mail, Plate) ignores DEX. Shield (equipped, `type == "shield"`) adds +2. DEX for the calculation uses `effectiveAbilities(ch).dexMod` so a Cloak of Dex propagates.
- `effectiveMaxHp` = current `ch.maxHp` + sum of `MaxHpBonus.amount` from equipped items **that are not already written through** (see equip handling below). In practice the authoritative `ch.maxHp` is updated on equip/unequip, so `effectiveMaxHp` and `ch.maxHp` agree outside that transient window and this helper is mainly a documentation seam for tests and UI code.
- `skillBonuses` sums per skill name. Case-insensitive keys, canonical capitalization in output.
- `resistances`/`immunities` are sets (duplicates collapse).
- `onHitRiders` returns every `OnHit` from equipped items in declaration order.
- `passiveTriggers` returns `text` values from every equipped `PassiveTrigger`.
- `promptSummary` returns a human-readable block (see below). Empty string when nothing equipped has effects.

### Equip/unequip recomputation

In `GameViewModel.equipToggle` (`game/GameViewModel.kt:1645`), after the existing equip/displace logic, we **write through** the AC and maxHp deltas so `ch.ac` and `ch.maxHp` remain authoritative for UI:

```kotlin
// Delta approach: compute what changed between old and new equipped sets.
val oldEquipped = ch.inventory.filter { it.equipped }
val newEquipped = inv.filter { it.equipped }
val deltaMaxHp = newEquipped.sumMaxHpBonus() - oldEquipped.sumMaxHpBonus()
val newMaxHp = (ch.maxHp + deltaMaxHp).coerceAtLeast(1)
val newHp = minOf(ch.hp, newMaxHp) // clamp down when max drops; do not auto-heal when max rises
val newCh0 = ch.copy(inventory = inv, maxHp = newMaxHp, hp = newHp)
val newAc = EquipmentEffects.effectiveAc(newCh0)
_ui.value = s.copy(character = newCh0.copy(ac = newAc))
```

Rationale: `ch.maxHp` already carries level-up accumulations, so additive delta is the safe mutation. This preserves existing behavior for code that reads `ch.maxHp` directly (HP bar UI, save/load). For ability bonuses we do **not** write through — `ch.abilities` stays as the base score, and every site that does stat math reads `effectiveAbilities(ch)` instead. This avoids compounding interactions with level-up ASI / items that touch the same stat.

### LLM integration

**Prompt summary format** (emitted by `promptSummary`):

```
Equipped gear:
- Flametongue Longsword (weapon, 1d8 slashing) — on hit: +1d6 fire
- Plate Armor (AC 18)
- Ring of Strength — +2 STR (applied)
- Cloak of Stealth — +2 Stealth checks
- Cursed Ring — passive: -1 to all rolls
Resistances: fire, cold
Immunities: poison
```

Lines per item show name, type, damage (weapons), AC (armor), then ability bonuses (noting "applied" because the LLM already sees the boosted score), skill bonuses, on-hit riders, passive triggers. Final two lines roll up resistances/immunities.

**Prompt system-text additions** (in `data/prompts/`): tell the LLM:
- Ability-bonus effects are already folded into the `Abilities` it sees in the prompt — do not re-apply them to checks.
- Skill-bonus effects should be added to skill-check rolls.
- Resistances halve incoming damage of that type; immunities negate it entirely.
- On-hit riders add their damage to successful weapon attacks.
- Passive triggers are narrative law — respect them as written.

**`ItemSpec` effects** — LLM loot emission gains the same `effects` list. Turn-effects schema doc gets an example showing a fire-resist ring.

**Two prompt sites to update:** `GameViewModel.kt:427` and `1180`. Both currently call `ch.inventory.filter { it.equipped }.joinToString(...) { it.name }`. Both become `EquipmentEffects.promptSummary(ch)`.

**Effective abilities in prompts** — grep every site that serializes `ch.abilities` into a prompt; replace with `EquipmentEffects.effectiveAbilities(ch)` so the LLM's check math stays consistent.

### UI

- Inventory `SelectedItemCard` adds a small effects list below damage/AC (e.g., `"+2 STR"`, `"+2 Stealth"`, `"Fire Resist"`, `"On hit: +1d4 fire"`, passive-trigger text).
- `BackpackCell` shows a compact indicator (a small chip or suffix) when an item carries effects, but doesn't enumerate them (not enough room).
- Character sheet stat display: when a stat has an active ability bonus, show `STR 16 (+2) = 18`.

### Testing

JVM tests under `app/src/test/kotlin/com/realmsoffate/game/game/`:

1. `EquipmentEffectsTest.kt` — each public function, covering both equipped and unequipped items, zero/one/many effects, heavy-armor DEX exclusion, multiple resistances collapse, etc.
2. `EquipToggleEffectsTest.kt` — equipping a Ring of Strength (+2 STR) raises `effectiveAbilities().str`; unequipping restores base. AC changes live when armor swaps. Equipping Amulet of Health raises `effectiveMaxHp`; unequipping drops it; current hp clamps down if it was above the new max; current hp does not auto-heal when max rises.
3. `ItemEffectSerializationTest.kt` — polymorphic round-trip for each sealed subtype; legacy `Item` JSON with no `effects` field deserializes to empty list.
4. `PromptSummaryTest.kt` — `promptSummary(ch)` emits the expected text for a representative equipped set; empty state returns empty string.
5. `ItemSpecEffectsParseTest.kt` — LLM-emitted `ItemSpec` JSON containing an `effects` array parses correctly; items-gained flow transfers effects into the player inventory.

One regression test is added to the existing `AutoTagUnknownNpcsTest.kt` only if an effect-related change touches that file; otherwise the new tests live in their own files.

### Migration

- `effects: List<ItemEffect> = emptyList()` on both `Item` and `ItemSpec` — no save migration needed.
- `Classes.kt` starting-inventory authoring is unchanged except to express existing AC/damage in the new schema as-is. We are wiring the system, not rebalancing classes.
- `Classes.kt:199-201` is deleted in favor of `EquipmentEffects.effectiveAc(ch)` called at the end of `applyClassStart`.

## Out of scope

- Rebalancing class starting gear with new effects.
- Authoring a curated magic-item table — LLM-emitted loot will populate effects going forward.
- Cursed items that block unequip (nothing currently prevents unequip; the `PassiveTrigger` text is informational only).
- Attunement limits.
