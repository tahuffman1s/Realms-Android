package com.realmsoffate.game.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class ContradictionQueueTest {
    @Before fun reset() { ContradictionQueue.clearForTest() }
    @After fun cleanup() { ContradictionQueue.clearForTest() }

    private fun npc(name: String, status: String = "alive") =
        LogNpc(
            id = name.lowercase(),
            name = name,
            race = "human",
            role = "npc",
            status = status,
            relationship = "neutral",
            lastLocation = "",
            metTurn = 0,
            lastSeenTurn = 0
        )

    private fun arc(text: String) = ArcSummary(
        id = 1L, turnStart = 1, turnEnd = 10, summary = text
    )

    @Test
    fun `checkArc logs conflict when dead NPC is mentioned in new arc`() {
        val npcs = listOf(npc("Thane Orin", status = "dead"))
        val flagged = ContradictionQueue.checkArc(npcs, arc("Thane Orin rallied the guards and led the charge."))
        assertEquals(1, flagged.size)
        assertTrue(flagged[0].contains("Thane Orin"))
        assertTrue(flagged[0].contains("dead"))
        assertEquals(1, ContradictionQueue.snapshot().size)
    }

    @Test
    fun `checkArc does not flag living NPCs`() {
        val npcs = listOf(npc("Mara", status = "alive"))
        val flagged = ContradictionQueue.checkArc(npcs, arc("Mara drew her blade."))
        assertTrue(flagged.isEmpty())
        assertTrue(ContradictionQueue.snapshot().isEmpty())
    }

    @Test
    fun `checkArc also flags cursed and doomed statuses`() {
        val npcs = listOf(npc("Kai", status = "cursed"), npc("Lena", status = "doomed"))
        val flagged = ContradictionQueue.checkArc(
            npcs,
            arc("Kai laughed while Lena told a joke.")
        )
        assertEquals(2, flagged.size)
    }

    @Test
    fun `checkArc does not flag when dead NPC is clearly referenced as past`() {
        val npcs = listOf(npc("Orin", status = "dead"))
        val flagged = ContradictionQueue.checkArc(
            npcs,
            arc("Orin's memory hung over the keep. The mourners lit candles.")
        )
        // Heuristic is deliberately simple — "memory", "mourners", possessive
        // ("Orin's") signal a past reference. We should NOT flag this.
        assertTrue("expected empty, got $flagged", flagged.isEmpty())
    }

    @Test
    fun `snapshot is bounded to max size`() {
        val npcs = listOf(npc("X", status = "dead"))
        repeat(250) { ContradictionQueue.checkArc(npcs, arc("X acts ($it).")) }
        assertTrue(ContradictionQueue.snapshot().size <= ContradictionQueue.MAX_ENTRIES)
    }

    @Test
    fun `clearForTest resets the queue`() {
        val npcs = listOf(npc("Orin", status = "dead"))
        ContradictionQueue.checkArc(npcs, arc("Orin speaks."))
        assertFalse(ContradictionQueue.snapshot().isEmpty())
        ContradictionQueue.clearForTest()
        assertTrue(ContradictionQueue.snapshot().isEmpty())
    }
}
