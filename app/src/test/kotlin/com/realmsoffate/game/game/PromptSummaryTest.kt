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
        val trinket = Item("Lucky Coin", type = "item", equipped = true)
        assertEquals("", EquipmentEffects.promptSummary(char(trinket)))
    }
}
