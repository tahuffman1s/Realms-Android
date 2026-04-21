package com.realmsoffate.game.game

import com.realmsoffate.game.data.LogNpc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcRelevanceFilterTest {

    private fun npc(name: String, lastSeen: Int, lastLoc: String = "") =
        LogNpc(id = name.lowercase(), name = name, metTurn = 0, lastSeenTurn = lastSeen, lastLocation = lastLoc)

    @Test
    fun `includes NPC seen within recency window`() {
        val all = listOf(npc("Mira", lastSeen = 45))
        val r = filterRelevantNpcs(all, currentTurn = 50, currentLocation = "Ashford")
        assertEquals(1, r.size)
    }

    @Test
    fun `includes NPC in current location even if old`() {
        val all = listOf(npc("Garrick", lastSeen = 1, lastLoc = "Ashford"))
        val r = filterRelevantNpcs(all, currentTurn = 80, currentLocation = "Ashford")
        assertEquals(1, r.size)
    }

    @Test
    fun `excludes NPC neither recent nor co-located`() {
        val all = listOf(npc("Oldfriend", lastSeen = 1, lastLoc = "Faraway"))
        val r = filterRelevantNpcs(all, currentTurn = 80, currentLocation = "Ashford")
        assertTrue(r.isEmpty())
    }

    @Test
    fun `caps at 30 preferring most recent`() {
        val all = (1..100).map { npc("n$it", lastSeen = it) }
        val r = filterRelevantNpcs(all, currentTurn = 200, currentLocation = "")
        val allClose = (1..100).map { npc("n$it", lastSeen = 200 - it, lastLoc = "") }
        val r2 = filterRelevantNpcs(allClose, currentTurn = 200, currentLocation = "")
        assertEquals(10, r2.size)
    }

    @Test
    fun `cap enforced when more than 30 relevant`() {
        val many = (1..50).map { npc("n$it", lastSeen = 199, lastLoc = "Ashford") }
        val r = filterRelevantNpcs(many, currentTurn = 200, currentLocation = "Ashford")
        assertEquals(30, r.size)
    }

    @Test
    fun `sorted by lastSeenTurn descending within cap`() {
        val all = listOf(
            npc("Old", lastSeen = 192),
            npc("Newest", lastSeen = 199),
            npc("Mid", lastSeen = 195)
        )
        val r = filterRelevantNpcs(all, currentTurn = 200, currentLocation = "")
        assertEquals("Newest", r[0].name)
        assertEquals("Mid", r[1].name)
        assertEquals("Old", r[2].name)
    }
}
