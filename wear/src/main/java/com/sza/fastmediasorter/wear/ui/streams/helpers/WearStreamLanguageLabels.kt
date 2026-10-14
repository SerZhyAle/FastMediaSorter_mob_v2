package com.sza.fastmediasorter.wear.ui.streams.helpers

import com.sza.fastmediasorter.domain.streams.facets.StreamFacetLabels
import java.util.Locale

/**
 * S2146: turns the catalogue's language ids into names in the interface language.
 *
 * S4133: the catalogue now holds the closed vocabulary of STREAM-BANK 2.3, and the label rule lives in the
 * package the watch shares with the phone, so both draw one label - the interface-language name with the
 * language's own name in parentheses - and the watch no longer keeps a private name-to-code index.
 *
 * The catalogue value is the id the filter matches on. Only the shown label is translated.
 */
object WearStreamLanguageLabels {

    /**
     * The language named for the interface, or [name] with a capital first letter when the vocabulary
     * does not know it (a channel stored before the catalogue was folded stays selectable under its own
     * text).
     */
    fun label(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return trimmed
        return StreamFacetLabels.languageLabel(trimmed.lowercase(Locale.ENGLISH), Locale.getDefault())
    }
}
