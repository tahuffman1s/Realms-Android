package com.realmsoffate.game.data

/**
 * Narrow contradiction detector for arc summaries. On every arc rollup, the
 * caller invokes [checkArc] with the current canonical NPC list and the new
 * arc summary. Any NPC whose status is in [FLAGGED_STATUSES] (dead / cursed
 * / doomed) and whose name appears in the summary WITHOUT a clear past-tense
 * cue is recorded in a bounded in-memory queue.
 *
 * This is intentionally conservative: we want to surface the most common
 * "dead NPC speaks" hallucinations without drowning in false positives from
 * legitimate flashback or possessive mentions ("Orin's keep", "Orin's
 * memory", etc.).
 */
object ContradictionQueue {
    val FLAGGED_STATUSES = setOf("dead", "cursed", "doomed")
    private const val MAX_ENTRIES = 200
    // Past-tense / flashback cues near the NPC name; treat as NON-contradictory.
    private val PAST_CUES = listOf(
        "'s memory", "'s legacy", "'s spirit", "'s ghost",
        "remembered", "remembering", "in memory of",
        "mourners", "mourning", "mourned",
        "had once", "once had", "long ago",
        "the late", "slain", "fallen"
    )
    private val _log = ArrayDeque<String>()
    private val lock = Any()

    /**
     * Returns a list of newly-flagged issue strings (also pushed into the
     * queue). Returns an empty list when there's nothing to flag.
     */
    fun checkArc(canonicalNpcs: List<LogNpc>, arc: ArcSummary): List<String> {
        val summary = arc.summary
        if (summary.isBlank() || canonicalNpcs.isEmpty()) return emptyList()
        val summaryLower = summary.lowercase()
        val flagged = mutableListOf<String>()
        for (n in canonicalNpcs) {
            if (n.status.lowercase() !in FLAGGED_STATUSES) continue
            val nameLower = n.name.lowercase()
            if (nameLower.isBlank() || !summaryLower.contains(nameLower)) continue
            // Skip when any past-tense cue appears in the summary. Coarse but
            // much less noisy than false-positive-flagging every mention.
            if (PAST_CUES.any { summaryLower.contains(it) }) continue
            flagged += "arc ${arc.id} (T${arc.turnStart}-${arc.turnEnd}): '${n.name}' marked ${n.status} but referenced as active: \"${snippet(summary, n.name)}\""
        }
        if (flagged.isNotEmpty()) {
            synchronized(lock) {
                flagged.forEach { _log.addLast(it) }
                while (_log.size > MAX_ENTRIES) _log.removeFirst()
            }
        }
        return flagged
    }

    fun snapshot(): List<String> = synchronized(lock) { _log.toList() }

    @androidx.annotation.VisibleForTesting
    internal fun clearForTest() = synchronized(lock) { _log.clear() }

    private fun snippet(text: String, name: String, window: Int = 60): String {
        val idx = text.indexOf(name, ignoreCase = true)
        if (idx < 0) return text.take(window * 2)
        val start = (idx - window).coerceAtLeast(0)
        val end = (idx + name.length + window).coerceAtMost(text.length)
        return text.substring(start, end)
    }
}
