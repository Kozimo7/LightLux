package com.example.lightluxmeter.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lightluxmeter.data.db.AppDatabase
import com.example.lightluxmeter.data.model.ExposureReading
import com.example.lightluxmeter.data.repository.ExposureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExposureViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ExposureRepository
    val allReadings: StateFlow<List<ExposureReading>>

    private val _currentEv100 = MutableStateFlow(0.0)
    val currentEv100: StateFlow<Double> = _currentEv100.asStateFlow()

    private val _currentLux = MutableStateFlow(0.0)
    val currentLux: StateFlow<Double> = _currentLux.asStateFlow()

    private val continuousExposureBuffer = mutableListOf<Double>()
    private var lastIntervalStartTime = System.currentTimeMillis()

    init {
        val exposureDao = AppDatabase.getDatabase(application).exposureDao()
        repository = ExposureRepository(exposureDao)
        allReadings = repository.allReadings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun updateLiveMetadata(ev100: Double) {
        continuousExposureBuffer.add(ev100)
        
        val now = System.currentTimeMillis()
        if (now - lastIntervalStartTime >= 500) {
            if (continuousExposureBuffer.isNotEmpty()) {
                val averageEv = continuousExposureBuffer.average()
                val rounded = (kotlin.math.round(averageEv * 3.0) / 3.0)
                
                _currentEv100.value = rounded
                _currentLux.value = com.example.lightluxmeter.domain.LuminosityAnalyzer.ev100ToLux(averageEv)
                
                continuousExposureBuffer.clear()
            }
            lastIntervalStartTime = now
        }
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
