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
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    /** In-memory cache - avoids repeated SharedPreferences/LocaleManager reads per Activity creation. */
    @Volatile private var cachedLanguageCode: String? = null

    // S1892: these two carry no behaviour - they exist only so an unchanged value is not re-logged.
    // Both sites below run on every language resolution, and printing an unchanged value 500 times
    // displaces real events out of the ring buffer a user log is truncated to.
    @Volatile private var lastLoggedSystemFallback: String? = null

    @Volatile private var lastLoggedAppliedLanguage: String? = null

    @Volatile private var lastLoggedS2936Language: String? = null

    private val languageRevisionState = MutableStateFlow(0)

    /**
     * S2571: bumped whenever the stored language changes. Carries no language of its own - a reader
     * takes a new revision as "ask again" and calls [getLanguage]. It exists because the language is
     * no longer mirrored into DataStore, so a settings flow has nothing else left to re-emit on.
     */
    val languageRevision: StateFlow<Int> = languageRevisionState.asStateFlow()

    fun isFollowSystemLanguage(languageCode: String?): Boolean {
        val normalized = languageCode?.trim()?.lowercase(Locale.ROOT)
        return normalized.isNullOrBlank() ||
            normalized == FOLLOW_SYSTEM_LANGUAGE ||
            normalized == LEGACY_DEFAULT_LANGUAGE
    }

    /**
     * S1190: the supported set comes from [UiLanguageCatalog] - the declaration in `locales_config.xml` -
     * so a new language needs no edit here. The returned value is the declared tag, which may carry a
     * script subtag (`zh-Hans`), not the caller's spelling.
     */
    fun resolveSupportedLanguageCode(languageCode: String?): String {
        val normalized = languageCode?.trim()
        if (isFollowSystemLanguage(normalized)) return detectSystemLanguage()
        return UiLanguageCatalog.resolveTag(normalized) ?: DEFAULT_LANGUAGE
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
     * Detect the system (OS) display language and map it to one of the app's supported languages;
     * anything outside the declared set falls back to English.
     *
     * S2936: on API 33+ the system locale list is read via [LocaleManager.getSystemLocales] and the
     * first entry that [UiLanguageCatalog] recognises is returned. [Resources.getSystem] reads
     * `persist.sys.locale`, which is the last-set locale and not the head of the user's ordered list,
     * so a user who put Russian first with an English `persist.sys.locale` saw English instead.
     * [systemRegion] already uses the same manager for the device region; this method mirrors it
     * for the interface language. Below API 33 or when [context] is null, the legacy
     * [Resources.getSystem] fallback stays.
     *
     * Resolves through [UiLanguageCatalog] directly rather than through [resolveSupportedLanguageCode]:
     * a device reporting a blank language would otherwise bounce between the two functions forever.
     */
    fun detectSystemLanguage(context: Context? = null): String {
        val resolved = resolveSystemLanguageViaManager(context) ?: run {
            val systemLang = Resources.getSystem().configuration.locales[0].toLanguageTag()
            UiLanguageCatalog.resolveTag(systemLang) ?: DEFAULT_LANGUAGE
        }
        if (lastLoggedS2936Language != resolved) {
            lastLoggedS2936Language = resolved
        }
        return resolved
    }

    private fun resolveSystemLanguageViaManager(context: Context?): String? {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return try {
            val systemLocales = context.getSystemService(LocaleManager::class.java)?.systemLocales
            if (systemLocales == null || systemLocales.isEmpty) {
                null
            } else {
                (0 until systemLocales.size()).firstNotNullOfOrNull { i ->
                    val tag = systemLocales[i].toLanguageTag()
                    UiLanguageCatalog.resolveTag(tag)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "LocaleHelper: Failed to read systemLocales for language, falling back to system resources")
            null
        }
    }

    /**
     * S2598: the region of the device, for a decision that depends on where the user is rather than on
     * which language the interface speaks - a measurement unit, a paper size, a calendar convention.
     *
     * The process default cannot answer this. Every writer of it builds the locale from a tag declared in
     * `locales_config.xml`, and none of the thirteen carries a region, so `Locale.getDefault().country` is
     * the empty string from the first line of the process onward. Reading it left the Fahrenheit branch of
     * the weather unit unreachable for every user in the countries that use it.
     *
     * [Resources.getSystem] does not answer it either, which is the part that is easy to get wrong and was:
     * measured on an API 35 emulator whose device locale is `en-US`, it reports region `US` while the app
     * has no per-app locale and an EMPTY region the moment one is set, because the per-app locale mechanism
     * propagates into the system resources too. It is a correct fallback only below API 33, where that
     * mechanism does not exist. [LocaleManager.getSystemLocales] is the source that stays the device's.
     *
     * Returns an empty string on a device that reports no region.
     */
    fun systemRegion(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val systemLocales = context.getSystemService(LocaleManager::class.java)?.systemLocales
                if (systemLocales != null && !systemLocales.isEmpty) return systemLocales[0].country
            } catch (e: Exception) {
                Timber.w(e, "LocaleHelper: Failed to read systemLocales, falling back to system resources")
            }
        }
        return Resources.getSystem().configuration.locales[0].country
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
        // S1190: this runs as the default argument of applyLocale, i.e. before applyLocale's own body,
        // so the catalog has to be ready here too - otherwise the very first resolution of the session
        // would see an empty catalog and cache English.
        UiLanguageCatalog.ensureInitialized(context)
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
        val systemLanguage = detectSystemLanguage(context)
        if (systemLanguage != lastLoggedSystemFallback) {
            lastLoggedSystemFallback = systemLanguage
            Timber.d("LocaleHelper: No saved language preference; using system language: $systemLanguage")
        }
        cachedLanguageCode = null
        return@allowDiskReads systemLanguage
    }

    /**
     * Save language code to preferences and LocaleManager (Android 13+)
     */
    fun saveLanguage(context: Context, languageCode: String) = StrictModeHelper.allowDiskWrites {
        val followSystem = isFollowSystemLanguage(languageCode)
        val resolvedLanguageCode = if (followSystem) {
            detectSystemLanguage(context)
        } else {
            resolveSupportedLanguageCode(languageCode)
        }
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
                val langLabel = if (followSystem) FOLLOW_SYSTEM_LANGUAGE else resolvedLanguageCode
                Timber.d("LocaleHelper: Set language via LocaleManager: $langLabel (system will restart app)")
            } catch (e: Exception) {
                Timber.e(e, "LocaleHelper: Failed to set language via LocaleManager, fallback to manual restart")
            }
        }

        languageRevisionState.update { it + 1 }
    }

    /**
     * Reset the saved language back to "follow system": clear the persisted preference, drop the
     * in-memory cache, and clear the Android 13+ per-app LocaleManager override. Used by the debug
     * "reset preferences" tool so a reset returns the app to a true clean state instead of keeping
     * the previously chosen language.
     */
    fun resetLanguage(context: Context) = StrictModeHelper.allowDiskWrites {
        cachedLanguageCode = null

        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_SELECTED_LANGUAGE)
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                    LocaleList.getEmptyLocaleList()
            } catch (e: Exception) {
                Timber.w(e, "LocaleHelper: Failed to clear LocaleManager override during reset")
            }
        }

        languageRevisionState.update { it + 1 }
    }

    /**
     * Apply locale to the given context
     * Should be called in attachBaseContext() or onCreate()
     */
    fun applyLocale(context: Context, languageCode: String = getLanguage(context)): Context {
        // S1190: earliest point in the process that resolves a language, so the catalog is parsed here
        // and every later caller can read it without carrying a Context.
        UiLanguageCatalog.ensureInitialized(context)
        val resolvedLanguageCode = resolveSupportedLanguageCode(languageCode)
        if (resolvedLanguageCode != lastLoggedAppliedLanguage) {
            lastLoggedAppliedLanguage = resolvedLanguageCode
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
        }

        val localized = localizedContext(context, languageCode)
        Locale.setDefault(Locale.forLanguageTag(resolvedLanguageCode))
        return localized
    }

    /**
     * S2571: a context for READING resources in a language other than the one the process runs in,
     * without touching the process-wide default. Everything [applyLocale] does except
     * `Locale.setDefault` - which is why [applyLocale] delegates here rather than repeating it, so the
     * two cannot drift apart.
     *
     * A caller that wants to change the language of the whole process must use [applyLocale] instead.
     * Resolving a launcher cell label does not: it runs on `Dispatchers.IO` on every desktop rebuild,
     * and mutating the global default from there carried a stale language into unrelated views.
     */
    fun localizedContext(context: Context, languageCode: String): Context {
        UiLanguageCatalog.ensureInitialized(context)
        val resolvedLanguageCode = resolveSupportedLanguageCode(languageCode)

        // forLanguageTag, not the Locale(String) constructor: a declared tag may carry a script subtag
        // ("zh-Hans"), which the constructor would take for a language code of its own.
        val locale = Locale.forLanguageTag(resolvedLanguageCode)

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
     *
     * S1153: this read gates an immediate navigate/finish branch in MainActivity.onCreate, so it
     * cannot be deferred off the main thread without breaking that control flow. It is an accepted
     * narrow StrictMode exception - the read+write is wrapped in [StrictModeHelper.allowDiskIO] so
     * both sides stay silent while remaining synchronous.
     */
    fun consumeReturnToSettings(context: Context): Boolean = StrictModeHelper.allowDiskIO {
        val prefs = context.getSharedPreferences(RESTART_STATE_PREFS, Context.MODE_PRIVATE)
        val value = prefs.getBoolean(PREF_RETURN_TO_SETTINGS, false)
        if (value) prefs.edit().remove(PREF_RETURN_TO_SETTINGS).apply()
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

    /** The language's own name, taken from the catalog rather than from a list that has to be extended. */
    fun getLanguageName(languageCode: String): String =
        UiLanguageCatalog.displayName(resolveSupportedLanguageCode(languageCode))

    /** Position of the language within the declared order; 0 (English) when it is not declared. */
    fun getLanguageIndex(languageCode: String): Int {
        val resolved = resolveSupportedLanguageCode(languageCode)
        return UiLanguageCatalog.supportedTags.indexOf(resolved).coerceAtLeast(0)
    }
}
