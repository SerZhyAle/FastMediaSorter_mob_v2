package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.ui.settings.SettingsSearchIndex

/**
 * Resolves the attribute references in a `RawSettingsSearchEntry` across all supported
 * locales and produces the fully populated `SettingsSearchIndex` entry consumed by the
 * search UI. Returns `null` when the raw entry resolves to no displayable text in any
 * locale (defensive - the source should filter these out, but the collector enforces it).
 */
interface SettingsSearchKeywordCollector {
    fun enrich(raw: RawSettingsSearchEntry): SettingsSearchIndex?
}
