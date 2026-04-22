package com.realmsoffate.game.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.realmsoffate.game.data.db.entities.QuestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestDao {
    @Query("SELECT * FROM quest WHERE status = 'active' ORDER BY turn_started DESC")
    fun observeActive(): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quest")
    suspend fun getAll(): List<QuestEntity>

    @Query("SELECT * FROM quest WHERE id = :id")
    suspend fun getById(id: String): QuestEntity?

    @Query("SELECT * FROM quest WHERE status = 'active'")
    suspend fun getActive(): List<QuestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<QuestEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(row: QuestEntity)

    @Query("DELETE FROM quest")
    suspend fun clear()
}
