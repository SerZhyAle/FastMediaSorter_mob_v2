package com.sza.fastmediasorter.core.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.os.LocaleListCompat
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import timber.log.Timber
import java.util.Locale

/**
 * Utility for managing app language/locale
 * According to V2 Specification: Language selection with app restart
 * 
 * IMPORTANT: Android 13+ (API 33) uses per-app language preferences via LocaleManager
 */
object LocaleHelper {

    private const val PREF_SELECTED_LANGUAGE = "selected_language"
    private const val DEFAULT_LANGUAGE = "en"

    /** Languages the app fully supports beyond English. */
    private val SUPPORTED_NON_DEFAULT_LANGUAGES = setOf("ru", "uk")

    /**
     * Detect the system (OS) display language and map it to one of the app's supported languages.
     * Returns "ru" or "uk" if the OS is set to that language; falls back to "en" for everything else.
     */
    fun detectSystemLanguage(): String {
        val systemLang = Locale.getDefault().language   // ISO 639-1 code, e.g. "ru", "uk", "en"
        return if (systemLang in SUPPORTED_NON_DEFAULT_LANGUAGES) systemLang else DEFAULT_LANGUAGE
    }

    /**
     * Get the active language code for the app.
     *
     * Priority:
     *  1. Android 13+ LocaleManager (per-app language, set by user in System Settings or in-app).
     *  2. SharedPreferences (persisted by [saveLanguage] when user picks a language in-app).
     *  3. System OS language — if the OS is set to Russian or Ukrainian, use that automatically
     *     (first-launch experience; no explicit preference saved yet).
     *  4. English as final fallback.
     */
    fun getLanguage(context: Context): String = StrictModeHelper.allowDiskReads {
        // Android 13+ (API 33): Try reading from LocaleManager first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                val locales = localeManager?.applicationLocales
                if (locales != null && !locales.isEmpty) {
                    val languageCode = locales[0].language
                    Timber.d("LocaleHelper: Read language from LocaleManager: $languageCode")
                    return@allowDiskReads languageCode
                }
            } catch (e: Exception) {
                Timber.w(e, "LocaleHelper: Failed to read from LocaleManager, fallback to SharedPreferences")
            }
        }

        // SharedPreferences — present only after user explicitly chose a language
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (prefs.contains(PREF_SELECTED_LANGUAGE)) {
            val languageCode = prefs.getString(PREF_SELECTED_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
            Timber.d("LocaleHelper: Read language from SharedPreferences: $languageCode")
            return@allowDiskReads languageCode
        }

        // No explicit preference yet — use system OS language (ru/uk) or fall back to en
        val systemLanguage = detectSystemLanguage()
        Timber.d("LocaleHelper: No saved language preference; using system language: $systemLanguage")
        return@allowDiskReads systemLanguage
    }

    /**
     * Save language code to preferences and LocaleManager (Android 13+)
     */
    fun saveLanguage(context: Context, languageCode: String) = StrictModeHelper.allowDiskWrites {
        Timber.d("LocaleHelper: Saving language: $languageCode")
        
        // Save to SharedPreferences (backward compatibility + for attachBaseContext)
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_SELECTED_LANGUAGE, languageCode).apply()
        
        // Android 13+ (API 33): Use LocaleManager for per-app language
        // NOTE: LocaleManager automatically restarts the app, no manual restart needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                val localeList = LocaleList(Locale.forLanguageTag(languageCode))
                localeManager?.applicationLocales = localeList
                Timber.d("LocaleHelper: Set language via LocaleManager: $languageCode (system will restart app)")
            } catch (e: Exception) {
                Timber.e(e, "LocaleHelper: Failed to set language via LocaleManager, fallback to manual restart")
            }
        }
    }

    /**
     * Apply locale to the given context
     * Should be called in attachBaseContext() or onCreate()
     */
    fun applyLocale(context: Context, languageCode: String = getLanguage(context)): Context {
        Timber.d("LocaleHelper: Applying locale: $languageCode")
        
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    /**
     * Change language and restart the app
     * According to specification: "save language, restart and show new language everywhere"
     * 
     * NOTE: On Android 13+, LocaleManager automatically restarts the app when language changes.
     * On older versions, we manually restart the app.
     */
    fun changeLanguage(activity: Activity, languageCode: String) {
        saveLanguage(activity, languageCode)
        
        // Android 13+ (API 33): LocaleManager handles restart automatically
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Timber.d("LocaleHelper: Android 13+ detected, LocaleManager will restart app automatically")
            // No manual restart needed, just finish current activity
            activity.finish()
        } else {
            // Android < 13: Manually restart app
            Timber.d("LocaleHelper: Android < 13, manually restarting app")
            restartApp(activity)
        }
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
