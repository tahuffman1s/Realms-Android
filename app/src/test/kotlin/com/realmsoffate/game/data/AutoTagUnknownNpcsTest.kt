package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoTagUnknownNpcsTest {

    private fun parsedWithSegments(vararg segments: NarrationSegmentData): ParsedReply =
        ParsedReply(
            scene = "default", sceneDesc = "", narration = "",
            choices = emptyList(), damage = 0, heal = 0, xp = 0,
            goldGained = 0, goldLost = 0, itemsGained = emptyList(),
            checks = emptyList(), npcsMet = emptyList(),
            questStarts = emptyList(), questUpdates = emptyList(),
            questComplete = emptyList(), questFails = emptyList(),
            shops = emptyList(), travelTo = null, partyJoins = emptyList(),
            timeOfDay = null, moralDelta = 0, repDeltas = emptyList(),
            worldEventHook = null,
            segments = segments.toList(),
            source = ParseSource.JSON
        )

    @Test
    fun `detects two-word proper noun in prose segment`() {
        val parsed = parsedWithSegments(
            NarrationSegmentData.Prose("You meet Mira Cole at the inn.")
        )
        val specs = AutoTagUnknownNpcs.scan(
            parsed = parsed,
            existingNpcs = emptyList(),
            currentLoc = "Hightower",
            turn = 5
        )
        assertEquals(1, specs.size)
        assertEquals("Mira Cole", specs[0].name)
    }

    @Test
    fun `skips existing NPCs`() {
        val parsed = parsedWithSegments(
            NarrationSegmentData.Prose("Mira Cole smiles.")
        )
        val specs = AutoTagUnknownNpcs.scan(
            parsed = parsed,
            existingNpcs = listOf(LogNpc(id = "m", name = "Mira Cole", metTurn = 1, lastSeenTurn = 1)),
            currentLoc = "Hightower",
            turn = 5
        )
        assertTrue(specs.isEmpty())
    }

    @Test
    fun `skips common words at start`() {
        val parsed = parsedWithSegments(
            NarrationSegmentData.Prose("The Queen rules here.")
        )
        val specs = AutoTagUnknownNpcs.scan(
            parsed = parsed,
            existingNpcs = emptyList(),
            currentLoc = "Hightower",
            turn = 5
        )
        assertTrue(specs.none { it.name.startsWith("The ") })
    }

    @Test
    fun `scan picks up unknown NPC names from envelope segments`() {
        val raw = """
            {"segments":[
              {"kind":"npc_dialog","name":"new-baron","text":"Halt! State your business, Baron Aldric commands."},
              {"kind":"prose","text":"Baron Aldric turns to face you."}
            ]}
        """.trimIndent()
        val parsed = EnvelopeParser.parse(raw, currentTurn = 1)
        val result = AutoTagUnknownNpcs.scan(
            parsed = parsed,
            existingNpcs = emptyList(),
            currentLoc = "Throne Room",
            turn = 1
        )
        // "Baron Aldric" is a two-word proper noun that should be detected
        assertTrue(
            "Expected Baron Aldric to be detected, got: ${result.map { it.name }}",
            result.any { it.name == "Baron Aldric" }
        )
    }

    @Test
    fun `skips equipment-like names ending in item category word`() {
        val parsed = parsedWithSegments(
            NarrationSegmentData.Prose("The Bone Shield deflects the blow. Iron Sword glints. Mira Cole watches.")
        )
        val specs = AutoTagUnknownNpcs.scan(
            parsed = parsed,
            existingNpcs = emptyList(),
            currentLoc = "Hightower",
            turn = 5
        )
        val names = specs.map { it.name }
        assertTrue("Bone Shield should be filtered: $names", "Bone Shield" !in names)
        assertTrue("Iron Sword should be filtered: $names", "Iron Sword" !in names)
        assertTrue("Mira Cole should still be detected: $names", "Mira Cole" in names)
    }

    @Test
    fun `skips names matching inventory items`() {
        val parsed = parsedWithSegments(
            NarrationSegmentData.Prose("Stormlight Brand hums in your hand. You see Elric Vance.")
        )
        val specs = AutoTagUnknownNpcs.scan(
            parsed = parsed,
            existingNpcs = emptyList(),
            currentLoc = "Hightower",
            turn = 5,
            itemNames = setOf("Stormlight Brand")
        )
        val names = specs.map { it.name }
        assertTrue("Stormlight Brand should be filtered as inventory: $names", "Stormlight Brand" !in names)
        assertTrue("Elric Vance should be detected: $names", "Elric Vance" in names)
    }

    @Test
    fun `returns empty when all segments are blank`() {
        val parsed = parsedWithSegments(
            NarrationSegmentData.Prose("   "),
            NarrationSegmentData.Aside("   ")
        )
        val specs = AutoTagUnknownNpcs.scan(
            parsed = parsed,
            existingNpcs = emptyList(),
            currentLoc = "Hightower",
            turn = 1
        )
        assertTrue(specs.isEmpty())
    }
}
