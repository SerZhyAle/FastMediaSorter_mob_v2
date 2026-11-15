package com.sza.fastmediasorter.ui.settings

/**
 * Settings tab the search result navigates to when the user picks an entry.
 * `tabIndex` mirrors the ViewPager2 position in `SettingsActivity`.
 */
enum class SettingsSearchDestination(val tabIndex: Int) {
    GENERAL(0),
    MEDIA(1),
    PLAYBACK(2),
    OPERATIONS(3)
}

/**
 * One searchable settings entry, fully resolved (title in the user's display locale,
 * keyword pool harvested from all supported locales). Produced by `SettingsSearchRegistry`
 * via the auto-indexing pipeline introduced in S0284.
 */
data class SettingsSearchIndex(
    val key: String,
    val title: String,
    val keywords: List<String>,
    val sectionId: String,
    val destination: SettingsSearchDestination,
    val viewId: Int,
    // View-ids of the row's ancestor containers, carried through from the XML scan so the
    // capability gate can suppress rows whose parent card is hidden at runtime (S0599).
    val ancestorIds: List<Int> = emptyList()
)
