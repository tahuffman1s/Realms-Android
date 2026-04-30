package com.realmsoffate.game.game.reducers

import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.ParsedReply
import com.realmsoffate.game.game.ParsedReplyBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `npcsMet rename promotes a recent descriptor-style NPC instead of duplicating`() {
        val existing = npc(id = "grey-cloak-hunter", name = "Grey Cloak Hunter")
            .copy(
                lastLocation = "Nightbriar",
                lastSeenTurn = 5,
                dialogueHistory = mutableListOf(
                    "T2: \"Name's Voss. I've been tracking your double for three months.\"",
                    "T5: \"Name's Voss, by the way — Voss Ironhand.\""
                )
            )
        val parsed = ParsedReplyBuilder()
            .addNpcMet(npc(name = "Voss Ironhand", race = "Human"))
            .build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed, turn = 5, loc = "Nightbriar")

        assertEquals("Should rename in place, not duplicate", 1, result.npcLog.size)
        val merged = result.npcLog[0]
        assertEquals("Voss Ironhand", merged.name)
        assertEquals("grey-cloak-hunter", merged.id)
        assertEquals("Human", merged.race)
        assertEquals(2, merged.dialogueHistory.size)
        assertTrue("Old slug should be preserved as alias", "grey-cloak-hunter" in merged.aliases)
        assertTrue(
            "Old name should be preserved as alias",
            merged.aliases.any { it.equals("Grey Cloak Hunter", true) }
        )
    }

    @Test
    fun `npcsMet does not rename when no descriptor-style entry is present`() {
        val existing = npc(id = "mira-cole", name = "Mira Cole", race = "Human")
            .copy(lastLocation = "Hightower", lastSeenTurn = 5)
        val parsed = ParsedReplyBuilder()
            .addNpcMet(npc(name = "Voss Ironhand", race = "Human"))
            .build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed, turn = 5, loc = "Hightower")

        assertEquals("Mira Cole is not a descriptor and should remain", 2, result.npcLog.size)
        assertTrue(result.npcLog.any { it.name == "Mira Cole" })
        assertTrue(result.npcLog.any { it.name == "Voss Ironhand" })
    }

    @Test
    fun `npcsMet does not rename when descriptor entry is in a different location`() {
        val existing = npc(id = "grey-cloak-hunter", name = "Grey Cloak Hunter")
            .copy(lastLocation = "Whispering Marsh", lastSeenTurn = 5)
        val parsed = ParsedReplyBuilder()
            .addNpcMet(npc(name = "Voss Ironhand", race = "Human"))
            .build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed, turn = 5, loc = "Nightbriar")

        assertEquals("Different location => no rename", 2, result.npcLog.size)
    }

    @Test
    fun `npcsMet does not rename when descriptor entry is too old`() {
        val existing = npc(id = "grey-cloak-hunter", name = "Grey Cloak Hunter")
            .copy(lastLocation = "Nightbriar", lastSeenTurn = 1) // current=5, gap=4 > 3
        val parsed = ParsedReplyBuilder()
            .addNpcMet(npc(name = "Voss Ironhand", race = "Human"))
            .build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed, turn = 5, loc = "Nightbriar")

        assertEquals("Stale descriptor => no rename", 2, result.npcLog.size)
    }

    @Test
    fun `resolveNpcIdx matches an alias`() {
        val npcs = listOf(
            LogNpc(
                id = "voss-ironhand",
                name = "Voss Ironhand",
                aliases = listOf("grey-cloak-hunter", "Grey Cloak Hunter"),
                metTurn = 1,
                lastSeenTurn = 5
            )
        )
        val byOldSlug = NpcLogReducer.resolveNpcIdx("grey-cloak-hunter", npcs)
        val byOldName = NpcLogReducer.resolveNpcIdx("Grey Cloak Hunter", npcs)
        assertEquals(0, byOldSlug)
        assertEquals(0, byOldName)
    }
}
