package com.example.lightluxmeter.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exposure_readings")
data class ExposureReading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ev: Float,
    val lux: Float,
    val aperture: Double,
    val shutterSpeed: String,
    val iso: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null
)
