package com.realmsoffate.game.data

import kotlinx.coroutines.flow.Flow

interface EntityRepository {
    // Observable reads for UI
    fun observeLoggedNpcs(): Flow<List<LogNpc>>
    fun observeLoreNpcs(): Flow<List<LoreNpc>>
    fun observeActiveQuests(): Flow<List<Quest>>
    fun observeFactions(): Flow<List<Faction>>
    fun observeLocations(): Flow<List<MapLocation>>

    // On-demand reads
    suspend fun snapshotForReducers(): EntitySnapshot
    suspend fun sceneRelevantNpcs(location: String, currentTurn: Int, withinTurns: Int = 10): List<LogNpc>
    suspend fun keywordMatchedEntities(tokens: List<String>, limit: Int = 15): KeywordHits

    // Writes
    suspend fun applyChanges(changes: EntityChanges)
    suspend fun clear()

    // Summaries
    suspend fun appendSceneSummary(s: SceneSummary): Long
    suspend fun recentSceneSummaries(limit: Int = 20): List<SceneSummary>
    suspend fun countUnrolledScenes(): Int
    suspend fun allArcSummaries(): List<ArcSummary>
    suspend fun rollupScenes(sceneIds: List<Long>, arc: ArcSummary)
}
