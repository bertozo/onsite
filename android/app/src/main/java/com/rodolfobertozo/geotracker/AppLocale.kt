package com.rodolfobertozo.geotracker

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** Languages the user can pick in Settings. [SYSTEM] follows the phone's locale. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    PORTUGUESE("pt"),
    SPANISH("es");

    val locale: Locale? get() = tag?.let { Locale.forLanguageTag(it) }
}

/**
 * Applies the language chosen in Settings. The choice lives in the "settings"
 * SharedPreferences (same file as the theme) and is read synchronously because
 * [MainActivity.attachBaseContext] needs it before anything else is created.
 */
object LocaleManager {

    private const val PREFS = "settings"
    private const val KEY_LANGUAGE = "language"

    fun current(context: Context): AppLanguage {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
        return runCatching { AppLanguage.valueOf(stored ?: "") }.getOrDefault(AppLanguage.SYSTEM)
    }

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
    }

    /** Returns [context] re-configured for the chosen language, or [context] itself when following the system. */
    fun wrap(context: Context): Context {
        val locale = current(context).locale ?: return context
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(config)
    }

    /** Resources in the chosen language, for code that only holds an Application context (ViewModels, PDF). */
    fun resources(context: Context): Resources = wrap(context).resources
}
