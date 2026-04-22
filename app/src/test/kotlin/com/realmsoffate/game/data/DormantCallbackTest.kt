package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DormantCallbackTest {
    private fun arc(id: Long, turnEnd: Int, summary: String = "arc_$id") =
        ArcSummary(id = id, turnStart = turnEnd - 5, turnEnd = turnEnd, summary = summary)

    @Test
    fun `pick returns the most dormant arc past the threshold`() {
        val arcs = listOf(
            arc(1L, turnEnd = 10),   // gap: 110
            arc(2L, turnEnd = 90),   // gap: 30 — below threshold
            arc(3L, turnEnd = 50)    // gap: 70
        )
        val pick = DormantCallback.pick(arcs, currentTurn = 120, dormantAfter = 50, excludeIds = emptySet())
        assertEquals(1L, pick?.id)
    }

    @Test
    fun `pick returns null when nothing is past the threshold`() {
        val arcs = listOf(arc(1L, turnEnd = 100), arc(2L, turnEnd = 95))
        assertNull(DormantCallback.pick(arcs, currentTurn = 105, dormantAfter = 50, excludeIds = emptySet()))
    }

    @Test
    fun `pick respects excludeIds so we don't double-surface arcs already matched`() {
        val arcs = listOf(arc(1L, turnEnd = 10), arc(2L, turnEnd = 20))
        // arc 1 is most dormant but excluded; arc 2 still past threshold
        val pick = DormantCallback.pick(arcs, currentTurn = 120, dormantAfter = 50, excludeIds = setOf(1L))
        assertEquals(2L, pick?.id)
    }

    @Test
    fun `pick returns null on empty arc list`() {
        assertNull(DormantCallback.pick(emptyList(), currentTurn = 100, dormantAfter = 10, excludeIds = emptySet()))
    }

    @Test
    fun `renderBlock emits an OPTIONAL CALLBACK section when arc is non-null`() {
        val a = arc(1L, turnEnd = 10, summary = "The Shadow Pact haunts you.")
        val block = DormantCallback.renderBlock(a)
        assertTrue(block.contains("OPTIONAL CALLBACK"))
        assertTrue(block.contains("weave only if organic"))
        assertTrue(block.contains("The Shadow Pact haunts you."))
    }

    @Test
    fun `renderBlock returns empty string for null`() {
        assertEquals("", DormantCallback.renderBlock(null))
    }
}
