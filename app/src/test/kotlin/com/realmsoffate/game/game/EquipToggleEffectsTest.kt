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
        assertEquals(18, hp)
    }

    @Test fun unequippingAmulet_dropsMaxHp_clampsHp() {
        val amulet = Item("Amulet of Health", type = "amulet", equipped = true,
            effects = listOf(ItemEffect.MaxHpBonus(5)))
        val ch = baseCharacter(amulet).copy(maxHp = 25, hp = 25)
        val (maxHp, hp) = EquipmentEffects.recalcHpAfterEquip(
            oldChar = ch,
            newChar = ch.copy(inventory = mutableListOf(amulet.copy(equipped = false)))
        )
        assertEquals(20, maxHp)
        assertEquals(20, hp)
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
        val ch = baseCharacter(leather, plate).copy(ac = 13)
        val newCh = ch.copy(inventory = mutableListOf(
            leather.copy(equipped = false),
            plate.copy(equipped = true)
        ))
        assertEquals(18, EquipmentEffects.effectiveAc(newCh))
    }

    @Test fun abilityBonus_reflectedInEffectiveAbilities_notBase() {
        val ring = Item("Ring of Strength", type = "ring", equipped = true,
            effects = listOf(ItemEffect.AbilityBonus("STR", 2)))
        val ch = baseCharacter(ring).copy(abilities = Abilities(str = 14))
        assertEquals(14, ch.abilities.str)
        assertEquals(16, EquipmentEffects.effectiveAbilities(ch).str)
    }
}
