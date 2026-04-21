package com.realmsoffate.game.game.reducers

import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.ParsedReply
import com.realmsoffate.game.game.ParsedReplyBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for NPC-log dedup + hyphenated-name bugs.
 *
 * Covers:
 *  - "Mira Cole" vs "Mira-Cole" vs "mira-cole" being treated as the same NPC.
 *  - AI hallucinating a fresh ID for an NPC we already logged.
 *  - Auto-register copying a slug ref ("mira-cole") into the display name.
 *  - AI putting the slug into the JSON `name` field.
 */
class NpcLogReducerTest {

    private fun applyWith(
        npcLog: List<LogNpc> = emptyList(),
        parsed: ParsedReply,
        turn: Int = 5,
        loc: String = "Town"
    ) = NpcLogReducer.apply(
        npcLog = npcLog,
        combat = null,
        parsed = parsed,
        currentTurn = turn,
        currentLocName = loc
    )

    private fun npc(id: String = "", name: String, race: String = "") = LogNpc(
        id = id,
        name = name,
        race = race,
        metTurn = 1,
        lastSeenTurn = 1
    )

    @Test
    fun `legacy NPC with hyphen variant dedupes against existing space-separated name`() {
        val existing = npc(id = "mira-cole", name = "Mira Cole")
        val parsed = ParsedReplyBuilder().addNpcMet(npc(name = "Mira-Cole", race = "Human")).build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed)

        assertEquals("Should not create a duplicate", 1, result.npcLog.size)
        assertEquals("mira-cole", result.npcLog[0].id)
        assertEquals("Mira Cole", result.npcLog[0].name)
    }

    @Test
    fun `ID-first with hallucinated slug for same name dedupes via name fallback`() {
        val existing = npc(id = "mira-cole", name = "Mira Cole")
        val parsed = ParsedReplyBuilder()
            .addNpcMet(npc(id = "mira-cole-mercer", name = "Mira Cole", race = "Human"))
            .build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed)

        assertEquals("Should not create a duplicate", 1, result.npcLog.size)
        assertEquals("mira-cole", result.npcLog[0].id)
        assertEquals("Mira Cole", result.npcLog[0].name)
        assertEquals("Human", result.npcLog[0].race)
    }

    @Test
    fun `ID-first match does not clobber display name when incoming name is the slug`() {
        val existing = npc(id = "mira-cole", name = "Mira Cole")
        // AI emits slug in the name field by mistake.
        val parsed = ParsedReplyBuilder()
            .addNpcMet(npc(id = "mira-cole", name = "mira-cole"))
            .build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed)

        assertEquals(1, result.npcLog.size)
        assertEquals(
            "Pretty display name must survive slug-as-name AI output",
            "Mira Cole",
            result.npcLog[0].name
        )
    }

    @Test
    fun `new NPC with slug-as-name field gets reverse-slugified for display`() {
        val parsed = ParsedReplyBuilder()
            .addNpcMet(npc(id = "mira-cole", name = "mira-cole"))
            .build()

        val result = applyWith(parsed = parsed)

        assertEquals(1, result.npcLog.size)
        assertEquals("mira-cole", result.npcLog[0].id)
        assertEquals("Mira Cole", result.npcLog[0].name)
    }

    @Test
    fun `auto-register from slug-form dialog ref uses reverse-slugified display name`() {
        val parsed = ParsedReplyBuilder()
            .addNpcDialog("mira-cole", "Good to meet you.")
            .build()

        val result = applyWith(parsed = parsed)

        assertEquals(1, result.npcLog.size)
        assertEquals("mira-cole", result.npcLog[0].id)
        assertEquals(
            "Stub display name should be derived from the slug, not the slug itself",
            "Mira Cole",
            result.npcLog[0].name
        )
    }

    @Test
    fun `auto-register slug ref matches existing pretty-named NPC instead of duplicating`() {
        val existing = npc(id = "mira-cole", name = "Mira Cole")
        val parsed = ParsedReplyBuilder()
            .addNpcDialog("mira-cole", "Welcome back.")
            .build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed)

        assertEquals(1, result.npcLog.size)
        assertEquals("Mira Cole", result.npcLog[0].name)
        assertEquals("dialogue attached to matched NPC", 1, result.npcLog[0].dialogueHistory.size)
    }

    @Test
    fun `legacy NPC with slug-as-name gets reverse-slugified and deduped against existing`() {
        val existing = npc(id = "mira-cole", name = "Mira Cole")
        val parsed = ParsedReplyBuilder().addNpcMet(npc(name = "mira-cole")).build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed)

        assertEquals(1, result.npcLog.size)
        assertEquals("Mira Cole", result.npcLog[0].name)
    }
}
