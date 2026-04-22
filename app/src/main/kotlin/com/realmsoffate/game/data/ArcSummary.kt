package com.realmsoffate.game.data

import kotlinx.serialization.Serializable

@Serializable
data class ArcSummary(
    val id: Long = 0,
    val turnStart: Int,
    val turnEnd: Int,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
)
