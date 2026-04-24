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
