package com.example.lightluxmeter.data.repository

import com.example.lightluxmeter.data.db.ExposureDao
import com.example.lightluxmeter.data.model.ExposureReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ExposureRepository(private val exposureDao: ExposureDao) {
    val allReadings: Flow<List<ExposureReading>> = exposureDao.getAllReadings()

    suspend fun saveReading(ev: Float, lux: Float, aperture: Double, shutterSpeed: String, iso: Int, note: String?) = withContext(Dispatchers.IO) {
        val sanitizedNote = note?.replace(Regex("[<>/';]"), "")?.trim()
        
        val reading = ExposureReading(
            ev = ev,
            lux = lux,
            aperture = aperture,
            shutterSpeed = shutterSpeed,
            iso = iso,
            note = sanitizedNote
        )
        exposureDao.insertReading(reading)
    }

    suspend fun deleteReading(reading: ExposureReading) = withContext(Dispatchers.IO) {
        exposureDao.deleteReading(reading)
    }
}
