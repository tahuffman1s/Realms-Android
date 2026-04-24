# Equipment Effects Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make equipped items alter game state and LLM reasoning — code owns AC/maxHP/ability-score math (live-recomputed on equip/unequip), and the LLM prompt carries a structured summary of every equipped effect (skills, resistances, on-hit, passive triggers) so it respects them in checks and combat narration.

**Architecture:** Introduce a polymorphic `ItemEffect` sealed interface attached to `Item` and `ItemSpec`. A new pure-Kotlin `EquipmentEffects` helper computes effective stats and produces a human-readable prompt block. `equipToggle` writes through AC/maxHP deltas and clamps HP. Ability math everywhere reads `effectiveAbilities(ch)` instead of `ch.abilities`. LLM loot schema gains an `effects` array. Each effect type has a dedicated test.

**Tech Stack:** Kotlin, kotlinx.serialization (polymorphic sealed-class serialization), JUnit 4 (JVM tests under `app/src/test/kotlin/`), Jetpack Compose for the UI surfaces.

**Spec:** `docs/superpowers/specs/2026-04-24-equipment-effects-design.md`

---

## File Structure

**New files:**
- `app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt` — pure helper functions for effective stats, prompt summary, resistance/immunity/on-hit aggregation.
- `app/src/test/kotlin/com/realmsoffate/game/data/ItemEffectSerializationTest.kt` — polymorphic JSON round-trip per sealed subtype.
- `app/src/test/kotlin/com/realmsoffate/game/data/ItemSpecEffectsParseTest.kt` — LLM loot JSON → Item inventory transfer with effects preserved.
- `app/src/test/kotlin/com/realmsoffate/game/game/EquipmentEffectsTest.kt` — each public `EquipmentEffects` function, per effect type.
- `app/src/test/kotlin/com/realmsoffate/game/game/EquipToggleEffectsTest.kt` — VM-level equip/unequip recomputation (JVM, no Android deps — directly exercise the relevant pure logic).
- `app/src/test/kotlin/com/realmsoffate/game/game/PromptSummaryTest.kt` — snapshot the prompt block string.

**Modified files:**
- `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt` — add `ItemEffect` sealed interface; add `effects: List<ItemEffect> = emptyList()` to `Item` and `ItemSpec`.
- `app/src/main/kotlin/com/realmsoffate/game/game/Classes.kt` — replace hand-rolled AC calc at lines 199-201 with `EquipmentEffects.effectiveAc(ch)`.
- `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` — update:
  - L423: print effective abilities in `/describe` output.
  - L427, L1180: emit `EquipmentEffects.promptSummary(ch)` for equipped-gear section of LLM prompt.
  - L833, L857, L913: read `EquipmentEffects.effectiveAbilities(ch).modByName(ability)` for skill/ability check math.
  - L1645-1675: `equipToggle` recomputes AC + maxHp delta + HP clamp on every toggle, and carries through items_gained effects.
  - Wherever `items_gained: List<ItemSpec>` is materialized into inventory (find with grep), copy `spec.effects` onto the new `Item`.
- `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — extend the `items_gained` JSON schema at L221 to include `effects`; add a short narrator instruction explaining how to use equipped-gear effects in checks and combat.
- `app/src/main/kotlin/com/realmsoffate/game/ui/panels/InventoryPage.kt` — render effect chips on `SelectedItemCard` and mark backpack cells with an effect indicator.

---

## Task 1: Add `ItemEffect` sealed interface + `effects` field

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/data/ItemEffectSerializationTest.kt`

