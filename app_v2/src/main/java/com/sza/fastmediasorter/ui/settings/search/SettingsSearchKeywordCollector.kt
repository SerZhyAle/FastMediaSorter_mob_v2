package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.ui.settings.SettingsSearchIndex

/**
 * Resolves the attribute references in a `RawSettingsSearchEntry` across all supported
 * locales and produces the fully populated `SettingsSearchIndex` entry consumed by the
 * search UI. Returns `null` when the raw entry resolves to no displayable text in any
 * locale (defensive - the source should filter these out, but the collector enforces it).
 */
interface SettingsSearchKeywordCollector {

    /**
     * Language tag the entry titles are shown in. The app language changes live, so a caller that
     * caches enriched entries compares this against the value it built them with.
     */
    fun displayLanguage(): String

    fun enrich(raw: RawSettingsSearchEntry, displayLanguage: String): SettingsSearchIndex?
}
