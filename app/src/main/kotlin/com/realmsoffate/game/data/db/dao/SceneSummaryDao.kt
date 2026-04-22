package com.realmsoffate.game.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.realmsoffate.game.data.db.entities.SceneSummaryEntity

@Dao
interface SceneSummaryDao {
    @Query("SELECT * FROM scene_summary WHERE arc_id IS NULL ORDER BY turn_end DESC LIMIT :limit")
    suspend fun recentUnrolled(limit: Int): List<SceneSummaryEntity>

    @Query("SELECT * FROM scene_summary WHERE arc_id IS NULL ORDER BY turn_end ASC")
    suspend fun allUnrolledOldestFirst(): List<SceneSummaryEntity>

    @Query("SELECT COUNT(*) FROM scene_summary WHERE arc_id IS NULL")
    suspend fun countUnrolled(): Int

    @Query("SELECT * FROM scene_summary")
    suspend fun getAll(): List<SceneSummaryEntity>

    /** Case-insensitive substring match against summary text, scene_name, location,
     *  or key_facts_json. Newest-first so the caller can early-exit once it has enough. */
    @Query(
        "SELECT * FROM scene_summary WHERE " +
            "summary LIKE :pattern OR scene_name LIKE :pattern OR location LIKE :pattern OR key_facts_json LIKE :pattern " +
            "ORDER BY turn_end DESC LIMIT :limit"
    )
    suspend fun matchKeyword(pattern: String, limit: Int): List<SceneSummaryEntity>

    @Insert
    suspend fun insert(row: SceneSummaryEntity): Long

    @Query("UPDATE scene_summary SET arc_id = :arcId WHERE id IN (:ids)")
    suspend fun assignArcId(ids: List<Long>, arcId: Long)

    @Query("DELETE FROM scene_summary")
    suspend fun clear()
}
