package com.example.lightluxmeter.ui.viewmodels

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lightluxmeter.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val isDarkMode: StateFlow<Boolean> =
            repository.isDarkMode.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = false
            )

    val shutterSpeedSteps: StateFlow<String> =
            repository.shutterSpeedSteps.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = "third"
            )

    fun setDarkMode(isEnabled: Boolean) {
        viewModelScope.launch { repository.setDarkMode(isEnabled) }
    }
    
    fun setShutterSpeedSteps(steps: String) {
        viewModelScope.launch { repository.setShutterSpeedSteps(steps) }
    }

    fun setLanguage(languageTag: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun getCurrentLanguage(): String {
        return AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { "en" }
    }
}
