package com.example.lightluxmeter.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lightluxmeter.data.db.AppDatabase
import com.example.lightluxmeter.data.model.ExposureReading
import com.example.lightluxmeter.data.repository.ExposureRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExposureViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ExposureRepository
    val allReadings: StateFlow<List<ExposureReading>>

    init {
        val exposureDao = AppDatabase.getDatabase(application).exposureDao()
        repository = ExposureRepository(exposureDao)
        allReadings = repository.allReadings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun saveReading(ev: Float, lux: Float, aperture: Double, shutterSpeed: String, iso: Int, note: String?) {
        viewModelScope.launch {
            repository.saveReading(ev, lux, aperture, shutterSpeed, iso, note)
        }
    }

    fun deleteReading(reading: ExposureReading) {
        viewModelScope.launch {
            repository.deleteReading(reading)
        }
    }
}
