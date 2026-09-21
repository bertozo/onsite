package com.xbertz.onsite

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.xbertz.onsite.reminders.ReminderScheduler
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

    private val _remindStart = MutableStateFlow(ReminderScheduler.isStartEnabled(application))
    val remindStart: StateFlow<Boolean> = _remindStart

    private val _remindEnd = MutableStateFlow(ReminderScheduler.isEndEnabled(application))
    val remindEnd: StateFlow<Boolean> = _remindEnd

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    /** Persists the choice; the caller must recreate the Activity for it to take effect. */
    fun setLanguage(language: AppLanguage) {
        LocaleManager.save(getApplication(), language)
        _language.value = language
    }

    fun setRemindStart(enabled: Boolean) {
        ReminderScheduler.setStartEnabled(getApplication(), enabled)
        _remindStart.value = enabled
    }

    fun setRemindEnd(enabled: Boolean) {
        ReminderScheduler.setEndEnabled(getApplication(), enabled)
        _remindEnd.value = enabled
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
