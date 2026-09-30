package com.sza.fastmediasorter.wear.domain.model

/**
 * S3555: a long-running program in progress, as a shortcut tile points back at it (WO-V4).
 *
 * The texts are resolved here for the reason [WearTileShortcut] gives: the tile is drawn from this record
 * alone. Nothing here names the stopwatch, so the next long-running program fills the same three fields
 * instead of teaching the tile layer about itself.
 *
 * @property label what is running, shown above the grid.
 * @property contentDescription what a screen reader announces on the chip that opens it.
 */
data class WearTileRunningProgram(
    val destinationId: WearDestinationId,
    val label: String,
    val contentDescription: String
)