- [ ] **Step 1: Write failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/ItemEffectSerializationTest.kt`:

```kotlin
package com.realmsoffate.game.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemEffectSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun abilityBonus_roundTrip() {
        val e: ItemEffect = ItemEffect.AbilityBonus("STR", 2)
        val s = json.encodeToString(ItemEffect.serializer(), e)
        assertTrue("type tag present: $s", s.contains("\"type\":\"ability\""))
        assertEquals(e, json.decodeFromString(ItemEffect.serializer(), s))
    }

    @Test fun skillBonus_roundTrip() {
        val e: ItemEffect = ItemEffect.SkillBonus("Stealth", 2)
        val s = json.encodeToString(ItemEffect.serializer(), e)
        assertEquals(e, json.decodeFromString(ItemEffect.serializer(), s))
    }

    @Test fun resistance_roundTrip() {
        val e: ItemEffect = ItemEffect.Resistance("fire")
        assertEquals(e, json.decodeFromString(ItemEffect.serializer(),
            json.encodeToString(ItemEffect.serializer(), e)))
    }

    @Test fun immunity_roundTrip() {
        val e: ItemEffect = ItemEffect.Immunity("poison")
        assertEquals(e, json.decodeFromString(ItemEffect.serializer(),
            json.encodeToString(ItemEffect.serializer(), e)))
    }

    @Test fun onHit_roundTrip() {
        val e: ItemEffect = ItemEffect.OnHit("1d4", "fire")
        assertEquals(e, json.decodeFromString(ItemEffect.serializer(),
            json.encodeToString(ItemEffect.serializer(), e)))
    }

    @Test fun maxHpBonus_roundTrip() {
        val e: ItemEffect = ItemEffect.MaxHpBonus(5)
        assertEquals(e, json.decodeFromString(ItemEffect.serializer(),
            json.encodeToString(ItemEffect.serializer(), e)))
    }

    @Test fun passiveTrigger_roundTrip() {
        val e: ItemEffect = ItemEffect.PassiveTrigger("cursed: -1 to all rolls")
        assertEquals(e, json.decodeFromString(ItemEffect.serializer(),
            json.encodeToString(ItemEffect.serializer(), e)))
    }

    @Test fun item_withoutEffectsField_decodes() {
        // Legacy save — no `effects` key present at all.
        val legacy = """{"name":"Rusty Sword","type":"weapon"}"""
        val item = json.decodeFromString(Item.serializer(), legacy)
        assertEquals("Rusty Sword", item.name)
        assertTrue("legacy effects default empty", item.effects.isEmpty())
    }

    @Test fun item_withEffects_roundTrip() {
        val item = Item(
            name = "Flametongue",
            type = "weapon",
            damage = "1d8",
            effects = listOf(ItemEffect.OnHit("1d6", "fire"), ItemEffect.AbilityBonus("CHA", 1))
        )
        val s = json.encodeToString(Item.serializer(), item)
        val decoded = json.decodeFromString(Item.serializer(), s)
        assertEquals(item, decoded)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.ItemEffectSerializationTest"`
Expected: FAIL — `ItemEffect` unresolved reference; `Item.effects` unresolved.

- [ ] **Step 3: Add sealed interface + effects fields**

Append to `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt` (just below the `Item` data class at line 27-36):

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

Add `effects` to `Item` (line 27-36):

```kotlin
@Serializable
data class Item(
    val name: String,
    val desc: String = "",
    val type: String = "item",
    val rarity: String = "common",
    var qty: Int = 1,
    var equipped: Boolean = false,
    val damage: String? = null,
    val ac: Int? = null,
    val effects: List<ItemEffect> = emptyList()
)
```

Add `effects` to `ItemSpec` (around line 471):

```kotlin
@Serializable
data class ItemSpec(
    val name: String = "",
    val desc: String = "",
    val type: String = "item",
    val rarity: String = "common",
    val effects: List<ItemEffect> = emptyList()
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.ItemEffectSerializationTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/Models.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/ItemEffectSerializationTest.kt
git commit -m "feat(equipment): ItemEffect sealed interface + effects field on Item/ItemSpec"
```

---

## Task 2: `EquipmentEffects.activeEffects` + `effectiveAbilities`

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/game/EquipmentEffectsTest.kt`

- [ ] **Step 1: Write failing tests**

Create `app/src/test/kotlin/com/realmsoffate/game/game/EquipmentEffectsTest.kt`:

```kotlin
package com.realmsoffate.game.game

import com.realmsoffate.game.data.Abilities
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.data.ItemEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentEffectsTest {

    private fun char(vararg items: Item, abilities: Abilities = Abilities()): Character =
        Character(name = "T", race = "Human", cls = "Fighter", abilities = abilities,
            inventory = items.toMutableList())

    @Test fun activeEffects_onlyEquipped() {
        val equipped = Item("A", equipped = true,
            effects = listOf(ItemEffect.AbilityBonus("STR", 2)))
        val unequipped = Item("B", equipped = false,
            effects = listOf(ItemEffect.AbilityBonus("STR", 99)))
        val ch = char(equipped, unequipped)
        val active = EquipmentEffects.activeEffects(ch)
        assertEquals(1, active.size)
        assertEquals(ItemEffect.AbilityBonus("STR", 2), active[0])
    }

    @Test fun effectiveAbilities_sumsAllEquippedAbilityBonuses() {
        val ring1 = Item("R1", type = "ring", equipped = true,
            effects = listOf(ItemEffect.AbilityBonus("STR", 2)))
        val ring2 = Item("R2", type = "ring", equipped = true,
            effects = listOf(ItemEffect.AbilityBonus("STR", 1), ItemEffect.AbilityBonus("DEX", 1)))
        val ch = char(ring1, ring2, abilities = Abilities(str = 10, dex = 12))
        val eff = EquipmentEffects.effectiveAbilities(ch)
        assertEquals(13, eff.str)
        assertEquals(13, eff.dex)
        // Originals untouched
        assertEquals(10, ch.abilities.str)
    }

    @Test fun effectiveAbilities_ignoresUnequipped() {
        val ring = Item("R", type = "ring", equipped = false,
            effects = listOf(ItemEffect.AbilityBonus("CON", 4)))
        val ch = char(ring, abilities = Abilities(con = 12))
        assertEquals(12, EquipmentEffects.effectiveAbilities(ch).con)
    }

    @Test fun effectiveAbilities_caseInsensitiveStat() {
        val ring = Item("R", type = "ring", equipped = true,
            effects = listOf(ItemEffect.AbilityBonus("str", 3)))
        val ch = char(ring, abilities = Abilities(str = 10))
        assertEquals(13, EquipmentEffects.effectiveAbilities(ch).str)
    }

    @Test fun activeEffects_emptyWhenNothingEquipped() {
        val ch = char(Item("X", effects = listOf(ItemEffect.Resistance("fire"))))
        assertTrue(EquipmentEffects.activeEffects(ch).isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.EquipmentEffectsTest"`
Expected: FAIL — `EquipmentEffects` unresolved.

- [ ] **Step 3: Create `EquipmentEffects.kt` with the two functions**

Create `app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt`:

```kotlin
package com.realmsoffate.game.game

import com.realmsoffate.game.data.Abilities
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.ItemEffect

object EquipmentEffects {

    fun activeEffects(ch: Character): List<ItemEffect> =
        ch.inventory.filter { it.equipped }.flatMap { it.effects }

    fun effectiveAbilities(ch: Character): Abilities {
        var str = ch.abilities.str
        var dex = ch.abilities.dex
        var con = ch.abilities.con
        var int = ch.abilities.int
        var wis = ch.abilities.wis
        var cha = ch.abilities.cha
        activeEffects(ch).forEach { e ->
            if (e is ItemEffect.AbilityBonus) {
                when (e.stat.uppercase()) {
                    "STR" -> str += e.amount
                    "DEX" -> dex += e.amount
                    "CON" -> con += e.amount
                    "INT" -> int += e.amount
                    "WIS" -> wis += e.amount
                    "CHA" -> cha += e.amount
                }
            }
        }
        return Abilities(str, dex, con, int, wis, cha)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.EquipmentEffectsTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/EquipmentEffectsTest.kt
git commit -m "feat(equipment): EquipmentEffects.activeEffects + effectiveAbilities"
```

---

## Task 3: `effectiveAc`

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/game/EquipmentEffectsTest.kt`

- [ ] **Step 1: Append failing tests to `EquipmentEffectsTest.kt`**

```kotlin
    @Test fun effectiveAc_unarmoredEquals10PlusDex() {
        val ch = char(abilities = Abilities(dex = 14))
        assertEquals(12, EquipmentEffects.effectiveAc(ch))
    }

    @Test fun effectiveAc_lightArmorAddsDex() {
        val leather = Item("Leather Armor", type = "armor", ac = 11, equipped = true)
        val ch = char(leather, abilities = Abilities(dex = 14))
        assertEquals(13, EquipmentEffects.effectiveAc(ch))
    }

    @Test fun effectiveAc_heavyArmorIgnoresDex() {
        val plate = Item("Plate Armor", type = "armor", ac = 18, equipped = true)
        val ch = char(plate, abilities = Abilities(dex = 16))
        assertEquals(18, EquipmentEffects.effectiveAc(ch))
    }

    @Test fun effectiveAc_chainMailIgnoresDex() {
        val chain = Item("Chain Mail", type = "armor", ac = 16, equipped = true)
        val ch = char(chain, abilities = Abilities(dex = 16))
        assertEquals(16, EquipmentEffects.effectiveAc(ch))
    }

    @Test fun effectiveAc_shieldAddsTwo() {
        val leather = Item("Leather Armor", type = "armor", ac = 11, equipped = true)
        val shield = Item("Shield", type = "shield", equipped = true)
        val ch = char(leather, shield, abilities = Abilities(dex = 14))
        assertEquals(15, EquipmentEffects.effectiveAc(ch)) // 11 + 2 dex + 2 shield
    }

    @Test fun effectiveAc_unequippedArmorIgnored() {
        val leather = Item("Leather Armor", type = "armor", ac = 11, equipped = false)
        val ch = char(leather, abilities = Abilities(dex = 14))
        assertEquals(12, EquipmentEffects.effectiveAc(ch))
    }

    @Test fun effectiveAc_usesEffectiveDex() {
        // Cloak of Dex +2 feeds into the unarmored AC calc.
        val cloak = Item("Cloak of Dex", type = "clothes", equipped = true,
            effects = listOf(ItemEffect.AbilityBonus("DEX", 2)))
        val ch = char(cloak, abilities = Abilities(dex = 14))
        assertEquals(13, EquipmentEffects.effectiveAc(ch)) // 10 + (14+2)/2-5 = 10+3
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.EquipmentEffectsTest"`
Expected: FAIL — `effectiveAc` unresolved.

- [ ] **Step 3: Add `effectiveAc` to `EquipmentEffects.kt`**

Append inside the `EquipmentEffects` object:

```kotlin
    /** Names whose presence disables the DEX contribution to AC. */
    private val HEAVY_ARMOR_TOKENS = listOf("Chain Mail", "Plate", "Ring Mail", "Splint")

    fun effectiveAc(ch: Character): Int {
        val eq = ch.inventory.filter { it.equipped }
        val armor = eq.firstOrNull { it.ac != null }
        val hasShield = eq.any { it.type.equals("shield", ignoreCase = true) }
        val effDex = effectiveAbilities(ch).dexMod
        val base = if (armor == null) 10 + effDex
            else if (HEAVY_ARMOR_TOKENS.any { armor.name.contains(it, ignoreCase = true) }) armor.ac!!
            else armor.ac!! + effDex
        return base + if (hasShield) 2 else 0
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.EquipmentEffectsTest"`
Expected: PASS (all 12 tests now).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/EquipmentEffectsTest.kt
git commit -m "feat(equipment): EquipmentEffects.effectiveAc (shields + heavy armor DEX rule)"
```

---

## Task 4: `effectiveMaxHp` + `skillBonuses` + `resistances`/`immunities` + `onHitRiders` + `passiveTriggers`

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/game/EquipmentEffectsTest.kt`

- [ ] **Step 1: Append failing tests**

```kotlin
    @Test fun effectiveMaxHp_addsMaxHpBonuses() {
        val amulet = Item("Amulet of Health", equipped = true,
            effects = listOf(ItemEffect.MaxHpBonus(5)))
        val ch = char(amulet).copy(maxHp = 20)
        assertEquals(25, EquipmentEffects.effectiveMaxHp(ch))
    }

    @Test fun effectiveMaxHp_ignoresUnequipped() {
        val amulet = Item("Amulet of Health", equipped = false,
            effects = listOf(ItemEffect.MaxHpBonus(5)))
        val ch = char(amulet).copy(maxHp = 20)
        assertEquals(20, EquipmentEffects.effectiveMaxHp(ch))
    }

    @Test fun skillBonuses_sumsByCanonicalKey() {
        val boots = Item("Boots of Stealth", equipped = true,
            effects = listOf(ItemEffect.SkillBonus("Stealth", 2)))
        val cloak = Item("Shadow Cloak", equipped = true,
            effects = listOf(ItemEffect.SkillBonus("stealth", 1), ItemEffect.SkillBonus("Perception", 1)))
        val ch = char(boots, cloak)
        val b = EquipmentEffects.skillBonuses(ch)
        assertEquals(3, b["Stealth"])
        assertEquals(1, b["Perception"])
    }

    @Test fun resistances_setOfLowercaseDamageTypes() {
        val ring1 = Item("R1", equipped = true, effects = listOf(ItemEffect.Resistance("Fire")))
        val ring2 = Item("R2", equipped = true, effects = listOf(ItemEffect.Resistance("fire"), ItemEffect.Resistance("cold")))
        val ch = char(ring1, ring2)
        assertEquals(setOf("fire", "cold"), EquipmentEffects.resistances(ch))
    }

    @Test fun immunities_onlyFromImmunityEffects() {
        val ring = Item("R", equipped = true,
            effects = listOf(ItemEffect.Immunity("poison"), ItemEffect.Resistance("fire")))
        val ch = char(ring)
        assertEquals(setOf("poison"), EquipmentEffects.immunities(ch))
        assertEquals(setOf("fire"), EquipmentEffects.resistances(ch))
    }

    @Test fun onHitRiders_orderPreserved() {
        val sword = Item("Flametongue", equipped = true,
            effects = listOf(ItemEffect.OnHit("1d6", "fire"), ItemEffect.OnHit("1d4", "radiant")))
        val ring = Item("Thunder Ring", equipped = true,
            effects = listOf(ItemEffect.OnHit("1d4", "thunder")))
        val ch = char(sword, ring)
        val riders = EquipmentEffects.onHitRiders(ch)
        assertEquals(3, riders.size)
        assertEquals("fire", riders[0].damageType)
        assertEquals("radiant", riders[1].damageType)
        assertEquals("thunder", riders[2].damageType)
    }

    @Test fun passiveTriggers_returnsText() {
        val cursed = Item("Cursed Ring", equipped = true,
            effects = listOf(ItemEffect.PassiveTrigger("cursed: -1 to all rolls")))
        val vorpal = Item("Vorpal Sword", equipped = true,
            effects = listOf(ItemEffect.PassiveTrigger("vorpal: on nat 20, narrate decapitation")))
        val ch = char(cursed, vorpal)
        val texts = EquipmentEffects.passiveTriggers(ch)
        assertEquals(2, texts.size)
        assertTrue(texts.any { it.startsWith("cursed") })
        assertTrue(texts.any { it.startsWith("vorpal") })
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.EquipmentEffectsTest"`
Expected: FAIL — 5 new helpers unresolved.

- [ ] **Step 3: Add the helpers**

Append inside the `EquipmentEffects` object:

```kotlin
    fun effectiveMaxHp(ch: Character): Int =
        ch.maxHp + activeEffects(ch).filterIsInstance<ItemEffect.MaxHpBonus>().sumOf { it.amount }

    fun skillBonuses(ch: Character): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        activeEffects(ch).filterIsInstance<ItemEffect.SkillBonus>().forEach { e ->
            val key = e.skill.trim().replaceFirstChar { c -> c.uppercaseChar() }
                .let { if (it.length > 1) it[0] + it.substring(1).lowercase() else it }
            out[key] = (out[key] ?: 0) + e.amount
        }
        return out
    }

    fun resistances(ch: Character): Set<String> =
        activeEffects(ch).filterIsInstance<ItemEffect.Resistance>()
            .map { it.damageType.lowercase() }.toSet()

    fun immunities(ch: Character): Set<String> =
        activeEffects(ch).filterIsInstance<ItemEffect.Immunity>()
            .map { it.damageType.lowercase() }.toSet()

    fun onHitRiders(ch: Character): List<ItemEffect.OnHit> =
        activeEffects(ch).filterIsInstance<ItemEffect.OnHit>()

    fun passiveTriggers(ch: Character): List<String> =
        activeEffects(ch).filterIsInstance<ItemEffect.PassiveTrigger>().map { it.text }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.EquipmentEffectsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/EquipmentEffectsTest.kt
git commit -m "feat(equipment): effectiveMaxHp, skillBonuses, resistances, immunities, onHit, triggers"
```

---

## Task 5: `promptSummary`

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/game/PromptSummaryTest.kt`

- [ ] **Step 1: Write failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/game/PromptSummaryTest.kt`:

```kotlin
package com.realmsoffate.game.game

import com.realmsoffate.game.data.Abilities
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.data.ItemEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptSummaryTest {

    private fun char(vararg items: Item): Character =
        Character(name = "T", race = "Human", cls = "Fighter",
            abilities = Abilities(), inventory = items.toMutableList())

    @Test fun empty_whenNoEquippedWithEffects() {
        val ch = char(Item("Apple", type = "consumable"))
        assertEquals("", EquipmentEffects.promptSummary(ch))
    }

    @Test fun rendersWeaponsArmorAbilitySkillResistOnHitTrigger() {
        val sword = Item("Flametongue", type = "weapon", damage = "1d8", equipped = true,
            effects = listOf(ItemEffect.OnHit("1d6", "fire")))
        val plate = Item("Plate Armor", type = "armor", ac = 18, equipped = true)
        val ringStr = Item("Ring of Strength", type = "ring", equipped = true,
            effects = listOf(ItemEffect.AbilityBonus("STR", 2)))
        val cloak = Item("Cloak of Stealth", type = "clothes", equipped = true,
            effects = listOf(ItemEffect.SkillBonus("Stealth", 2)))
        val ringFire = Item("Fire Resist Ring", type = "ring", equipped = true,
            effects = listOf(ItemEffect.Resistance("fire")))
        val amulet = Item("Amulet of Antivenom", type = "amulet", equipped = true,
            effects = listOf(ItemEffect.Immunity("poison")))
        val cursed = Item("Cursed Ring", type = "ring", equipped = true,
            effects = listOf(ItemEffect.PassiveTrigger("cursed: -1 to all rolls")))
        val ch = char(sword, plate, ringStr, cloak, ringFire, amulet, cursed)
        val out = EquipmentEffects.promptSummary(ch)
        assertTrue("heading present", out.startsWith("Equipped gear:"))
        assertTrue("weapon line", out.contains("Flametongue") && out.contains("1d8"))
        assertTrue("on-hit", out.contains("on hit: +1d6 fire"))
        assertTrue("armor line", out.contains("Plate Armor") && out.contains("AC 18"))
        assertTrue("ability bonus marked applied", out.contains("+2 STR") && out.contains("applied"))
        assertTrue("skill bonus", out.contains("+2 Stealth"))
        assertTrue("resistances line", out.contains("Resistances: fire"))
        assertTrue("immunities line", out.contains("Immunities: poison"))
        assertTrue("passive trigger", out.contains("cursed: -1 to all rolls"))
    }

    @Test fun skipsItemsWithNoEffectsAndNoStatFields() {
        // A plain equipped widget should not be rendered at all.
        val trinket = Item("Lucky Coin", type = "item", equipped = true)
        assertEquals("", EquipmentEffects.promptSummary(char(trinket)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.PromptSummaryTest"`
Expected: FAIL — `promptSummary` unresolved.

- [ ] **Step 3: Add `promptSummary`**

Append inside the `EquipmentEffects` object:

```kotlin
    fun promptSummary(ch: Character): String {
        val eq = ch.inventory.filter { it.equipped }
        val interesting = eq.filter { it.damage != null || it.ac != null || it.effects.isNotEmpty() }
        if (interesting.isEmpty() && resistances(ch).isEmpty() && immunities(ch).isEmpty())
            return ""
        val sb = StringBuilder()
        sb.appendLine("Equipped gear:")
        interesting.forEach { item ->
            val head = buildString {
                append("- ")
                append(item.name)
                append(" (")
                append(item.type)
                if (item.damage != null) append(", ${item.damage}")
                if (item.ac != null) append(", AC ${item.ac}")
                append(")")
            }
            sb.append(head)
            val extras = mutableListOf<String>()
            item.effects.forEach { e ->
                when (e) {
                    is ItemEffect.AbilityBonus -> extras.add("${signed(e.amount)} ${e.stat.uppercase()} (applied)")
                    is ItemEffect.SkillBonus   -> extras.add("${signed(e.amount)} ${e.skill} checks")
                    is ItemEffect.Resistance   -> { /* rolled up below */ }
                    is ItemEffect.Immunity     -> { /* rolled up below */ }
                    is ItemEffect.OnHit        -> extras.add("on hit: +${e.dice} ${e.damageType}")
                    is ItemEffect.MaxHpBonus   -> extras.add("${signed(e.amount)} max HP")
                    is ItemEffect.PassiveTrigger -> extras.add("passive: ${e.text}")
                }
            }
            if (extras.isNotEmpty()) sb.append(" — ").append(extras.joinToString("; "))
            sb.append('\n')
        }
        val res = resistances(ch)
        if (res.isNotEmpty()) sb.appendLine("Resistances: ${res.sorted().joinToString(", ")}")
        val imm = immunities(ch)
        if (imm.isNotEmpty()) sb.appendLine("Immunities: ${imm.sorted().joinToString(", ")}")
        return sb.toString().trimEnd()
    }

    private fun signed(n: Int): String = if (n >= 0) "+$n" else "$n"
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.PromptSummaryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/PromptSummaryTest.kt
git commit -m "feat(equipment): promptSummary renders equipped gear for LLM prompt"
```

---

## Task 6: Wire `effectiveAc` into `Classes.applyClassStart`

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/Classes.kt`

- [ ] **Step 1: Add a regression test for class start AC**

Append to `app/src/test/kotlin/com/realmsoffate/game/game/EquipmentEffectsTest.kt`:

```kotlin
    @Test fun applyClassStart_setsAcViaEffectiveAc() {
        val ch = Character(name = "T", race = "Human", cls = "Fighter",
            abilities = Abilities(dex = 14, con = 12))
        val cls = Classes.find("Fighter")!!
        applyClassStart(ch, cls)
        // Fighter starts with Chain Mail (heavy, AC 16, no DEX) + Shield (+2).
        assertEquals(18, ch.ac)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.EquipmentEffectsTest.applyClassStart_setsAcViaEffectiveAc"`
Expected: FAIL — likely mismatched AC because current logic at lines 199-201 doesn't add shield correctly, or does, depending on name match. Confirm the actual failure before writing the fix.

- [ ] **Step 3: Replace lines 199-201 in `Classes.kt` with EquipmentEffects call**

In `app/src/main/kotlin/com/realmsoffate/game/game/Classes.kt`, change `applyClassStart` (lines 194-203):

```kotlin
fun applyClassStart(ch: Character, cls: ClassDef) {
    ch.maxHp = Classes.rollHp(cls.name, ch.abilities.con)
    ch.hp = ch.maxHp
    ch.inventory.clear()
    ch.inventory.addAll(cls.startingItems.map { it.copy() })
    ch.ac = EquipmentEffects.effectiveAc(ch)
    if (cls.isCaster) Spells.grantStartingSpells(ch, cls)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.EquipmentEffectsTest"`
Expected: PASS (all EquipmentEffectsTest tests, including `applyClassStart_setsAcViaEffectiveAc`).

Also run the full suite to catch regressions:

Run: `gradle :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/Classes.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/EquipmentEffectsTest.kt
git commit -m "refactor(classes): applyClassStart delegates AC to EquipmentEffects.effectiveAc"
```

---

## Task 7: `equipToggle` recomputes AC + maxHP delta + HP clamp

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/game/EquipToggleEffectsTest.kt`

Note: `equipToggle` is not trivially testable without a VM harness. We'll extract the pure recomputation logic into a helper on `EquipmentEffects` and test that directly. The VM method will just call it.

- [ ] **Step 1: Write failing test for the pure helper**

Create `app/src/test/kotlin/com/realmsoffate/game/game/EquipToggleEffectsTest.kt`:

```kotlin
package com.realmsoffate.game.game

import com.realmsoffate.game.data.Abilities
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.data.ItemEffect
import org.junit.Assert.assertEquals
import org.junit.Test

class EquipToggleEffectsTest {

    private fun baseCharacter(vararg items: Item): Character =
        Character(name = "T", race = "Human", cls = "Fighter",
            abilities = Abilities(dex = 14), maxHp = 20, hp = 20, ac = 12,
            inventory = items.toMutableList())

    @Test fun equippingAmulet_raisesMaxHp_doesNotHealCurrent() {
        val amulet = Item("Amulet of Health", type = "amulet", equipped = false,
            effects = listOf(ItemEffect.MaxHpBonus(5)))
        val ch = baseCharacter(amulet).copy(hp = 18)
        val (maxHp, hp) = EquipmentEffects.recalcHpAfterEquip(
            oldChar = ch,
            newChar = ch.copy(inventory = mutableListOf(amulet.copy(equipped = true)))
        )
        assertEquals(25, maxHp)
        assertEquals(18, hp) // unchanged
    }

    @Test fun unequippingAmulet_dropsMaxHp_clampsHp() {
        val amulet = Item("Amulet of Health", type = "amulet", equipped = true,
            effects = listOf(ItemEffect.MaxHpBonus(5)))
        // Character has amulet already worked into maxHp=25, hp=25.
        val ch = baseCharacter(amulet).copy(maxHp = 25, hp = 25)
        val (maxHp, hp) = EquipmentEffects.recalcHpAfterEquip(
            oldChar = ch,
            newChar = ch.copy(inventory = mutableListOf(amulet.copy(equipped = false)))
        )
        assertEquals(20, maxHp)
        assertEquals(20, hp) // clamped down
    }

    @Test fun unequippingAmulet_whenAlreadyWounded_keepsWoundedHp() {
        val amulet = Item("Amulet of Health", type = "amulet", equipped = true,
            effects = listOf(ItemEffect.MaxHpBonus(5)))
        val ch = baseCharacter(amulet).copy(maxHp = 25, hp = 12)
        val (maxHp, hp) = EquipmentEffects.recalcHpAfterEquip(
            oldChar = ch,
            newChar = ch.copy(inventory = mutableListOf(amulet.copy(equipped = false)))
        )
        assertEquals(20, maxHp)
        assertEquals(12, hp)
    }

    @Test fun equipSwap_betweenArmorPieces_changesAcLive() {
        val leather = Item("Leather Armor", type = "armor", ac = 11, equipped = true)
        val plate = Item("Plate Armor", type = "armor", ac = 18, equipped = false)
        val ch = baseCharacter(leather, plate).copy(ac = 13) // 11 + dex 2
        val newCh = ch.copy(inventory = mutableListOf(
            leather.copy(equipped = false),
            plate.copy(equipped = true)
        ))
        val newAc = EquipmentEffects.effectiveAc(newCh)
        assertEquals(18, newAc) // plate ignores dex
    }

    @Test fun abilityBonus_reflectedInEffectiveAbilities_notBase() {
        val ring = Item("Ring of Strength", type = "ring", equipped = true,
            effects = listOf(ItemEffect.AbilityBonus("STR", 2)))
        val ch = baseCharacter(ring).copy(abilities = Abilities(str = 14))
        assertEquals(14, ch.abilities.str) // base untouched
        assertEquals(16, EquipmentEffects.effectiveAbilities(ch).str)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.EquipToggleEffectsTest"`
Expected: FAIL — `EquipmentEffects.recalcHpAfterEquip` unresolved.

- [ ] **Step 3: Add `recalcHpAfterEquip` to `EquipmentEffects.kt`**

Append inside the `EquipmentEffects` object:

```kotlin
    /**
     * Given the character before and after an equip-state change, return the (newMaxHp, newHp)
     * pair that should be written back. Bases the delta on MaxHpBonus effects, preserves wounds
     * (never heals on equip), and clamps down on unequip.
     */
    fun recalcHpAfterEquip(oldChar: Character, newChar: Character): Pair<Int, Int> {
        val oldBonus = oldChar.inventory.filter { it.equipped }
            .flatMap { it.effects }.filterIsInstance<ItemEffect.MaxHpBonus>().sumOf { it.amount }
        val newBonus = newChar.inventory.filter { it.equipped }
            .flatMap { it.effects }.filterIsInstance<ItemEffect.MaxHpBonus>().sumOf { it.amount }
        val delta = newBonus - oldBonus
        val newMax = (oldChar.maxHp + delta).coerceAtLeast(1)
        val newHp = oldChar.hp.coerceAtMost(newMax)
        return newMax to newHp
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.EquipToggleEffectsTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Wire into `GameViewModel.equipToggle`**

In `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`, modify `equipToggle` (lines 1645-1675). Replace the final two lines (`_ui.value = s.copy(...)` and what follows) so the method ends like this:

```kotlin
        inv[idx] = inv[idx].copy(equipped = nowEquipped)
        val newCh = ch.copy(inventory = inv)
        val (newMaxHp, newHp) = EquipmentEffects.recalcHpAfterEquip(oldChar = ch, newChar = newCh)
        val newAc = EquipmentEffects.effectiveAc(newCh)
        _ui.value = s.copy(character = newCh.copy(maxHp = newMaxHp, hp = newHp, ac = newAc))
        val action = when {
            !nowEquipped -> "I unequip my ${item.name}"
            displaced.isEmpty() -> "I equip my ${item.name}"
            else -> "I unequip my ${displaced.joinToString(" and ")} and equip my ${item.name}"
        }
        submitAction(action)
    }
```

Make sure to add the import at the top of the file:

```kotlin
import com.realmsoffate.game.game.EquipmentEffects
```

(If the file is already in package `com.realmsoffate.game.game`, the import is not needed — same-package access.)

- [ ] **Step 6: Rerun tests**

Run: `gradle :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/EquipToggleEffectsTest.kt
git commit -m "feat(equipment): equipToggle recomputes AC + maxHp delta + HP clamp"
```

---

## Task 8: Swap ability-check sites to effective abilities

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`

- [ ] **Step 1: Read the three ability-check sites**

Sites: lines 833, 857, 913. Each currently reads `char.abilities.modByName(ability)`. We want `EquipmentEffects.effectiveAbilities(char).modByName(ability)`.

- [ ] **Step 2: Replace at line 833**

In `GameViewModel.kt` replace (line ~833):

```kotlin
val mod = char.abilities.modByName(ability)
```

with:

```kotlin
val mod = EquipmentEffects.effectiveAbilities(char).modByName(ability)
```

- [ ] **Step 3: Replace at line 857**

Same edit at line 857.

- [ ] **Step 4: Replace at line 913**

Same edit at line 913.

- [ ] **Step 5: Also update `/describe` stat print at line 423**

Replace line 423 with:

```kotlin
val effAb = EquipmentEffects.effectiveAbilities(ch)
appendLine("STR:${effAb.str} DEX:${effAb.dex} CON:${effAb.con} INT:${effAb.int} WIS:${effAb.wis} CHA:${effAb.cha}")
```

- [ ] **Step 6: Build + run tests**

Run: `gradle :app:testDebugUnitTest`
Expected: PASS (no new tests — this is a routing change).

Run: `gradle :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "refactor(checks): ability math reads EquipmentEffects.effectiveAbilities"
```

---

## Task 9: Emit `promptSummary` in LLM prompt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`

- [ ] **Step 1: Replace line 427**

Current:

```kotlin
appendLine("Equipped: ${ch.inventory.filter { it.equipped }.joinToString(", ") { it.name }.ifBlank { "nothing" }}")
```

Replace with:

```kotlin
val gearSummary = EquipmentEffects.promptSummary(ch)
if (gearSummary.isBlank()) appendLine("Equipped: nothing")
else { appendLine(); appendLine(gearSummary) }
```

- [ ] **Step 2: Replace line 1180**

Current:

```kotlin
val inv = ch.inventory.filter { it.equipped }.joinToString(", ") { it.name }.ifBlank { "nothing" }
```

Find the `appendLine` or concatenation that uses `inv` and replace the whole segment with an equivalent block that emits `EquipmentEffects.promptSummary(ch)` in the same way — the surrounding prompt format at this site is the second LLM call. Keep the caller's existing "Equipped: ..." fallback phrasing if the summary is blank.

The concrete edit: read around line 1180 for 15 lines of context, locate the nearest sink (`appendLine`, `+`, `"""..."""` interpolation) that uses `inv`, and substitute:

```kotlin
val gear = EquipmentEffects.promptSummary(ch).ifBlank { "Equipped: nothing" }
```

…then interpolate `$gear` where `$inv` was used.

- [ ] **Step 3: Build**

Run: `gradle :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all tests**

Run: `gradle :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "feat(prompt): send EquipmentEffects.promptSummary to LLM in both prompt sites"
```

---

## Task 10: LLM loot schema — extend `items_gained` with `effects`

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/data/ItemSpecEffectsParseTest.kt`

- [ ] **Step 1: Write failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/ItemSpecEffectsParseTest.kt`:

```kotlin
package com.realmsoffate.game.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemSpecEffectsParseTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun itemSpec_parsesEffectsArray() {
        val raw = """
        {
          "name": "Ring of Fire Resistance",
          "desc": "Shimmers with heat.",
          "type": "ring",
          "rarity": "uncommon",
          "effects": [
            {"type":"resist","damageType":"fire"}
          ]
        }
        """.trimIndent()
        val spec = json.decodeFromString(ItemSpec.serializer(), raw)
        assertEquals("Ring of Fire Resistance", spec.name)
        assertEquals(1, spec.effects.size)
        val e = spec.effects[0]
        assertTrue(e is ItemEffect.Resistance)
        assertEquals("fire", (e as ItemEffect.Resistance).damageType)
    }

    @Test fun itemSpec_absentEffects_defaultsEmpty() {
        val raw = """{"name":"Apple","type":"consumable"}"""
        val spec = json.decodeFromString(ItemSpec.serializer(), raw)
        assertTrue(spec.effects.isEmpty())
    }

    @Test fun itemSpec_multipleEffects() {
        val raw = """
        {
          "name": "Flametongue",
          "type": "weapon",
          "effects": [
            {"type":"onhit","dice":"1d6","damageType":"fire"},
            {"type":"ability","stat":"CHA","amount":1}
          ]
        }
        """.trimIndent()
        val spec = json.decodeFromString(ItemSpec.serializer(), raw)
        assertEquals(2, spec.effects.size)
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.ItemSpecEffectsParseTest"`
Expected: PASS (ItemSpec already has effects from Task 1 — this test locks the parse behavior).

- [ ] **Step 3: Update the prompt schema at `Prompts.kt:221`**

Current (line 221):

```kotlin
    "items_gained": [{"name":"","desc":"","type":"weapon|armor|shield|amulet|ring|clothes|consumable|item","rarity":"common|uncommon|rare|epic|legendary"}],
```

Replace with:

```kotlin
    "items_gained": [{"name":"","desc":"","type":"weapon|armor|shield|amulet|ring|clothes|consumable|item","rarity":"common|uncommon|rare|epic|legendary","effects":[
        // ZERO OR MORE of any of:
        //   {"type":"ability","stat":"STR|DEX|CON|INT|WIS|CHA","amount":1},
        //   {"type":"skill","skill":"Stealth","amount":2},
        //   {"type":"resist","damageType":"fire"},
        //   {"type":"immune","damageType":"poison"},
        //   {"type":"onhit","dice":"1d4","damageType":"fire"},
        //   {"type":"maxhp","amount":5},
        //   {"type":"trigger","text":"cursed: -1 to all rolls"}
    ]}],
```

- [ ] **Step 4: Add narrator guidance about equipped gear**

Near the existing "Mechanical side effects go in the "metadata" object" line (~line 277), add a new paragraph — check nearby lines first to place it naturally:

```text
EQUIPPED GEAR (see the "Equipped gear:" block in the character context):
- Ability bonuses from equipped items are ALREADY folded into the STR/DEX/CON/INT/WIS/CHA scores you see. Do not re-apply them to checks or saves.
- Skill bonuses are NOT folded in — add them to the relevant skill check roll.
- Resistances halve incoming damage of that type. Immunities negate it entirely.
- On-hit riders add their damage to successful weapon attacks — narrate and include the rider damage in your numbers.
- Passive-trigger text is narrative law: honor it exactly as written for the duration the item is equipped.
```

Find a free spot in the narrator-instructions text (search for existing guidance around metadata / numeric rules) and insert the block. It does not need to replace any existing text — it's additive.

- [ ] **Step 5: Build + test**

Run: `gradle :app:testDebugUnitTest`
Expected: PASS.

Run: `gradle :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/ItemSpecEffectsParseTest.kt
git commit -m "feat(prompt): LLM loot schema + narrator rules for equipped effects"
```

---

## Task 11: Carry `ItemSpec.effects` onto materialized `Item`

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` (or reducer — whichever file materializes `items_gained`)

- [ ] **Step 1: Find the materialization site**

Run: `grep -n "items_gained\|itemsGained" app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt app/src/main/kotlin/com/realmsoffate/game/game/reducers/*.kt app/src/main/kotlin/com/realmsoffate/game/game/handlers/*.kt`

There will be a spot that constructs `Item(name = spec.name, desc = spec.desc, type = spec.type, rarity = spec.rarity, ...)`. Record the file path and line.

- [ ] **Step 2: Write a failing test**

Append to `app/src/test/kotlin/com/realmsoffate/game/data/ItemSpecEffectsParseTest.kt`:

```kotlin
    @Test fun itemSpecToItem_preservesEffects() {
        val spec = ItemSpec(
            name = "Ring of Fire Resistance",
            type = "ring",
            effects = listOf(ItemEffect.Resistance("fire"))
        )
        // Direct construction mirrors what the reducer should do.
        val item = Item(name = spec.name, desc = spec.desc, type = spec.type,
            rarity = spec.rarity, effects = spec.effects)
        assertEquals(1, item.effects.size)
        assertTrue(item.effects[0] is ItemEffect.Resistance)
    }
```

- [ ] **Step 3: Run and confirm pass** (the test is a property of the data class — it passes immediately, but it documents the contract the reducer must honor).

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.ItemSpecEffectsParseTest.itemSpecToItem_preservesEffects"`
Expected: PASS.

- [ ] **Step 4: Update the materialization site**

At the file/line found in Step 1, ensure the `Item(...)` constructor call receives `effects = spec.effects`. Example fix:

```kotlin
// Before:
Item(name = spec.name, desc = spec.desc, type = spec.type, rarity = spec.rarity)
// After:
Item(name = spec.name, desc = spec.desc, type = spec.type, rarity = spec.rarity, effects = spec.effects)
```

- [ ] **Step 5: Build**

Run: `gradle :app:assembleDebug && gradle :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests PASS.

- [ ] **Step 6: Commit**

```bash
git add <file-modified-in-step-4> \
        app/src/test/kotlin/com/realmsoffate/game/data/ItemSpecEffectsParseTest.kt
git commit -m "feat(loot): carry ItemSpec.effects onto player Item on items_gained"
```

---

## Task 12: Inventory UI — show effects on selected item

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/InventoryPage.kt`

- [ ] **Step 1: Locate `SelectedItemCard`**

It's at `InventoryPage.kt:253` approximately. There's a block (around line 234) that already shows `AC X` / `damage` if present.

- [ ] **Step 2: Add effects rendering**

Inside the card, after the existing AC/damage Texts and before the action buttons, add:

```kotlin
if (item.effects.isNotEmpty()) {
    Spacer(Modifier.height(4.dp))
    item.effects.forEach { e ->
        val line = when (e) {
            is ItemEffect.AbilityBonus -> "${if (e.amount >= 0) "+${e.amount}" else "${e.amount}"} ${e.stat.uppercase()}"
            is ItemEffect.SkillBonus   -> "${if (e.amount >= 0) "+${e.amount}" else "${e.amount}"} ${e.skill}"
            is ItemEffect.Resistance   -> "Resist ${e.damageType}"
            is ItemEffect.Immunity     -> "Immune ${e.damageType}"
            is ItemEffect.OnHit        -> "On hit: +${e.dice} ${e.damageType}"
            is ItemEffect.MaxHpBonus   -> "${if (e.amount >= 0) "+${e.amount}" else "${e.amount}"} max HP"
            is ItemEffect.PassiveTrigger -> e.text
        }
        Text(line, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

Add the import at the top of the file:

```kotlin
import com.realmsoffate.game.data.ItemEffect
```

- [ ] **Step 3: Deploy and smoke-test**

Run: `gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735`

Open inventory, equip a test item with effects (use debug bridge to inject one) and confirm the effect lines render.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/panels/InventoryPage.kt
git commit -m "feat(inventory-ui): render equipment effect list on selected item"
```

---

## Task 13: Integration sanity + full-suite green

**Files:** (verification only)

- [ ] **Step 1: Full unit suite**

Run: `gradle test`
Expected: All tests PASS. No skipped or ignored.

- [ ] **Step 2: Lint**

Run: `gradle lint`
Expected: No new errors introduced (warnings from pre-existing code are acceptable).

- [ ] **Step 3: Smoke test on emulator**

- Equip a weapon, armor, and shield — confirm AC updates live in the UI.
- Use debug bridge to inject an item with `MaxHpBonus(5)`; equip it; confirm maxHp rises by 5 and HP bar does not auto-heal.
- Unequip it while wounded; confirm maxHp drops and current HP is preserved (not clamped up).
- Use debug bridge to inject an item with `AbilityBonus("STR", 2)`; confirm `/describe` shows boosted STR.

- [ ] **Step 4: Final commit (docs touch-up or nothing)**

If any spec/plan edits surfaced during execution, commit them. Otherwise no final commit is required.

---

## Self-Review

**Spec coverage:**

- ✅ #1 Weapon damage — LLM-informed via `promptSummary` weapon line (Tasks 5, 9).
- ✅ #2 AC / shield — `effectiveAc` + `applyClassStart` + `equipToggle` (Tasks 3, 6, 7).
- ✅ #3 Ability bonuses — `effectiveAbilities` + call-site swap (Tasks 2, 8).
- ✅ #4 Skill bonuses — `skillBonuses` + prompt summary + narrator guidance (Tasks 4, 5, 10).
- ✅ #5 Resistances/immunities — `resistances`/`immunities` + prompt rollup + narrator rule (Tasks 4, 5, 10).
- ✅ #6 On-hit / passive triggers — `onHitRiders` + `passiveTriggers` + prompt + narrator rule (Tasks 4, 5, 10).
- ✅ #7 MaxHP — `effectiveMaxHp` + `recalcHpAfterEquip` + equipToggle wire-in (Tasks 4, 7).
- ✅ Tests for all seven — EquipmentEffectsTest, EquipToggleEffectsTest, PromptSummaryTest, ItemEffectSerializationTest, ItemSpecEffectsParseTest.
- ✅ UI — Task 12.
- ✅ LLM schema + narrator rules — Task 10.
- ✅ Migration — default-empty `effects` field means old saves load.

**Placeholder scan:** No TBD/TODO/vague-error-handling language. Only one placeholder-style reference: Task 11 Step 1 asks the engineer to grep for the materialization site because it may live in one of several reducer files. The grep command is exact and the expected change is a one-line constructor addition shown in Step 4.

**Type consistency:** `ItemEffect` subtypes and their field names (`stat`, `amount`, `skill`, `damageType`, `dice`, `text`) match between Models.kt, EquipmentEffects.kt, and every test. `recalcHpAfterEquip(oldChar, newChar)` signature is consistent between Task 7 Step 3 and Step 5.
