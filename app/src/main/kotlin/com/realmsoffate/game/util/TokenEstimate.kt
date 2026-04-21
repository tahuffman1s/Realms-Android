package com.realmsoffate.game.util

import com.realmsoffate.game.data.ChatMsg

/**
 * Rough token counter for DeepSeek / OpenAI-style tokenizers.
 *
 * Uses the char/4 heuristic — a well-known approximation that over-estimates
 * short English strings slightly and under-estimates dense JSON. Good enough
 * for windowing decisions; do not use for billing.
 *
 * Each ChatMsg carries ~4 tokens of framing overhead (role label + separators)
 * in the OpenAI chat format; we bake that into `ofMessage`.
 */
object TokenEstimate {
    private const val MSG_FRAMING_OVERHEAD = 4

    fun ofText(s: String): Int = if (s.isEmpty()) 0 else (s.length + 3) / 4

    fun ofMessage(m: ChatMsg): Int = ofText(m.content) + MSG_FRAMING_OVERHEAD

    fun sumMessages(msgs: List<ChatMsg>): Int = msgs.sumOf { ofMessage(it) }
}
