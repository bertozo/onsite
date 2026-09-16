package com.rodolfobertozo.geotracker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _language = MutableStateFlow(LocaleManager.current(application))
    val language: StateFlow<AppLanguage> = _language

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    /** Persists the choice; the caller must recreate the Activity for it to take effect. */
    fun setLanguage(language: AppLanguage) {
        LocaleManager.save(getApplication(), language)
        _language.value = language
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
