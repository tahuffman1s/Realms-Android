package com.realmsoffate.game.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.realmsoffate.game.data.db.entities.ArcSummaryEntity

@Dao
interface ArcSummaryDao {
    @Query("SELECT * FROM arc_summary ORDER BY turn_end DESC")
    suspend fun allNewestFirst(): List<ArcSummaryEntity>

    /** Case-insensitive substring match against the arc summary text. Newest-first. */
    @Query(
        "SELECT * FROM arc_summary WHERE summary LIKE :pattern " +
            "ORDER BY turn_end DESC LIMIT :limit"
    )
    suspend fun matchKeyword(pattern: String, limit: Int): List<ArcSummaryEntity>

    @Insert
    suspend fun insert(row: ArcSummaryEntity): Long

    @Query("DELETE FROM arc_summary")
    suspend fun clear()
}
