package com.realmsoffate.game.util

import com.realmsoffate.game.data.ChatMsg
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenEstimateTest {
    @Test
    fun `estimateTokens returns zero for empty string`() {
        assertEquals(0, TokenEstimate.ofText(""))
    }

    @Test
    fun `estimateTokens uses char div 4 heuristic`() {
        // 16 chars → 4 tokens
        assertEquals(4, TokenEstimate.ofText("abcdefghijklmnop"))
    }

    @Test
    fun `estimateTokens rounds up`() {
        // 5 chars → 2 tokens (5/4 = 1.25, ceil = 2)
        assertEquals(2, TokenEstimate.ofText("hello"))
    }

    @Test
    fun `estimateChatMsg includes role overhead`() {
        val msg = ChatMsg(role = "user", content = "test")
        // 4/4 + 4 overhead = 5
        assertEquals(5, TokenEstimate.ofMessage(msg))
    }

    @Test
    fun `sumMessages totals individual estimates`() {
        val msgs = listOf(
            ChatMsg(role = "user", content = "abcd"),     // 1 + 4 = 5
            ChatMsg(role = "assistant", content = "efgh")  // 1 + 4 = 5
        )
        assertEquals(10, TokenEstimate.sumMessages(msgs))
    }
}
