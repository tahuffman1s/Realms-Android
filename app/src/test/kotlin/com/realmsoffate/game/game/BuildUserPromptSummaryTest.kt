package com.realmsoffate.game.game

import com.realmsoffate.game.data.SceneSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildUserPromptSummaryTest {
    @Test
    fun `renderSceneSummariesBlock includes header and facts`() {
        val block = renderSceneSummariesBlock(listOf(
            SceneSummary(1, 4, "ashford-tavern", "Ashford", "Met Mira.", listOf("Owes 5g"))
        ))
        assertTrue(block.contains("STORY SO FAR"))
        assertTrue(block.contains("Mira"))
        assertTrue(block.contains("Owes 5g"))
    }

    @Test
    fun `renderSceneSummariesBlock returns empty string when list empty`() {
        assertTrue(renderSceneSummariesBlock(emptyList()).isEmpty())
    }

    @Test
    fun `renderSceneSummariesBlock respects token budget keeping newest`() {
        val many = (1..50).map {
            SceneSummary(it, it, "s$it", "L$it", "Long summary text ".repeat(20), emptyList())
        }
        val block = renderSceneSummariesBlock(many, tokenBudget = 200)
        assertTrue("Should contain newest", block.contains("[T50-50"))
        assertFalse("Should drop oldest under tight budget", block.contains("[T1-1 @ L1]"))
    }
}
