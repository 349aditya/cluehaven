package com.cluehaven.puzzlegames.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "cluehaven_settings")

data class AppSettings(val darkTheme: Boolean = false)

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val darkThemeKey = booleanPreferencesKey("dark_theme")

    val settings: Flow<AppSettings> = appContext.settingsStore.data.map { preferences ->
        AppSettings(darkTheme = preferences[darkThemeKey] ?: false)
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        appContext.settingsStore.edit { preferences ->
            preferences[darkThemeKey] = enabled
        }
    }
}
