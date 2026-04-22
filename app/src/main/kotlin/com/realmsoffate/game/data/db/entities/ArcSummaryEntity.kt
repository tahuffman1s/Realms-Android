package com.realmsoffate.game.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "arc_summary", indices = [Index("turn_end")])
data class ArcSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "turn_start") val turnStart: Int,
    @ColumnInfo(name = "turn_end") val turnEnd: Int,
    val summary: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
