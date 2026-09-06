package com.sza.fastmediasorter.wear.domain.model

/**
 * S1955: the states a Wear OS tile can draw.
 *
 * Each is a complete visual state and not an error case (strategic §5.2).
 */
sealed interface WearTileContent {

    /**
     * S2511: a grid of shortcuts, which is the second role in this family.
     *
     * The four states below all describe one pinned unit of content and the ways it can be absent. This one
     * pins nothing: its entries come from a fixed catalog, so it has no unassigned state and cannot be
     * emptied by anything the owner does.
     */
    data class Shortcuts(val entries: List<WearTileShortcut>) : WearTileContent

    data class Assigned(
        val title: String,
        val subtitle: String?,
        val iconResId: Int?,
        val launchTarget: WearLaunchTarget,
        val entries: List<String> = emptyList()
    ) : WearTileContent

    data class Unassigned(val kind: WearTileKind) : WearTileContent

    data class TargetMissing(val kind: WearTileKind) : WearTileContent

    data object FavouritesEmpty : WearTileContent
}
