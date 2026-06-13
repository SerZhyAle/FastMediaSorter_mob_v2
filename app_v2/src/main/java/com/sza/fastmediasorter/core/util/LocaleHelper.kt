package com.sza.fastmediasorter.core.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import androidx.core.os.LocaleListCompat
import com.sza.fastmediasorter.BuildConfig
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
    const val FOLLOW_SYSTEM_LANGUAGE = "system"
    private const val LEGACY_DEFAULT_LANGUAGE = "default"

    private const val RESTART_STATE_PREFS = "app_restart_state"
    private const val PREF_RETURN_TO_SETTINGS = "return_to_settings"
    private const val PREF_PENDING_CACHE_SIZE_TOAST_MB = "pending_cache_size_toast_mb"

    /** Languages the app fully supports beyond English. */
    private val SUPPORTED_NON_DEFAULT_LANGUAGES = setOf("ru", "uk")

    /** In-memory cache - avoids repeated SharedPreferences/LocaleManager reads per Activity creation. */
    @Volatile private var cachedLanguageCode: String? = null

    fun isFollowSystemLanguage(languageCode: String?): Boolean {
        val normalized = languageCode?.trim()?.lowercase(Locale.ROOT)
        return normalized.isNullOrBlank() ||
            normalized == FOLLOW_SYSTEM_LANGUAGE ||
            normalized == LEGACY_DEFAULT_LANGUAGE
    }

    fun resolveSupportedLanguageCode(languageCode: String?): String {
        val normalized = languageCode?.trim()?.lowercase(Locale.ROOT)
        return when {
            isFollowSystemLanguage(normalized) -> detectSystemLanguage()
            normalized == DEFAULT_LANGUAGE -> DEFAULT_LANGUAGE
            normalized != null && normalized in SUPPORTED_NON_DEFAULT_LANGUAGES -> normalized
            else -> DEFAULT_LANGUAGE
        }
    }

    fun isFollowingSystemLanguage(context: Context): Boolean = StrictModeHelper.allowDiskReads {
        isFollowingSystemLanguageInternal(context)
    }

    private fun isFollowingSystemLanguageInternal(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                val locales = localeManager?.applicationLocales
                if (locales != null) return locales.isEmpty
            } catch (e: Exception) {
                Timber.w(e, "LocaleHelper: Failed to inspect LocaleManager state, fallback to SharedPreferences")
            }
        }

        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return !prefs.contains(PREF_SELECTED_LANGUAGE) ||
            isFollowSystemLanguage(prefs.getString(PREF_SELECTED_LANGUAGE, null))
    }

    /**
     * Detect the system (OS) display language and map it to one of the app's supported languages.
     * Returns "ru" or "uk" if the OS is set to that language; falls back to "en" for everything else.
     */
    fun detectSystemLanguage(): String {
        // Locale.setDefault() is overridden by the app locale, so Resources.getSystem() is the
        // only stable source for the device language while the process is already localized.
        val systemLang = Resources.getSystem().configuration.locales[0].language
        return resolveSupportedLanguageCode(systemLang)
    }

    /**
     * Get the active language code for the app.
     *
     * Priority:
     *  1. Android 13+ LocaleManager (per-app language, set by user in System Settings or in-app).
     *  2. SharedPreferences (persisted by [saveLanguage] when user picks a language in-app).
     *  3. System OS language - if the OS is set to Russian or Ukrainian, use that automatically
     *     (first-launch experience; no explicit preference saved yet).
     *  4. English as final fallback.
     */
    fun getLanguage(context: Context): String = StrictModeHelper.allowDiskReads {
        cachedLanguageCode?.let { cached ->
            if (!isFollowingSystemLanguageInternal(context)) {
                Timber.d("LocaleHelper: Read language from cache: $cached")
                return@allowDiskReads cached
            }
        }

        // Android 13+ (API 33): Try reading from LocaleManager first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                val locales = localeManager?.applicationLocales
                if (locales != null && !locales.isEmpty) {
                    val languageCode = resolveSupportedLanguageCode(locales[0].language)
                    Timber.d("LocaleHelper: Read language from LocaleManager: $languageCode")
                    cachedLanguageCode = languageCode
                    return@allowDiskReads languageCode
                }
            } catch (e: Exception) {
                Timber.w(e, "LocaleHelper: Failed to read from LocaleManager, fallback to SharedPreferences")
            }
        }

        // SharedPreferences - present only after user explicitly chose a language
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (prefs.contains(PREF_SELECTED_LANGUAGE)) {
            val storedLanguageCode = prefs.getString(PREF_SELECTED_LANGUAGE, null)
            if (!isFollowSystemLanguage(storedLanguageCode)) {
                val languageCode = resolveSupportedLanguageCode(storedLanguageCode)
                Timber.d("LocaleHelper: Read language from SharedPreferences: $languageCode")
                cachedLanguageCode = languageCode
                return@allowDiskReads languageCode
            }
        }

        // No explicit preference yet - use system OS language (ru/uk) or fall back to en
        val systemLanguage = detectSystemLanguage()
        Timber.d("LocaleHelper: No saved language preference; using system language: $systemLanguage")
        cachedLanguageCode = null
        return@allowDiskReads systemLanguage
    }

    /**
     * Save language code to preferences and LocaleManager (Android 13+)
     */
    fun saveLanguage(context: Context, languageCode: String) = StrictModeHelper.allowDiskWrites {
        val followSystem = isFollowSystemLanguage(languageCode)
        val resolvedLanguageCode = if (followSystem) detectSystemLanguage() else resolveSupportedLanguageCode(languageCode)
        Timber.d("LocaleHelper: Saving language: ${if (followSystem) FOLLOW_SYSTEM_LANGUAGE else resolvedLanguageCode}")
        cachedLanguageCode = if (followSystem) null else resolvedLanguageCode
        
        // Save to SharedPreferences (backward compatibility + for attachBaseContext)
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (followSystem) {
                remove(PREF_SELECTED_LANGUAGE)
            } else {
                putString(PREF_SELECTED_LANGUAGE, resolvedLanguageCode)
            }
        }.apply()
        
        // Android 13+ (API 33): Use LocaleManager for per-app language
        // NOTE: LocaleManager automatically restarts the app, no manual restart needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                val localeList = if (followSystem) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList(Locale.forLanguageTag(resolvedLanguageCode))
                }
                localeManager?.applicationLocales = localeList
                Timber.d("LocaleHelper: Set language via LocaleManager: ${if (followSystem) FOLLOW_SYSTEM_LANGUAGE else resolvedLanguageCode} (system will restart app)")
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
        val resolvedLanguageCode = resolveSupportedLanguageCode(languageCode)
        if (BuildConfig.DEBUG) {
            val t0 = SystemClock.uptimeMillis()
            val caller = Thread.currentThread().stackTrace
                .dropWhile { frame -> !frame.className.contains("LocaleHelper") }
                .drop(1)
                .firstOrNull { frame ->
                    frame.className.contains("fastmediasorter") &&
                        !frame.className.contains("LocaleHelper")
                }
            val callerLabel = if (caller != null) {
                "${caller.className.substringAfterLast('.')}.${caller.methodName}"
            } else {
                "unknown"
            }
            Timber.d("LocaleHelper: applyLocale('$resolvedLanguageCode') called from $callerLabel [${t0}ms uptime]")
        }
        Timber.d("LocaleHelper: Applying locale: $resolvedLanguageCode")
        
        val locale = Locale(resolvedLanguageCode)
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
     * Mark that the app should return to SettingsActivity after the next restart.
     * Call this immediately before any restart triggered from within SettingsActivity.
     */
    fun markReturnToSettings(context: Context) = StrictModeHelper.allowDiskWrites {
        context.getSharedPreferences(RESTART_STATE_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_RETURN_TO_SETTINGS, true).apply()
    }

    /**
     * Returns true (and clears the flag) if a restart was triggered from SettingsActivity,
     * meaning MainActivity should immediately forward the user to SettingsActivity.
     */
    fun consumeReturnToSettings(context: Context): Boolean = StrictModeHelper.allowDiskWrites {
        val prefs = context.getSharedPreferences(RESTART_STATE_PREFS, Context.MODE_PRIVATE)
        val value = prefs.getBoolean(PREF_RETURN_TO_SETTINGS, false)
        if (value) prefs.edit().remove(PREF_RETURN_TO_SETTINGS).apply()
        value
    }

    fun markPendingCacheSizeToast(context: Context, cacheSizeMb: Int) = StrictModeHelper.allowDiskWrites {
        context.getSharedPreferences(RESTART_STATE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_PENDING_CACHE_SIZE_TOAST_MB, cacheSizeMb)
            .apply()
    }

    fun consumePendingCacheSizeToastMb(context: Context): Int? = StrictModeHelper.allowDiskWrites {
        val prefs = context.getSharedPreferences(RESTART_STATE_PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(PREF_PENDING_CACHE_SIZE_TOAST_MB)) {
            return@allowDiskWrites null
        }
        val value = prefs.getInt(PREF_PENDING_CACHE_SIZE_TOAST_MB, 0)
        prefs.edit().remove(PREF_PENDING_CACHE_SIZE_TOAST_MB).apply()
        value
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
        return when (resolveSupportedLanguageCode(languageCode)) {
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
        return when (resolveSupportedLanguageCode(languageCode)) {
            "en" -> 0
            "ru" -> 1
            "uk" -> 2
            else -> 0
        }
    }
}
