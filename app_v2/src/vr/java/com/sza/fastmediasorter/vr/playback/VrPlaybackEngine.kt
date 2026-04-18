package com.sza.fastmediasorter.vr.playback

/**
 * Backend-agnostic playback engine for VR.
 *
 * Lifecycle: VrPlayerActivity creates OpenXR session →
 * OpenXrSessionManager.createSessionAndGetSurface() returns Surface →
 * VrPlayerActivity calls prepare(source, surface).
 *
 * Surface is NOT a property — it is unavailable until the XR session is initialised (ADR-2).
 */
interface VrPlaybackEngine {

    /** Prepare the source for playback on the given XR-owned Surface. */
    suspend fun prepare(source: VrPlaybackSource, outputSurface: android.view.Surface)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /** Release all engine resources. Must be called from onDestroy/onStop. */
    suspend fun release()

    fun getAvailableAudioTracks(): List<PlaybackTrack>

    fun getAvailableSubtitleTracks(): List<PlaybackTrack>

    fun selectAudioTrack(index: Int)

    fun selectSubtitleTrack(index: Int)
}

/**
 * Describes a selectable audio or subtitle track.
 */
data class PlaybackTrack(
    val index: Int,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false
)
