package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptUserBlocksTest {

    @Test
    fun `recent choices block returns empty for no actions`() {
        assertEquals("", renderRecentPlayerChoicesBlock(emptyList()))
    }

    @Test
    fun `recent choices block lists last 5 actions, oldest first`() {
        val actions = listOf("a1", "a2", "a3", "a4", "a5", "a6")
        val out = renderRecentPlayerChoicesBlock(actions)
        assertTrue("block should contain header", out.contains("RECENT PLAYER CHOICES"))
        // a1 is dropped; a2..a6 kept, oldest first
        val a2Pos = out.indexOf("a2")
        val a6Pos = out.indexOf("a6")
        assertTrue("a2 should appear", a2Pos >= 0)
        assertTrue("a6 should appear", a6Pos >= 0)
        assertTrue("a2 should appear before a6 (chronological)", a2Pos < a6Pos)
        assertTrue("a1 should NOT appear (cap is 5)", !out.contains("a1"))
    }

    @Test
    fun `recent choices block truncates each entry to 200 chars with ellipsis`() {
        val long = "x".repeat(300)
        val out = renderRecentPlayerChoicesBlock(listOf(long))
        // Should contain a 200-char x-run + an ellipsis marker.
        assertTrue(out.contains("x".repeat(200)))
        assertTrue(out.contains("…") || out.contains("..."))
    }

    @Test
    fun `recent story block returns empty for no narrations`() {
        assertEquals("", renderRecentStoryBlock(emptyList()))
    }

    @Test
    fun `recent story block uses 4-message 600-char window`() {
        // 5 messages each padded to 700 chars, distinct first-2-char prefix per message.
        val msgs = listOf(
            "m1" + "a".repeat(698),
            "m2" + "b".repeat(698),
            "m3" + "c".repeat(698),
            "m4" + "d".repeat(698),
            "m5" + "e".repeat(698)
        )
        val out = renderRecentStoryBlock(msgs)
        // Last 4: m2..m5
        assertTrue("m1 should be dropped", !out.contains("m1"))
        assertTrue("m2 should appear", out.contains("m2"))
        assertTrue("m5 should appear", out.contains("m5"))
        // Each truncated to 600 chars — so the 'b' run should not have all 698 chars.
        // After 'm2' prefix + 598 b's = 600 total, so 'b' should appear 598 times max for m2.
        val bRun = "b".repeat(599)
        assertTrue("m2 should be truncated to 600 chars (599 b's max)", !out.contains(bRun))
    }
}
