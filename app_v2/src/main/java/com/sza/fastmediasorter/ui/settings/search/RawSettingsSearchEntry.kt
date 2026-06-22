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
    val inlineHint: String? = null,
    // View-ids of the row's ancestor elements (parent cards/containers). Lets the availability
    // filter gate a row by its parent card (e.g. groupScreenGestures) without a per-row id list.
    val ancestorIds: List<Int> = emptyList()
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
    SPINNER,

    /**
     * `SettingsInputRow` compound widget (S0567). Carries its own label via `app:sir_title`
     * instead of `android:hint`, so it needs dedicated extraction; without it the migrated
     * input settings fall out of the search catalog (S0597 regression).
     */
    INPUT_ROW,

    /**
     * `SettingsDropdownRow` compound widget (S0567). Carries its label via `app:sdr_title`;
     * without dedicated extraction its settings (Language, Color theme, ..) are invisible to
     * search (S0598).
     */
    DROPDOWN_ROW,

    /**
     * `SettingsSelectionRow` compound widget (S0567). Carries its label via `app:ssr_title`
     * plus an optional `app:ssr_subtitle`; without dedicated extraction its settings (Device
     * profile, Saved authorizations, ..) are invisible to search (S0598).
     */
    SELECTION_ROW
}
