package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import androidx.core.os.ConfigurationCompat
import com.sza.fastmediasorter.domain.streams.facets.StreamFacetLabels
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import java.text.Collator
import java.util.Locale

/**
 * Maps the streams catalog's language names into [Option]s for the searchable picker (S0580, S4133).
 * The catalog holds names from the closed vocabulary of STREAM-BANK 2.3 (lowercase English, one per
 * language); the label comes from [StreamFacetLabels] - the interface-language name followed by the
 * language's own name in parentheses, written once when the two are equal, the form the translation
 * language list already shows. A name the vocabulary does not know (a row stored before the vocabulary
 * closed) keeps its display-cased English text. No option carries a flag: a language is marked by its
 * name (ICON-EXTERNAL 0.12 rule 6).
 * Categories are handled by [StreamCategoryOptionMapper].
 */
object StreamLanguageOptionMapper {

    /** Languages pinned to the top of the picker, in this order, ahead of the sorted remainder. */
    private val PINNED_LANGUAGES = listOf("english", "russian", "ukrainian")

    /**
     * Builds options from catalog language names (from `StreamsFacets.languages`). The option `id` is
     * the lowercase name so it matches `StreamsFilter.language`. The three primary languages are pinned
     * to the top; the rest follow in the alphabet of the interface language, which the incoming facet
     * list (alphabetical in English) is not once the labels are Russian or Ukrainian.
     */
    fun languageOptions(context: Context, languageNames: List<String>): List<Option> =
        languageOptions(uiLocale(context), languageNames)

    fun languageOptions(uiLocale: Locale, languageNames: List<String>): List<Option> {
        val collator = Collator.getInstance(uiLocale).apply { strength = Collator.PRIMARY }
        return languageNames
            .map { name ->
                val id = name.trim().lowercase(Locale.ENGLISH)
                Option(id = id, label = StreamFacetLabels.languageLabel(id, uiLocale))
            }
            .sortedWith(
                compareBy<Option> { option ->
                    PINNED_LANGUAGES.indexOf(option.id).let { if (it >= 0) it else PINNED_LANGUAGES.size }
                }.thenComparator { a, b -> collator.compare(a.label, b.label) },
            )
    }

    /**
     * S1477: rubric options carry the catalog id (what [StreamsFilter] matches) but a localized label,
     * re-sorted by that label - the incoming facet list is alphabetical in English, which is the wrong
     * order once the labels are Russian or Ukrainian.
     */
    fun rubricOptions(context: Context, rubrics: List<String>): List<Option> =
        rubrics.map { rubric -> Option(id = rubric, label = StreamTopicRubricCatalog.label(context, rubric) ?: rubric) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    private fun uiLocale(context: Context): Locale =
        ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
}
