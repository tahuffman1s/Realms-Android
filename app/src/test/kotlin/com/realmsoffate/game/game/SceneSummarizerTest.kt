package com.realmsoffate.game.game

import com.realmsoffate.game.data.ChatMsg
import com.realmsoffate.game.data.SceneSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SceneSummarizerTest {

    private fun msg(role: String, c: String) = ChatMsg(role, c)

    @Test
    fun `skips when history slice is empty`() = runTest {
        val summarizer = SceneSummarizer { _, _, _, _ -> "ignored" to listOf("x") }
        val existing = listOf<SceneSummary>()
        val result = summarizer.summarizeIfPossible(
            apiKey = "k",
            priorSummaries = existing,
            scene = "a", location = "L",
            history = emptyList(),
            turnStart = 1, turnEnd = 1
        )
        assertSame(existing, result)
    }

    @Test
    fun `appends summary when AI returns one`() = runTest {
        val summarizer = SceneSummarizer { _, _, _, _ -> "A thing happened." to listOf("fact1") }
        val prior = listOf<SceneSummary>()
        val result = summarizer.summarizeIfPossible(
            apiKey = "k",
            priorSummaries = prior,
            scene = "ashford-tavern", location = "Ashford",
            history = listOf(msg("user", "I enter"), msg("assistant", "You see Mira")),
            turnStart = 5, turnEnd = 8
        )
        assertEquals(1, result.size)
        val s = result[0]
        assertEquals("A thing happened.", s.summary)
        assertEquals(listOf("fact1"), s.keyFacts)
        assertEquals(5, s.turnStart)
        assertEquals(8, s.turnEnd)
        assertEquals("ashford-tavern", s.sceneName)
        assertEquals("Ashford", s.locationName)
    }

    @Test
    fun `returns prior list unchanged on AI failure`() = runTest {
        val summarizer = SceneSummarizer { _, _, _, _ -> null }
        val prior = listOf(
            SceneSummary(1, 2, "s", "L", "old", emptyList())
        )
        val result = summarizer.summarizeIfPossible(
            apiKey = "k",
            priorSummaries = prior,
            scene = "s2", location = "L2",
            history = listOf(msg("user", "hi")),
            turnStart = 3, turnEnd = 3
        )
        assertSame(prior, result)
    }

    @Test
    fun `appends to existing summaries in order`() = runTest {
        val summarizer = SceneSummarizer { _, _, _, _ -> "new" to emptyList() }
        val prior = listOf(SceneSummary(1, 2, "s1", "L1", "first"))
        val result = summarizer.summarizeIfPossible(
            apiKey = "k",
            priorSummaries = prior,
            scene = "s2", location = "L2",
            history = listOf(msg("user", "x")),
            turnStart = 3, turnEnd = 4
        )
        assertEquals(2, result.size)
        assertEquals("first", result[0].summary)
        assertEquals("new", result[1].summary)
    }
}
