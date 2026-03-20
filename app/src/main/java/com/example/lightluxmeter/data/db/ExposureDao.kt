package com.example.lightluxmeter.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lightluxmeter.data.model.ExposureReading
import kotlinx.coroutines.flow.Flow
import kotlin.jvm.JvmSuppressWildcards

@Dao
interface ExposureDao {
    @Query("SELECT * FROM exposure_readings ORDER BY timestamp DESC")
    fun getAllReadings(): Flow<List<ExposureReading>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: ExposureReading): @JvmSuppressWildcards Long

    @Delete
    suspend fun deleteReading(reading: ExposureReading): @JvmSuppressWildcards Int
}
