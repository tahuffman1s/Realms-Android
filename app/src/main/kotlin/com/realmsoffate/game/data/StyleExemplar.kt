package com.realmsoffate.game.data

/**
 * Renders a short "house voice" exemplar from existing narrative text. The
 * sample is pinned to the cached system prompt so the model has a stable
 * tone/cadence anchor across a long game. Source should be the earliest
 * scene summary — it does not change after it's generated, which keeps the
 * cache prefix stable.
 */
object StyleExemplar {
    /** Keep at most the first 3 sentence-terminated sentences from [sample]. */
    fun render(sample: String): String {
        val sentences = sample.split(Regex("(?<=[.!?])\\s+"))
            .filter { it.isNotBlank() }
        return sentences.take(3).joinToString(" ").trim()
    }

    /** Wraps [sample] in a cache-friendly STYLE block. Returns "" if [sample]
     *  is null or blank so callers can unconditionally concat without a guard. */
    fun block(sample: String?): String {
        val picked = sample?.takeIf { it.isNotBlank() } ?: return ""
        val trimmed = render(picked)
        if (trimmed.isBlank()) return ""
        return "\n# STYLE (match this voice — tone, cadence, word choice)\n\"$trimmed\"\n"
    }
}
