package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import androidx.core.os.ConfigurationCompat
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import java.util.Locale

/**
 * S0761: maps the streams catalog's country facet values (ISO 3166-1 alpha-2 codes, uppercase) into
 * [Option]s for the searchable picker. Their names use the active interface locale. RU/BY carry their
 * code in [Option.countryFlag] so the picker draws the custom image flag
 * ([LanguageFlagFormatter.hasCustomCountryFlag]); all other countries keep the emoji-in-label fallback
 * ("🇺🇦 UA") via [TranslationLanguageCatalog.getFlagEmoji]. The option `id` is the verbatim code so it
 * matches `StreamsFilter.country` (equality).
 */
object StreamCountryOptionMapper {

    fun countryOptions(context: Context, countryCodes: List<String>): List<Option> =
        countryCodes.map { code ->
            val normalized = code.trim().uppercase(Locale.ROOT)
            val customFlag = normalized.takeIf { LanguageFlagFormatter.hasCustomCountryFlag(it) }
            val flagEmoji = TranslationLanguageCatalog.getFlagEmoji(normalized)
            val countryName = localizedCountryName(context, normalized) ?: code
            val label = if (customFlag != null || flagEmoji.isBlank()) countryName else "$flagEmoji $countryName"
            Option(id = normalized, label = label, countryFlag = customFlag)
        }

    /**
     * S2314: `StreamCatalogFacetNormalizer` deliberately passes an unrecognised country value through
     * verbatim so a newer catalog stays visible, and `Locale.Builder().setRegion` answers anything but
     * an alpha-2 / three-digit region with `IllformedLocaleException` - so the raw value has to be
     * rejected before it reaches the builder, or one odd catalog row takes the whole picker down.
     * Null means "no localized name", which the caller degrades to the raw code.
     */
    private fun localizedCountryName(context: Context, normalized: String): String? {
        if (normalized.length != ISO_REGION_LENGTH || !normalized.all { it in 'A'..'Z' }) return null
        val uiLocale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        return Locale.Builder().setRegion(normalized).build()
            .getDisplayCountry(uiLocale)
            .takeUnless { it.isBlank() || it.equals(normalized, ignoreCase = true) }
    }

    private const val ISO_REGION_LENGTH = 2
}
