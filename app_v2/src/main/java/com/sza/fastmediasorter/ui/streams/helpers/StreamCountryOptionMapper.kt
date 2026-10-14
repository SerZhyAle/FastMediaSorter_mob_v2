package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import androidx.core.os.ConfigurationCompat
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import timber.log.Timber
import java.text.Collator
import java.util.Locale

/**
 * S0761: maps the streams catalog's country facet values (ISO 3166-1 alpha-2 codes, uppercase) into
 * [Option]s for the searchable picker. Their names use the active interface locale. RU/BY carry their
 * code in [Option.countryFlag] so the picker draws the custom image flag
 * ([LanguageFlagFormatter.hasCustomCountryFlag]); all other countries keep the emoji-in-label fallback
 * ("🇺🇦 UA") via [TranslationLanguageCatalog.getFlagEmoji]. The option `id` is the verbatim code so it
 * matches `StreamsFilter.country` (equality).
 *
 * S4133 order (owner ruling 2026-10-07): the list is alphabetical by name in the interface language,
 * except that the countries of the interface language ([InterfaceLanguageCountries]) come first as a
 * block and then appear again in the full list; English has no block. A repeated country keeps its
 * code as `id` but gets its own `rowKey`, so the picker tells the two rows apart.
 */
object StreamCountryOptionMapper {

    private const val BLOCK_ROW_PREFIX = "interface-language:"

    fun countryOptions(context: Context, countryCodes: List<String>): List<Option> =
        countryOptions(uiLocale(context), countryCodes)

    fun countryOptions(uiLocale: Locale, countryCodes: List<String>): List<Option> {
        val collator = Collator.getInstance(uiLocale).apply { strength = Collator.PRIMARY }
        val named = countryCodes
            .map { code ->
                val normalized = code.trim().uppercase(Locale.ROOT)
                normalized to (localizedCountryName(uiLocale, normalized) ?: code)
            }
            .sortedWith { a, b -> collator.compare(a.second, b.second) }
        val all = named.map { (normalized, name) -> option(normalized, name) }
        val block = InterfaceLanguageCountries.forLocale(uiLocale)
        val leading = all.filter { it.id in block }.map { it.copy(rowKey = BLOCK_ROW_PREFIX + it.id) }
        Timber.d("S4133: country picker locale=${uiLocale.language} block=${leading.size} total=${all.size}")
        return leading + all
    }

    private fun option(normalized: String, countryName: String): Option {
        val customFlag = normalized.takeIf { LanguageFlagFormatter.hasCustomCountryFlag(it) }
        val flagEmoji = TranslationLanguageCatalog.getFlagEmoji(normalized)
        val label = if (customFlag != null || flagEmoji.isBlank()) countryName else "$flagEmoji $countryName"
        return Option(id = normalized, label = label, countryFlag = customFlag)
    }

    /**
     * S2314: a value that is not an alpha-2 / three-digit region makes `Locale.Builder().setRegion` throw
     * `IllformedLocaleException`, so the raw value is rejected before it reaches the builder. After the
     * STREAM-BANK 2.3 fold a catalog holds only assigned codes, and this stays as a safety net for a row
     * stored before it. Null means "no localized name", which the caller degrades to the raw code.
     */
    private fun localizedCountryName(uiLocale: Locale, normalized: String): String? {
        if (normalized.length != ISO_REGION_LENGTH || !normalized.all { it in 'A'..'Z' }) return null
        return Locale.Builder().setRegion(normalized).build()
            .getDisplayCountry(uiLocale)
            .takeUnless { it.isBlank() || it.equals(normalized, ignoreCase = true) }
    }

    private fun uiLocale(context: Context): Locale =
        ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()

    private const val ISO_REGION_LENGTH = 2
}
