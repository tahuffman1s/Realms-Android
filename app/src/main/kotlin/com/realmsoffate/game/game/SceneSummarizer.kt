package com.realmsoffate.game.game

import com.realmsoffate.game.data.AiRepository
import com.realmsoffate.game.data.ChatMsg
import com.realmsoffate.game.data.SceneSummary

/**
 * Orchestrates one scene-summary call and returns the updated list. A thin
 * shim over [AiRepository.summarizeScene] that's easy to unit-test — the
 * primary constructor accepts a callable `summarize` lambda so tests can
 * inject a deterministic stub instead of hitting the network.
 *
 * Failure policy: missing summaries are preferred over crashing. If the AI
 * call returns null, the prior list is returned unchanged and the caller
 * can simply try again at the next scene boundary.
 */
class SceneSummarizer(
    private val summarize: suspend (
        apiKey: String,
        scene: String,
        location: String,
        history: List<ChatMsg>
    ) -> Pair<String, List<String>>?
) {
    constructor(ai: AiRepository) : this({ apiKey, scene, loc, hist ->
        ai.summarizeScene(apiKey, scene, loc, hist)
    })

    suspend fun summarizeIfPossible(
        apiKey: String,
        priorSummaries: List<SceneSummary>,
        scene: String,
        location: String,
        history: List<ChatMsg>,
        turnStart: Int,
        turnEnd: Int
    ): List<SceneSummary> {
        if (history.isEmpty()) return priorSummaries
        val result = summarize(apiKey, scene, location, history) ?: return priorSummaries
        return priorSummaries + SceneSummary(
            turnStart = turnStart,
            turnEnd = turnEnd,
            sceneName = scene,
            locationName = location,
            summary = result.first,
            keyFacts = result.second
        )
    }
}
