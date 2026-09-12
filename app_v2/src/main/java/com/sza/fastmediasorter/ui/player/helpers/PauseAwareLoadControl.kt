package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import timber.log.Timber

/**
 * Wraps [DefaultLoadControl] and returns false from [shouldContinueLoading] while the player
 * is paused. Prevents network DataSources (SFTP/SMB/FTP/Cloud) from continuing to fill the
 * ExoPlayer buffer - and holding open connections - while playback is stopped by the user.
 * On resume, ExoPlayer calls shouldContinueLoading with playWhenReady restored and buffering restarts.
 *
 * Register this instance as a Player.Listener after ExoPlayer is built so the flag is kept
 * in sync with the player state.
 *
 * S2914: media3 1.11.0 eliminated the cross-calling default overloads that caused StackOverflowError
 * under `by delegate` in 1.2.1 (S1776 ADR-3). The new `LoadControl.Parameters` methods delegate
 * one-way to the deprecated versions, which throw by default. Explicit implementation with direct
 * delegation to DefaultLoadControl replaces both the `by delegate` pattern and the deprecated
 * override suppressions.
 */
internal class PauseAwareLoadControl(
    private val delegate: DefaultLoadControl
) : LoadControl, Player.Listener {

    @Volatile
    private var isPlayWhenReady = true

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        if (!isPlayWhenReady) return false
        return delegate.shouldContinueLoading(parameters)
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldStartPlayback(parameters)

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) {
        delegate.onTracksSelected(parameters, trackGroups, trackSelections)
    }

    override fun getAllocator(playerId: PlayerId): Allocator =
        delegate.getAllocator(playerId)

    override fun getBackBufferDurationUs(playerId: PlayerId): Long =
        delegate.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
        delegate.retainBackBufferFromKeyframe(playerId)

    override fun onPrepared(playerId: PlayerId) {
        delegate.onPrepared(playerId)
    }

    override fun onStopped(playerId: PlayerId) {
        delegate.onStopped(playerId)
    }

    override fun onReleased(playerId: PlayerId) {
        delegate.onReleased(playerId)
    }

    override fun shouldContinuePreloading(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaPeriodId,
        playbackPositionUs: Long,
    ): Boolean = delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, playbackPositionUs)

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        isPlayWhenReady = playWhenReady
    }
}
