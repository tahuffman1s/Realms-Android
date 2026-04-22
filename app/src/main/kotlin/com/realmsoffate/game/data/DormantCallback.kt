package com.realmsoffate.game.data

/**
 * Occasional resurrection of a long-dormant arc summary as an OPTIONAL
 * callback for the narrator. Keeps slow-burning threads from being silently
 * abandoned when the player never mentions them directly.
 *
 * Selection is simple: pick the arc with the largest gap between its
 * [ArcSummary.turnEnd] and the current turn, provided the gap is at least
 * [dormantAfter] and the arc is not already surfaced via keyword retrieval
 * (pass those ids in [excludeIds]).
 */
object DormantCallback {
    fun pick(
        arcs: List<ArcSummary>,
        currentTurn: Int,
        dormantAfter: Int,
        excludeIds: Set<Long>
    ): ArcSummary? =
        arcs
            .asSequence()
            .filter { it.id !in excludeIds }
            .map { it to (currentTurn - it.turnEnd) }
            .filter { it.second >= dormantAfter }
            .maxByOrNull { it.second }
            ?.first

    /** Wraps [arc] as an optional-callback hint. Empty string when [arc] is null
     *  so callers can concat unconditionally. */
    fun renderBlock(arc: ArcSummary?): String {
        arc ?: return ""
        return "\n# OPTIONAL CALLBACK (weave only if organic — skip if it doesn't fit)\n- ${arc.summary}\n"
    }
}
