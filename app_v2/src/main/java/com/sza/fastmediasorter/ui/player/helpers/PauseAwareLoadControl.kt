package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection

/**
 * Wraps [DefaultLoadControl] and returns false from [shouldContinueLoading] while the player
 * is paused. Prevents network DataSources (SFTP/SMB/FTP/Cloud) from continuing to fill the
 * ExoPlayer buffer — and holding open connections — while playback is stopped by the user.
 * On resume, ExoPlayer calls shouldContinueLoading with playWhenReady restored and buffering restarts.
 *
 * Register this instance as a Player.Listener after ExoPlayer is built so the flag is kept
 * in sync with the player state.
 */
internal class PauseAwareLoadControl(
    private val delegate: DefaultLoadControl
) : LoadControl by delegate, Player.Listener {

    @Volatile
    private var isPlayWhenReady = true

    override fun shouldContinueLoading(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
        playbackSpeed: Float
    ): Boolean {
        if (!isPlayWhenReady) return false
        return delegate.shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed)
    }

    // Kotlin `by delegate` calls LoadControl.DefaultImpls.<method>(this, ...) for Java default
    // methods instead of delegate.<method>(...). LoadControl.java has pairs of cross-calling
    // default overloads (shouldStartPlayback, onTracksSelected) that recurse infinitely through
    // `this` (StackOverflowError). Explicit overrides below force dispatch to DefaultLoadControl.
    override fun shouldStartPlayback(
        timeline: Timeline,
        mediaPeriodId: MediaPeriodId,
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long
    ): Boolean = delegate.shouldStartPlayback(
        timeline, mediaPeriodId, bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs
    )

    @Suppress("OVERRIDE_DEPRECATION")
    override fun shouldStartPlayback(
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long
    ): Boolean = delegate.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs)

    override fun onTracksSelected(
        timeline: Timeline,
        mediaPeriodId: MediaPeriodId,
        renderers: Array<out Renderer>,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection>
    ) {
        delegate.onTracksSelected(timeline, mediaPeriodId, renderers, trackGroups, trackSelections)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTracksSelected(
        renderers: Array<out Renderer>,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection>
    ) {
        delegate.onTracksSelected(renderers, trackGroups, trackSelections)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        isPlayWhenReady = playWhenReady
    }
}
