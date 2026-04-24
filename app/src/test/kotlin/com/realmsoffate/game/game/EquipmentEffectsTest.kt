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
        assertEquals(15, EquipmentEffects.effectiveAc(ch))
    }

    @Test fun effectiveAc_unequippedArmorIgnored() {
        val leather = Item("Leather Armor", type = "armor", ac = 11, equipped = false)
        val ch = char(leather, abilities = Abilities(dex = 14))
        assertEquals(12, EquipmentEffects.effectiveAc(ch))
    }

    @Test fun effectiveAc_usesEffectiveDex() {
        val cloak = Item("Cloak of Dex", type = "clothes", equipped = true,
            effects = listOf(ItemEffect.AbilityBonus("DEX", 2)))
        val ch = char(cloak, abilities = Abilities(dex = 14))
        assertEquals(13, EquipmentEffects.effectiveAc(ch))
    }
}
