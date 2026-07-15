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

    // ── Manual frame rotation (S0995) ─────────────────────────────────────────

    /**
     * Apply a pure-visual frame rotation of [degrees] (∈ {0,90,180,270}, clockwise) to the active
     * video surface. This is NOT the screen-orientation sensor and NOT a destructive file edit -
     * it rotates only the rendered frame; controls stay upright. No-op when no video is active.
     *
     * Default no-op: hosts without a rotatable frame (audio/document/text and the deprecated
     * standalone host) inherit it unchanged; the image/video handles override with the real apply.
     */
    fun setContentRotationDegrees(degrees: Int) {}

    /** Current visual frame rotation in degrees (0 when not determinable). Default 0 for frameless hosts. */
    fun getContentRotationDegrees(): Int = 0

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
