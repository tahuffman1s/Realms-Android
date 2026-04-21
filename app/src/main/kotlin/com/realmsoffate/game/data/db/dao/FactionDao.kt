package com.realmsoffate.game.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.realmsoffate.game.data.db.entities.FactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FactionDao {
    @Query("SELECT * FROM faction ORDER BY name ASC")
    fun observeAll(): Flow<List<FactionEntity>>

    @Query("SELECT * FROM faction")
    suspend fun getAll(): List<FactionEntity>

    @Query("SELECT * FROM faction WHERE id = :id")
    suspend fun getById(id: String): FactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<FactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(row: FactionEntity)

    @Query("DELETE FROM faction")
    suspend fun clear()
}
