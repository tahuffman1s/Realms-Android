package com.realmsoffate.game.data

import com.realmsoffate.game.util.TokenEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRepositoryWindowTest {

    private fun msg(role: String, len: Int) =
        ChatMsg(role = role, content = "x".repeat(len))

    @Test
    fun `windowByTokenBudget returns all when under budget`() {
        val history = listOf(
            msg("user", 40),       // 10 + 4 = 14
            msg("assistant", 40),  // 14
            msg("user", 40)        // 14
        )
        val windowed = AiRepository.windowByTokenBudget(history, budget = 1000)
        assertEquals(3, windowed.size)
        assertEquals(history, windowed)
    }

    @Test
    fun `windowByTokenBudget trims oldest when over budget`() {
        val history = List(100) { i -> msg(if (i % 2 == 0) "user" else "assistant", 400) }
        val windowed = AiRepository.windowByTokenBudget(history, budget = 1000)
        assertTrue("Should keep fewer than input", windowed.size < history.size)
        assertTrue("Should respect budget", TokenEstimate.sumMessages(windowed) <= 1000)
        assertTrue("Should keep tail (most recent)", windowed.last() == history.last())
    }

    @Test
    fun `windowByTokenBudget always preserves final user message even if oversized`() {
        val big = msg("user", 10_000)
        val history = listOf(
            msg("user", 10),
            msg("assistant", 10),
            big
        )
        val windowed = AiRepository.windowByTokenBudget(history, budget = 500)
        assertEquals(1, windowed.size)
        assertEquals(big, windowed[0])
    }

    @Test
    fun `windowByTokenBudget returns empty for empty input`() {
        assertEquals(emptyList<ChatMsg>(), AiRepository.windowByTokenBudget(emptyList(), budget = 1000))
    }
}
