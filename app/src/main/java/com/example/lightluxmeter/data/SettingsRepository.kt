package com.example.lightluxmeter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")

    val isDarkMode: Flow<Boolean> =
            context.dataStore.data.map { preferences ->
                preferences[DARK_MODE_KEY] ?: false
            }

    suspend fun setDarkMode(isEnabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[DARK_MODE_KEY] = isEnabled }
    }
}
