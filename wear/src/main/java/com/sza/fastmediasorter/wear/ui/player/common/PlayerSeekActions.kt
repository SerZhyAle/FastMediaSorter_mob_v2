package com.sza.fastmediasorter.wear.ui.player.common

/**
 * S2140: the three ways a watch player moves the playhead, held together rather than spread across the
 * screen's action class.
 *
 * Grouped because the two step commands arrived together with a constraint: `VideoPlayerActions` was
 * already at detekt's `constructorThreshold`, so a flat pair would have pushed it over. Grouping is the
 * better answer than raising the threshold - these three are one subject, and a caller wiring a player
 * now supplies seeking as one thing instead of remembering three unrelated-looking lambdas.
 *
 * [onSeekTo] is absolute, from the progress bar. [onSeekBackward]/[onSeekForward] are relative steps,
 * bound to a long press since S2140 moved seeking off the bezel to make room for volume.
 */
internal data class PlayerSeekActions(
    val onSeekTo: (Long) -> Unit,
    val onSeekBackward: () -> Unit,
    val onSeekForward: () -> Unit
)
