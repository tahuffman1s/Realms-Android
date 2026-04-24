package com.realmsoffate.game.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SceneSummary(
    val turnStart: Int,
    val turnEnd: Int,
    val sceneName: String,
    val locationName: String,
    val summary: String,
    val keyFacts: List<String> = emptyList(),
    val id: Long = 0,
    // Always encode; default calls System.currentTimeMillis() so a missing
    // field would round-trip to a new timestamp and break equality.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val createdAt: Long = System.currentTimeMillis()
)
