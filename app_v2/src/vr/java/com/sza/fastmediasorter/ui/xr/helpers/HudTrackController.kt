package com.sza.fastmediasorter.ui.xr.helpers

import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.ui.player.VideoTrackSelectionManager

/**
 * S0964: immersive-HUD track selection on top of the shared [VideoTrackSelectionManager]
 * primitives (epic S0773 ADR-3 - the VR HUD must not re-implement track selection). Cycle
 * semantics instead of a list UI: the HUD panel offers previous/next arrows per track type.
 *
 * Threading mirrors [HudPlaybackController]: ExoPlayer access is marshalled to the main looper
 * explicitly, so a render-thread call site cannot trip the player's thread check.
 *
 * Label reads may briefly lag a cycle call - `currentTracks` updates asynchronously after a
 * `TrackSelectionOverride`; the Activity re-renders once more from `onTracksChanged`, which
 * self-heals the stale label.
 */
class HudTrackController(getPlayer: () -> ExoPlayer?) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val selectionManager = VideoTrackSelectionManager(getPlayer) { null }

    /** Label of the selected (or first, or none) audio track for the HUD row. */
    fun audioLabel(noTracksLabel: String): String {
        val tracks = selectionManager.getAvailableAudioTracks()
        return tracks.firstOrNull { it.isSelected }?.label
            ?: tracks.firstOrNull()?.label
            ?: noTracksLabel
    }

    /** Label of the selected subtitle track, [offLabel] when text is off, [noTracksLabel] when none exist. */
    fun subtitleLabel(offLabel: String, noTracksLabel: String): String {
        val tracks = selectionManager.getAvailableSubtitleTracks()
        if (tracks.isEmpty()) return noTracksLabel
        return tracks.firstOrNull { it.isSelected }?.label ?: offLabel
    }

    fun hasMultipleAudioTracks(): Boolean = selectionManager.getAvailableAudioTracks().size > 1

    fun hasSubtitleTracks(): Boolean = selectionManager.getAvailableSubtitleTracks().isNotEmpty()

    /** Cycle the audio track by [step] (+1/-1); no-op with fewer than two tracks. */
    fun cycleAudio(step: Int, onApplied: () -> Unit) {
        mainHandler.post {
            val tracks = selectionManager.getAvailableAudioTracks()
            if (tracks.size < 2) return@post
            val current = tracks.indexOfFirst { it.isSelected }
            val next = tracks[(current + step).mod(tracks.size)]
            selectionManager.selectAudioTrack(next.groupIndex, next.trackIndex)
            onApplied()
        }
    }

    /**
     * Cycle subtitles through OFF + available tracks (position 0 = OFF); no-op when the file has
     * no text tracks. OFF maps to `selectSubtitleTrack(-1, -1)` which disables the TEXT type.
     */
    fun cycleSubtitle(step: Int, onApplied: () -> Unit) {
        mainHandler.post {
            val tracks = selectionManager.getAvailableSubtitleTracks()
            if (tracks.isEmpty()) return@post
            val current = tracks.indexOfFirst { it.isSelected } + 1
            val next = (current + step).mod(tracks.size + 1)
            if (next == 0) {
                selectionManager.selectSubtitleTrack(-1, -1)
            } else {
                val track = tracks[next - 1]
                selectionManager.selectSubtitleTrack(track.groupIndex, track.trackIndex)
            }
            onApplied()
        }
    }
}
