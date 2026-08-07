package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import java.util.Locale

/**
 * Maps the streams catalog's free-text facet values into [Option]s for the searchable picker (S0580).
 * Language names are lowercase English (e.g. "ukrainian"); each is resolved to a translator
 * `LanguageItem` for the flag glyph through a name->code reverse index built once over
 * [TranslationLanguageCatalog.supportedCodes]. Names outside the translator catalog (e.g. "sanskrit",
 * "tagalog", "brazilian portuguese") resolve to no flag and degrade to plain text (strategic §3.2).
 * Categories are plain strings with no flag.
 */
object StreamLanguageOptionMapper {

    /** English display-name (lowercase) -> language code, computed once over the translator catalog. */
    private val nameToCode: Map<String, String> by lazy {
        TranslationLanguageCatalog.supportedCodes.associateBy { code ->
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ENGLISH)
        }
    }

    /** Languages pinned to the top of the picker, in this order, ahead of the alphabetical remainder. */
    private val PINNED_LANGUAGES = listOf("english", "russian", "ukrainian")

    /**
     * Builds flag-bearing options from catalog language names (from `StreamsFacets.languages`). The
     * option `id` is the lowercase name so it matches `StreamsFilter.language`; the `label` is the
     * display-cased name; the flag is null when the name is not in the translator catalog. The three
     * primary languages are pinned to the top; a stable sort keeps the rest in their incoming
     * (alphabetical) order.
     */
    fun languageOptions(languageNames: List<String>): List<Option> =
        languageNames.map { name ->
            val normalized = name.trim().lowercase(Locale.ENGLISH)
            val flag = nameToCode[normalized]?.let { TranslationLanguageCatalog.findLanguage(it) }
            Option(id = normalized, label = displayCase(name.trim()), flag = flag)
        }.sortedBy { option ->
            PINNED_LANGUAGES.indexOf(option.id).let { if (it >= 0) it else PINNED_LANGUAGES.size }
        }

    /** Builds flag-less options from plain category strings (id == the raw value, matched verbatim). */
    fun categoryOptions(categories: List<String>): List<Option> =
        categories.map { Option(id = it, label = it) }

    /**
     * S1477: rubric options carry the catalog id (what [StreamsFilter] matches) but a localized label,
     * re-sorted by that label - the incoming facet list is alphabetical in English, which is the wrong
     * order once the labels are Russian or Ukrainian.
     */
    fun rubricOptions(context: Context, rubrics: List<String>): List<Option> =
        rubrics.map { rubric -> Option(id = rubric, label = StreamTopicRubricCatalog.label(context, rubric) ?: rubric) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    private fun displayCase(name: String): String =
        name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
}
