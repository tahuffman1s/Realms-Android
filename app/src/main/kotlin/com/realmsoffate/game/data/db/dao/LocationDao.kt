package com.realmsoffate.game.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.realmsoffate.game.data.db.entities.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM location ORDER BY id ASC")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM location")
    suspend fun getAll(): List<LocationEntity>

    @Query("SELECT * FROM location WHERE id = :id")
    suspend fun getById(id: Int): LocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<LocationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(row: LocationEntity)

    @Query("DELETE FROM location")
    suspend fun clear()
}
