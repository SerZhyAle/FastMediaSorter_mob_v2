package com.sza.fastmediasorter.wear.ui.player.common

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/** S1701: how long the volume readout stays after the last bezel step. */
private const val VOLUME_VISIBLE_MS = 1_500L

/**
 * S2432: one bezel step on the system media stream, plus the countdown that takes the readout away
 * again - held once for both watch players instead of once each.
 *
 * ADR-1 of S1701 moved the bezel from seeking to volume, which is the Wear OS media convention. The
 * level is read back from the system after the adjustment instead of being tracked here, so a change
 * made by the watch's own volume UI is reflected the next time the bezel moves rather than fighting a
 * private counter.
 *
 * [onReadout] is called only when the system actually answered; a device with no audio service leaves
 * the screen untouched, which is what both players did before this was shared.
 */
internal class PlayerVolumeController(
    private val scope: CoroutineScope,
    private val context: Context,
    private val onReadout: (level: Int, max: Int) -> Unit,
    private val onHidden: () -> Unit
) {

    /** Cancelled and restarted on every bezel step; dies with the owning view model. */
    private var hideJob: Job? = null

    fun onStep(up: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0,
        )
        Timber.d("S2432: shared volume step up=$up")
        onReadout(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        )
        hideAfterDelay()
    }

    fun cancel() {
        hideJob?.cancel()
        hideJob = null
    }

    /**
     * Restarted on every step so a continuous turn keeps the readout up; S1701 strategic 3.2 forbids
     * adding to what the wave drawing already costs, so it must not stay on screen once the user has
     * stopped turning.
     */
    private fun hideAfterDelay() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(VOLUME_VISIBLE_MS)
            onHidden()
        }
    }
}
