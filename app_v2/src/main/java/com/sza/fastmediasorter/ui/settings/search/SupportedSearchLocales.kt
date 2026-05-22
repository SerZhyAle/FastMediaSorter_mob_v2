package com.sza.fastmediasorter.ui.settings.search

/**
 * Closed set of locale tags the settings search index harvests keywords for.
 *
 * The order MUST match the project's `values/`, `values-ru/`, `values-uk/` directories.
 * Adding a new locale to the project means appending its tag here — no other code path
 * has a hard-coded list of supported locales for search.
 */
object SupportedSearchLocales {

    val tags: List<String> = listOf("en", "ru", "uk")
}
