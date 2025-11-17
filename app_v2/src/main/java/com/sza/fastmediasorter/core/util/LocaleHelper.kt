package com.sza.fastmediasorter.core.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Utility for managing app language/locale
 * According to V2 Specification: Language selection with app restart
 */
object LocaleHelper {

    private const val PREF_SELECTED_LANGUAGE = "selected_language"
    private const val DEFAULT_LANGUAGE = "en"

    /**
     * Get saved language code from preferences
     */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getString(PREF_SELECTED_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    /**
     * Save language code to preferences
     */
    fun saveLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_SELECTED_LANGUAGE, languageCode).apply()
    }

    /**
     * Apply locale to the given context
     * Should be called in attachBaseContext() or onCreate()
     */
    fun applyLocale(context: Context, languageCode: String = getLanguage(context)): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    /**
     * Change language and restart the app
     * According to specification: "save language, restart and show new language everywhere"
     */
    fun changeLanguage(activity: Activity, languageCode: String) {
        saveLanguage(activity, languageCode)
        restartApp(activity)
    }

    /**
     * Restart the application
     */
    fun restartApp(activity: Activity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
        activity.finish()
    }

    /**
     * Get language name for display
     */
    fun getLanguageName(languageCode: String): String {
        return when (languageCode) {
            "en" -> "English"
            "ru" -> "Русский"
            "uk" -> "Українська"
            else -> "English"
        }
    }

    /**
     * Get language index for spinner
     */
    fun getLanguageIndex(languageCode: String): Int {
        return when (languageCode) {
            "en" -> 0
            "ru" -> 1
            "uk" -> 2
            else -> 0
        }
    }
}
