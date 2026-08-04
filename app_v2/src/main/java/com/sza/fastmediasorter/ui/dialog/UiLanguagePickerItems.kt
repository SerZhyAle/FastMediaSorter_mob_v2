package com.sza.fastmediasorter.ui.dialog

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.core.util.UiLanguageCatalog
import com.sza.fastmediasorter.ui.player.helpers.LanguageItem
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import java.util.Locale

/**
 * S1190: turns the declared interface languages into the rows [SearchableLanguagePickerDialog] shows.
 *
 * The set of languages comes from [UiLanguageCatalog] (i.e. from `locales_config.xml`), while the flag
 * and the native spelling come from [TranslationLanguageCatalog] - the mapping the app already ships.
 * Building a second flag/name source here is exactly how the two would drift apart: that catalog also
 * owns the exception where a language is drawn with an image flag instead of an emoji.
 */
object UiLanguagePickerItems {

    /**
     * Rows for the interface-language picker: "follow system" first, then every declared language.
     *
     * [displayLocale] is the language the app is currently showing, so each row reads as
     * "German (Deutsch)" while the app is English and "Немецкий (Deutsch)" while it is Russian.
     */
    fun build(context: Context, displayLocale: Locale = Locale.getDefault()): List<LanguageItem> {
        val languages = UiLanguageCatalog.supportedTags.map { tag -> item(tag, displayLocale) }
        return listOf(followSystemItem(context)) + languages
    }

    /** The row for a single tag, used to render the current value outside the dialog as well. */
    fun item(languageTag: String, displayLocale: Locale = Locale.getDefault()): LanguageItem {
        val locale = Locale.forLanguageTag(languageTag)
        // The translation catalog is keyed by bare language code, so "zh-Hans" resolves through "zh".
        val base = TranslationLanguageCatalog.findLanguage(locale.language, displayLocale)
        return LanguageItem(
            code = languageTag,
            countryCode = base?.countryCode,
            localizedName = locale.getDisplayLanguage(displayLocale).capitalized(displayLocale),
            nativeName = UiLanguageCatalog.displayName(languageTag),
            flagEmoji = base?.flagEmoji.orEmpty(),
            capabilities = emptySet(),
        )
    }

    /**
     * The "follow system" row. It carries [LocaleHelper.FOLLOW_SYSTEM_LANGUAGE] as its code so the
     * caller stores the same value the settings screen has always stored - picking it clears the
     * per-app language rather than pinning the language the device happens to use today.
     */
    fun followSystemItem(context: Context): LanguageItem {
        val label = context.getString(R.string.language_default)
        return LanguageItem(
            code = LocaleHelper.FOLLOW_SYSTEM_LANGUAGE,
            countryCode = null,
            localizedName = label,
            nativeName = label,
            flagEmoji = FOLLOW_SYSTEM_GLYPH,
            capabilities = emptySet(),
        )
    }

    /**
     * The one-line label a screen shows for a stored language value: the flag and the language's own
     * name, or the "follow system" wording. Both the settings row and the Welcome control read it from
     * here so they cannot disagree about what the current language is called.
     *
     * Languages drawn with an image flag carry no emoji, so those labels are the name alone - the name
     * is the required carrier of meaning and the flag is decoration (strategic ADR-3).
     */
    fun label(context: Context, languageCode: String?): String {
        if (LocaleHelper.isFollowSystemLanguage(languageCode)) {
            return context.getString(R.string.language_default)
        }
        val entry = item(LocaleHelper.resolveSupportedLanguageCode(languageCode))
        return if (entry.flagEmoji.isBlank()) entry.nativeName else "${entry.flagEmoji} ${entry.nativeName}"
    }

    private fun String.capitalized(locale: Locale): String =
        replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(locale) else char.toString() }

    private const val FOLLOW_SYSTEM_GLYPH = "🌐"
}
