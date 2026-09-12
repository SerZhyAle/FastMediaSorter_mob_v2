package com.sza.fastmediasorter.wear.domain.model

/**
 * S2511: one tappable cell of a shortcut-grid tile.
 *
 * [contentDescription] is resolved to text here rather than left as a resource id because it is the only
 * thing a screen reader can announce: the grid draws glyphs alone, so a cell that carried just an icon
 * would be unreachable without sight.
 *
 * S2751: [destinationId] replaced the drawable this record used to carry. Which glyph a destination wears
 * is a drawing decision, and answering it here forced the composing use case to reach up into the screens
 * for the icon tables. The announced text stays resolved, for the reason above.
 */
data class WearTileShortcut(
    val destinationId: WearDestinationId,
    val contentDescription: String,
    val launchTarget: WearLaunchTarget
)
