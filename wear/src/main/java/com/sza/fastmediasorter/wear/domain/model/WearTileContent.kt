package com.sza.fastmediasorter.wear.domain.model

/**
 * S1955: the four states a Wear OS tile can draw.
 *
 * Each is a complete visual state and not an error case (strategic §5.2).
 */
sealed interface WearTileContent {

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
