package com.sza.fastmediasorter.ui.settings.search

/**
 * Unresolved attribute references captured during a single XML pass over a settings-fragment layout.
 *
 * The localization collector (Phase 03) consumes these and produces the fully resolved
 * `SettingsSearchIndex` entries by reading each resource id in EN, RU, and UK locales.
 *
 * At least one of the title/subtitle/hint slots should be non-null for a useful entry; the
 * keyword collector filters away entries that resolve to no text at all in any locale.
 */
data class RawSettingsSearchEntry(
    val viewId: Int,
    val layoutResId: Int,
    val kind: EntryKind,
    val titleResId: Int? = null,
    val subtitleResId: Int? = null,
    val hintResId: Int? = null,
    val inlineTitle: String? = null,
    val inlineSubtitle: String? = null,
    val inlineHint: String? = null
)

/**
 * Kind of settings surface the entry was captured from. Drives which attributes the
 * XML source extracts and which fallbacks the keyword collector applies.
 */
enum class EntryKind {
    TOGGLE_ROW,
    SECTION_HEADER,
    BUTTON,
    TEXT_INPUT,
    SPINNER
}
