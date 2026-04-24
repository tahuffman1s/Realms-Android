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
        val ring2 = Item("R2", equipped = true,
            effects = listOf(ItemEffect.Resistance("fire"), ItemEffect.Resistance("cold")))
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
}
