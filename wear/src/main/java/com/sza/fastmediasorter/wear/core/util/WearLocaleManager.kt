package com.sza.fastmediasorter.wear.core.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import timber.log.Timber
import java.util.Locale

/**
 * Manages locale resolution, validation, and runtime application on Wear OS.
 *
 * S1814: Wear UI language follows the active phone app language. If a language is unsupported, it is
 * gracefully ignored (ADR-2).
 *
 * S2054: the supported set is no longer restated here - it comes from [WearLanguageCatalog], which reads
 * `res/xml/locales_config.xml`. That file is also what Android's per-app language settings read on API
 * 33+, so both paths into the watch's language now agree by construction rather than by coincidence.
 */
object WearLocaleManager {

    /**
     * Resolves incoming language code or tag (e.g. "ru", "ru-RU", "de", "zh-Hans") to a declared tag.
     * Returns null if the language is blank or not declared.
     */
    fun resolveSupportedTag(context: Context, languageCode: String?): String? =
        WearLanguageCatalog.resolveTag(context, languageCode)

    /**
     * Applies the specified language tag to the application environment.
     * On Android 13+ (API 33+), sets per-app application locales via [LocaleManager].
     * On API 28..32, updates [Locale.setDefault] and resources configuration.
     */
    @Suppress("TooGenericExceptionCaught")
    fun applyLocale(context: Context, languageTag: String?) {
        val resolvedTag = resolveSupportedTag(context, languageTag) ?: return

        Timber.d("WearLocaleManager: applying locale tag '%s'", resolvedTag)
        Timber.d("S2054: '%s' resolved from %d declared", resolvedTag, WearLanguageCatalog.supportedTags(context).size)

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
}
