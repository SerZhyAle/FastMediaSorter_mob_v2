package com.sza.fastmediasorter.wear.ui.player.common

import androidx.media3.common.Player
import timber.log.Timber

/** S1683: one step means the same thing in both watch players, which is why it is named once. */
internal const val PLAYER_SEEK_STEP_MS = 10_000L

/**
 * S2432: the position one forward step lands on.
 *
 * ExoPlayer reports `C.TIME_UNSET`, a large negative, while the duration is still unknown - clamping to
 * it would send playback backwards past the start. Reachable since S1683, because the bezel can reach
 * this action within the first moments of a stream opening.
 */
internal fun forwardSeekTarget(player: Player): Long {
    Timber.d("S2432: shared forward seek step")
    val target = player.currentPosition + PLAYER_SEEK_STEP_MS
    val duration = player.duration
    return if (duration > 0) target.coerceAtMost(duration) else target
}

/** S2432: the position one backward step lands on, never before the start. */
internal fun backwardSeekTarget(player: Player): Long =
    (player.currentPosition - PLAYER_SEEK_STEP_MS).coerceAtLeast(0)
