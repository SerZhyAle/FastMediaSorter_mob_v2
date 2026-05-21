package com.sza.fastmediasorter.ui.settings.search

/**
 * Produces raw, unresolved settings search entries.
 *
 * Implementations capture view ids and attribute references from one or more sources
 * (currently only the layout XML scanner). Localization happens downstream in
 * `SettingsSearchKeywordCollector`.
 */
interface SettingsSearchSource {
    fun collect(): List<RawSettingsSearchEntry>
}
