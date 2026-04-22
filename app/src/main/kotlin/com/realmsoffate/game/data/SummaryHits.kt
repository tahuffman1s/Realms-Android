package com.realmsoffate.game.data

/** Bundle of narrative-summary records matched by keyword for prompt injection. */
data class SummaryHits(
    val scenes: List<SceneSummary>,
    val arcs: List<ArcSummary>
) {
    val isEmpty: Boolean get() = scenes.isEmpty() && arcs.isEmpty()

    companion object {
        val EMPTY = SummaryHits(emptyList(), emptyList())
    }
}
