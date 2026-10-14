package com.hackqing.scale.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Query("SELECT * FROM measurement ORDER BY timeMillis DESC")
    fun stream(): Flow<List<ScaleMeasurementEntity>>

    @Query("SELECT * FROM measurement ORDER BY timeMillis DESC")
    fun all(): List<ScaleMeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(items: List<ScaleMeasurementEntity>)

    @Query("DELETE FROM measurement")
    fun clear()

    @Query("DELETE FROM measurement WHERE id = :id")
    fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM measurement")
    fun count(): Int
}
