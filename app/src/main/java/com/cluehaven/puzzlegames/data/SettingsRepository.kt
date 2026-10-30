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
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val darkTheme: Boolean = false,
    val ageGroup: AgeGroup? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reducedMotion: Boolean = false,
    val soundEffects: Boolean = false,
    val haptics: Boolean = false,
)

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val ageGroupKey = stringPreferencesKey("age_group")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")
    private val soundKey = booleanPreferencesKey("sound_effects")
    private val hapticsKey = booleanPreferencesKey("haptics")

    val settings: Flow<AppSettings> = appContext.settingsStore.data.map { preferences ->
        AppSettings(
            darkTheme = preferences[darkThemeKey] ?: false,
            themeMode = preferences[themeModeKey]?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
                ?: preferences[darkThemeKey]?.let { if (it) ThemeMode.DARK else ThemeMode.LIGHT }
                ?: ThemeMode.SYSTEM,
            reducedMotion = preferences[reducedMotionKey] ?: false,
            soundEffects = preferences[soundKey] ?: false,
            haptics = preferences[hapticsKey] ?: false,
            ageGroup = preferences[ageGroupKey]?.let { value ->
                AgeGroup.entries.firstOrNull { it.name == value }
            },
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        appContext.settingsStore.edit { preferences ->
            preferences[darkThemeKey] = enabled
            preferences[themeModeKey] = if (enabled) ThemeMode.DARK.name else ThemeMode.LIGHT.name
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        appContext.settingsStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        appContext.settingsStore.edit { it[reducedMotionKey] = enabled }
    }

    suspend fun setFeedback(sound: Boolean, haptics: Boolean) {
        appContext.settingsStore.edit { it[soundKey] = sound; it[hapticsKey] = haptics }
    }

    suspend fun setAgeGroup(group: AgeGroup) {
        appContext.settingsStore.edit { preferences ->
            preferences[ageGroupKey] = group.name
        }
    }
}
