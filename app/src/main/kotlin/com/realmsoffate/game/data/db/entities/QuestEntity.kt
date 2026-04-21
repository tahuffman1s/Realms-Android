package com.realmsoffate.game.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "quest", indices = [Index("status")])
data class QuestEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String = "side",
    val desc: String = "",
    val giver: String = "",
    val location: String = "",
    @ColumnInfo(name = "objectives_json") val objectivesJson: String = "[]",
    @ColumnInfo(name = "completed_json") val completedJson: String = "[]",
    val reward: String = "",
    val status: String = "active",
    @ColumnInfo(name = "turn_started") val turnStarted: Int = 0,
    @ColumnInfo(name = "turn_completed") val turnCompleted: Int? = null
)
