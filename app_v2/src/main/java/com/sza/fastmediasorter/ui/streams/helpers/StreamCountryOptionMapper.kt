package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import java.util.Locale

/**
 * S0761: maps the streams catalog's country facet values (ISO 3166-1 alpha-2 codes, uppercase) into
 * [Option]s for the searchable picker. Their names use the active interface locale. RU/BY reuse
 * the translation UI's custom image-flag contract via
 * [LanguageFlagFormatter.customCountryFlagItem]; all other countries keep the emoji-in-label fallback
 * ("🇺🇦 UA") via [TranslationLanguageCatalog.getFlagEmoji]. The option `id` is the verbatim code so it
 * matches `StreamsFilter.country` (equality).
 */
object StreamCountryOptionMapper {

    fun countryOptions(context: Context, countryCodes: List<String>): List<Option> =
        countryCodes.map { code ->
            val normalized = code.trim().uppercase(Locale.ROOT)
            val customFlag = LanguageFlagFormatter.customCountryFlagItem(normalized)
            val flagEmoji = TranslationLanguageCatalog.getFlagEmoji(normalized)
            val countryName = Locale.Builder().setRegion(normalized).build()
                .getDisplayCountry(context.resources.configuration.locales[0])
                .takeUnless { it.isBlank() || it.equals(normalized, ignoreCase = true) }
                ?: code
            val label = if (customFlag != null || flagEmoji.isBlank()) countryName else "$flagEmoji $countryName"
            Option(id = normalized, label = label, flag = customFlag)
        }
}
