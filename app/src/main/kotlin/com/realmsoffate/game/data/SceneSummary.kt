package com.realmsoffate.game.data

import kotlinx.serialization.Serializable

/**
 * Compressed record of a finished scene. Emitted by the summarizer when a
 * scene boundary is crossed (location change, combat transition, explicit
 * [SCENE:] tag change).
 *
 * turnStart/turnEnd are inclusive and reference `GameUiState.turns` values.
 * summary is the narrative compression (~200 tokens). keyFacts is an optional
 * bullet list the summarizer may emit for high-precision facts that must
 * survive (NPC promises, item handoffs, death confirmations).
 */
@Serializable
data class SceneSummary(
    val turnStart: Int,
    val turnEnd: Int,
    val sceneName: String,
    val locationName: String,
    val summary: String,
    val keyFacts: List<String> = emptyList()
)
