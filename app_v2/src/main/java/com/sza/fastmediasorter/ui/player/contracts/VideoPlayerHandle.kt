package com.sza.fastmediasorter.ui.player.contracts

import com.sza.fastmediasorter.ui.player.VideoTrackSelectionManager

/**
 * Capability surface over the active video/audio player that the shared dialog and
 * coordinators consume without knowing whether the host is [PlayerActivity] or
 * [StandalonePlayerActivity].
 *
 * Why: unifying the player dialog (spec_standalone-vs-inapp-player-parity §5.2) requires a
 * single interface that abstracts over [VideoPlayerManager] (in-app) and
 * [StandaloneViewManager] (standalone) for every operation the dialog touches.
 */
interface VideoPlayerHandle {

    // ── Track selection ───────────────────────────────────────────────────────

    fun getAvailableAudioTracks(): List<VideoTrackSelectionManager.TrackInfo>
    fun selectAudioTrack(groupIndex: Int, trackIndex: Int)
    fun getAvailableSubtitleTracks(): List<VideoTrackSelectionManager.TrackInfo>
    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int)

    // ── Video colour adjustments ──────────────────────────────────────────────

    fun getHueAdjustmentDegrees(): Float
    fun setHueAdjustmentDegrees(degrees: Float)
    fun getBrightnessProgress(): Int
    fun setBrightnessProgress(progress: Int)
    fun getBrightnessPercentOffset(): Int

    // ── Playback speed ────────────────────────────────────────────────────────

    /** Returns the current playback speed; defaults to 1.0 when not determinable. */
    fun getPlaybackSpeed(): Float

    /**
     * Applies [speed] to whichever player is currently active.
     * For in-app audio with a running service, routes to [AudioPlaybackService].
     * For standalone, routes to the active ExoPlayer inside [StandaloneViewManager].
     */
    fun setPlaybackSpeed(speed: Float)
}
