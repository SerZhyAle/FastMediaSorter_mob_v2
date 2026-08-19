package com.sza.fastmediasorter.wear.core.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import timber.log.Timber
import java.util.Locale

/**
 * Manages locale resolution, validation, and runtime application on Wear OS.
 *
 * S1814: Wear UI language follows the active phone app language.
 * The watch resolves incoming codes against its supported set [SUPPORTED_LANGUAGE_TAGS].
 * If a language is unsupported, it is gracefully ignored (ADR-2).
 */
object WearLocaleManager {

    val SUPPORTED_LANGUAGE_TAGS: Set<String> = setOf("en", "ru", "uk")

    /**
     * Resolves incoming language code or tag (e.g. "ru", "ru-RU", "uk", "en-US") to a supported tag.
     * Returns null if the language is blank or unsupported.
     */
    fun resolveSupportedTag(languageCode: String?): String? {
        val trimmed = languageCode?.trim()
        if (trimmed.isNullOrEmpty()) return null

        val lower = trimmed.lowercase(Locale.ROOT)
        val baseLang = Locale.forLanguageTag(trimmed).language.lowercase(Locale.ROOT)
        return when {
            SUPPORTED_LANGUAGE_TAGS.contains(lower) -> lower
            SUPPORTED_LANGUAGE_TAGS.contains(baseLang) -> baseLang
            else -> null
        }
    }

    /**
     * Applies the specified language tag to the application environment.
     * On Android 13+ (API 33+), sets per-app application locales via [LocaleManager].
     * On API 28..32, updates [Locale.setDefault] and resources configuration.
     */
    @Suppress("TooGenericExceptionCaught")
    fun applyLocale(context: Context, languageTag: String?) {
        val resolvedTag = resolveSupportedTag(languageTag) ?: return

        Timber.d("WearLocaleManager: applying locale tag '%s'", resolvedTag)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                localeManager?.applicationLocales = LocaleList.forLanguageTags(resolvedTag)
            } catch (e: Exception) {
                Timber.w(e, "WearLocaleManager: Failed to set applicationLocales on LocaleManager")
            }
        } else {
            val locale = Locale.forLanguageTag(resolvedTag)
            Locale.setDefault(locale)
            val config = context.resources.configuration
            config.setLocales(LocaleList(locale))
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }

    /**
     * Wraps the given context with a configuration matching the resolved language tag.
     * Used in [android.app.Activity.attachBaseContext] on API < 33.
     */
    fun wrapContext(base: Context, languageTag: String?): Context {
        val resolvedTag = resolveSupportedTag(languageTag) ?: return base
        val locale = Locale.forLanguageTag(resolvedTag)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
