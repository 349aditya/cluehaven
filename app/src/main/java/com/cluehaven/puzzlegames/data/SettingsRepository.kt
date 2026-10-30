package com.cluehaven.puzzlegames.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "cluehaven_settings")

enum class AgeGroup { UNDER_13, AGE_13_TO_17, AGE_18_PLUS }

data class AppSettings(
    val darkTheme: Boolean = false,
    val ageGroup: AgeGroup? = null,
)

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val ageGroupKey = stringPreferencesKey("age_group")

    val settings: Flow<AppSettings> = appContext.settingsStore.data.map { preferences ->
        AppSettings(
            darkTheme = preferences[darkThemeKey] ?: false,
            ageGroup = preferences[ageGroupKey]?.let { value ->
                AgeGroup.entries.firstOrNull { it.name == value }
            },
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        appContext.settingsStore.edit { preferences ->
            preferences[darkThemeKey] = enabled
        }
    }

    suspend fun setAgeGroup(group: AgeGroup) {
        appContext.settingsStore.edit { preferences ->
            preferences[ageGroupKey] = group.name
        }
    }
}
