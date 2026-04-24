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

    @Test fun itemSpecToItem_preservesEffects() {
        val spec = ItemSpec(
            name = "Ring of Fire Resistance",
            type = "ring",
            effects = listOf(ItemEffect.Resistance("fire"))
        )
        val item = Item(name = spec.name, desc = spec.desc, type = spec.type,
            rarity = spec.rarity, effects = spec.effects)
        assertEquals(1, item.effects.size)
        assertTrue(item.effects[0] is ItemEffect.Resistance)
    }
}
