package com.realmsoffate.game.game

import com.realmsoffate.game.data.AiRepository
import com.realmsoffate.game.data.ChatMsg
import com.realmsoffate.game.data.EntityRepository
import com.realmsoffate.game.data.ROLLUP_BATCH_SIZE
import com.realmsoffate.game.data.ROLLUP_THRESHOLD
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
 *
 * Phase 2 adds [persistAndMaybeRollup]: when a repo is available, newly
 * produced summaries are also inserted into Room and batched into an arc
 * summary once [ROLLUP_THRESHOLD] unrolled scenes accumulate.
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

    /**
     * Persists [summary] into [repo] and triggers [ArcSummarizer] rollup when
     * the number of unrolled scene summaries reaches [ROLLUP_THRESHOLD]. Pass a
     * null [arcSummarizer] to disable rollup (tests, or when the API key is
     * missing).
     */
    suspend fun persistAndMaybeRollup(
        repo: EntityRepository,
        summary: SceneSummary,
        arcSummarizer: ArcSummarizer?
    ) {
        repo.appendSceneSummary(summary)
        maybeRollupArcs(repo, arcSummarizer)
    }

    internal suspend fun maybeRollupArcs(repo: EntityRepository, arcSummarizer: ArcSummarizer?) {
        val arc = arcSummarizer ?: return
        if (repo.countUnrolledScenes() < ROLLUP_THRESHOLD) return
        val batch = repo.recentSceneSummaries(limit = Int.MAX_VALUE)
            .sortedBy { it.turnEnd }
            .take(ROLLUP_BATCH_SIZE)
        if (batch.size < ROLLUP_BATCH_SIZE) return
        val arcSummary = arc.run(batch)
        repo.rollupScenes(batch.map { it.id }, arcSummary)
    }
}
