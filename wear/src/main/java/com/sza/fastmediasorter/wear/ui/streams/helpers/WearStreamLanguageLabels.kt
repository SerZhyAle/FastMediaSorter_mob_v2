package com.sza.fastmediasorter.wear.ui.streams.helpers

import java.util.Locale

/**
 * S2146: turns the catalogue's English language names into names in the interface language.
 *
 * Strategic §9 ADR-2 splits this from the rubrics deliberately. Rubrics are a closed set of 32 with a
 * translation each, so they get a map; languages number over a hundred, arrive as English words, and
 * the system already holds a translation of every one of them - so the system's own locale table is
 * used instead of copying the phone's translator catalogue into this module, and instead of anyone
 * hand-translating a hundred names.
 *
 * The catalogue value is the id the filter matches on. Only the shown label is translated.
 */
object WearStreamLanguageLabels {

    /**
     * Lowercased English display name to ISO code, built once on first use.
     *
     * Reverse rather than forward because the catalogue gives the name and wants the code; there is no
     * lookup in that direction, so the index is built by walking the codes and asking each for its
     * English name. `Locale.ENGLISH` on both sides pins the key's language and its casing - reading the
     * device locale here would key the index differently on every watch.
     */
    private val codeByEnglishName: Map<String, String> by lazy {
        Locale.getISOLanguages().associateBy { code ->
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ENGLISH)
        }.entries.associate { (name, code) -> name to code }
    }

    /**
     * The language named for the interface, or [name] with a capital first letter when it does not
     * resolve.
     *
     * A composite catalogue value such as `brazilian portuguese` is in no ISO table and is expected to
     * miss - strategic §7 accepts that and requires it stay selectable under its own text, which is
     * also what the phone does with the same value, so the two platforms do not disagree.
     */
    fun label(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return trimmed
        // A code the table has but whose display name comes back blank falls through to the raw value
        // exactly as an unresolved name does, so the two failures need only one exit.
        val display = codeByEnglishName[trimmed.lowercase(Locale.ENGLISH)]
            ?.let { Locale.forLanguageTag(it).getDisplayLanguage() }
            ?.takeIf { it.isNotBlank() }
        return (display ?: trimmed).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
}
