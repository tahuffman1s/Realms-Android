package com.realmsoffate.game.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "faction")
data class FactionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String = "",
    val description: String = "",
    @ColumnInfo(name = "base_loc") val baseLoc: String = "",
    val color: String? = null,
    @ColumnInfo(name = "government_json") val governmentJson: String? = null,
    @ColumnInfo(name = "economy_json") val economyJson: String? = null,
    val population: String = "",
    val mood: String = "",
    val disposition: String = "",
    val goal: String = "",
    val ruler: String = "",
    val status: String = "active"
)
