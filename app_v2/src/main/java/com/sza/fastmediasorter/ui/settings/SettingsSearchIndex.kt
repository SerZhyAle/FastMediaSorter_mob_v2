package com.sza.fastmediasorter.ui.settings

/**
 * Settings tab the search result navigates to when the user picks an entry.
 * S4621: [destinationId] is the stable [SettingsDestination.id]; `SettingsActivity` resolves the pager
 * position through the adapter, so a flavor page inserted before About cannot shift a result.
 */
enum class SettingsSearchDestination(val destinationId: String) {
    GENERAL(SettingsDestination.GENERAL),
    MEDIA(SettingsDestination.MEDIA),
    PLAYBACK(SettingsDestination.PLAYBACK),
    OPERATIONS(SettingsDestination.OPERATIONS),
    LAUNCHER(SettingsDestination.LAUNCHER),
    TOOLS(SettingsDestination.TOOLS),
    ABOUT(SettingsDestination.ABOUT),
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
