package com.sza.fastmediasorter.wear.ui.player.common

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/** Twice a second: fast enough for a moving progress bar, slow enough to stay off S1709's budget. */
private const val PROGRESS_TICK_MS = 500L

/**
 * S2432: the position pump both watch players run while sound or frames are moving.
 *
 * The tick deliberately keeps running while the audio screen is dark: stopping it was measured on the
 * watch (679 recompositions per ten seconds against 672 with it running), so the recomposition it
 * drives is not what a dark screen costs.
 */
internal class PlaybackProgressTicker(
    private val scope: CoroutineScope,
    private val player: ExoPlayer,
    private val onPosition: (Long) -> Unit
) {

    private var job: Job? = null

    fun start() {
        Timber.d("S2432: shared progress ticker started")
        job?.cancel()
        job = scope.launch {
            while (isActive && player.isPlaying) {
                onPosition(player.currentPosition.coerceAtLeast(0))
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
