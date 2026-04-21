package com.realmsoffate.game.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.realmsoffate.game.data.db.entities.NpcEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NpcDao {
    @Query("SELECT * FROM npc WHERE discovery != 'lore' ORDER BY last_seen_turn DESC")
    fun observeLogged(): Flow<List<NpcEntity>>

    @Query("SELECT * FROM npc WHERE discovery = 'lore' ORDER BY name ASC")
    fun observeLore(): Flow<List<NpcEntity>>

    @Query("SELECT * FROM npc")
    suspend fun getAll(): List<NpcEntity>

    @Query("SELECT * FROM npc WHERE discovery != 'lore'")
    suspend fun getAllLogged(): List<NpcEntity>

    @Query("SELECT * FROM npc WHERE id = :id")
    suspend fun getById(id: String): NpcEntity?

    @Query(
        """
        SELECT * FROM npc
        WHERE discovery != 'lore'
          AND (
            last_location = :loc
            OR last_seen_turn >= :minTurn
          )
        ORDER BY last_seen_turn DESC
        """
    )
    suspend fun sceneRelevant(loc: String, minTurn: Int): List<NpcEntity>

    @Query("SELECT * FROM npc WHERE name_tokens LIKE :pattern LIMIT :limit")
    suspend fun matchKeyword(pattern: String, limit: Int): List<NpcEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<NpcEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(row: NpcEntity)

    @Update
    suspend fun update(row: NpcEntity)

    @Query("DELETE FROM npc")
    suspend fun clear()
}
