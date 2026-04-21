package com.realmsoffate.game.game

import com.realmsoffate.game.data.ArcSummary
import com.realmsoffate.game.data.SceneSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Compresses a batch of [SceneSummary] into a single [ArcSummary] via an
 * injectable AI call. The `summarize` lambda gets pre-formatted scene inputs
 * and returns the raw model response; the class unwraps the expected
 * `{"summary":"..."}` JSON envelope and falls back to the raw text if the
 * envelope is malformed.
 */
class ArcSummarizer(
    private val summarize: suspend (inputs: List<String>) -> String
) {
    @Serializable
    private data class Envelope(val summary: String = "")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun run(scenes: List<SceneSummary>): ArcSummary {
        require(scenes.isNotEmpty()) { "Cannot roll up zero scenes" }
        val inputs = scenes.map { "T${it.turnStart}-${it.turnEnd}: ${it.summary}" }
        val raw = summarize(inputs)
        val summary = runCatching {
            json.decodeFromString<Envelope>(raw).summary.ifBlank { raw }
        }.getOrElse { raw }.trim()
        return ArcSummary(
            turnStart = scenes.minOf { it.turnStart },
            turnEnd = scenes.maxOf { it.turnEnd },
            summary = summary
        )
    }
}
