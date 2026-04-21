package com.realmsoffate.game.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "npc",
    indices = [
        Index("discovery"),
        Index("last_seen_turn"),
        Index("last_location"),
        Index("name_tokens")
    ]
)
data class NpcEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "name_tokens") val nameTokens: String,
    val race: String? = null,
    val role: String? = null,
    val age: String? = null,
    val appearance: String? = null,
    val personality: String? = null,
    val faction: String? = null,
    @ColumnInfo(name = "home_location") val homeLocation: String? = null,
    val discovery: String = "lore",
    val relationship: String? = null,
    val thoughts: String? = null,
    @ColumnInfo(name = "last_location") val lastLocation: String? = null,
    @ColumnInfo(name = "met_turn") val metTurn: Int? = null,
    @ColumnInfo(name = "last_seen_turn") val lastSeenTurn: Int? = null,
    @ColumnInfo(name = "dialogue_history") val dialogueHistoryJson: String? = null,
    @ColumnInfo(name = "memorable_quotes") val memorableQuotesJson: String? = null,
    @ColumnInfo(name = "relationship_note") val relationshipNote: String? = null,
    val status: String = "alive"
)
