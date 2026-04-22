package com.realmsoffate.game.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location")
data class LocationEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val type: String,
    val icon: String,
    val x: Int,
    val y: Int,
    val discovered: Int = 0
)
