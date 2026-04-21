package com.realmsoffate.game.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scene_summary",
    indices = [Index("turn_end"), Index("arc_id")]
)
data class SceneSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "turn_start") val turnStart: Int,
    @ColumnInfo(name = "turn_end") val turnEnd: Int,
    val location: String = "",
    val summary: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "arc_id") val arcId: Long? = null
)
