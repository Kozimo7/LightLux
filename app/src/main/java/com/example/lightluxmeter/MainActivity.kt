package com.example.lightluxmeter

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lightluxmeter.ui.navigation.MainScreen
import com.example.lightluxmeter.ui.theme.LightLuxMeterTheme
import com.example.lightluxmeter.ui.viewmodels.SettingsViewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val isDarkMode by settingsViewModel.isDarkMode.collectAsState()

            LightLuxMeterTheme(darkTheme = isDarkMode) { MainScreen() }
        }
    }
}
