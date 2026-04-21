package com.realmsoffate.game.game

import com.realmsoffate.game.data.SceneSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcSummarizerTest {
    @Test
    fun `produces ArcSummary spanning turn range from scene list`() = runTest {
        val summarizer = ArcSummarizer { _ -> """{"summary":"arc text"}""" }
        val scenes = listOf(
            SceneSummary(id = 1, turnStart = 1, turnEnd = 5, sceneName = "a", locationName = "L", summary = "a"),
            SceneSummary(id = 2, turnStart = 6, turnEnd = 10, sceneName = "b", locationName = "L", summary = "b")
        )
        val arc = summarizer.run(scenes)
        assertEquals(1, arc.turnStart)
        assertEquals(10, arc.turnEnd)
        assertEquals("arc text", arc.summary)
    }

    @Test
    fun `falls back to raw text on JSON parse failure`() = runTest {
        val summarizer = ArcSummarizer { _ -> "not json" }
        val scenes = listOf(
            SceneSummary(id = 1, turnStart = 1, turnEnd = 5, sceneName = "a", locationName = "L", summary = "a")
        )
        val arc = summarizer.run(scenes)
        assertTrue(arc.summary.isNotEmpty())
    }
}
