package com.sza.fastmediasorter.ui.player.helpers

import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.Tracks
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Extracted from VideoPlayerManager.kt as part of S0274 Wave 01 decomposition.
 *
 * Owns the `Player.Listener.onTracksChanged` body:
 *  - selected-track log probe kept for the S0041 pixelization investigation,
 *  - off-main stereo detection via `StereoDetector.detectForVideo` and the
 *    `PlayerCallback.onStereoDetected` dispatch,
 *  - the one-shot M2TS audio-unsupported toast (`audioUnsupportedShownForPath` guard).
 *
 * Side effects mutate manager state through the existing `internal` surface
 * (`stereoDetector`, `audioUnsupportedShownForPath`, `currentFilePath`, `managerScope`,
 * `playerCallback`, `context`). No new coroutine scope is introduced.
 */
internal class VideoPlayerTracksObserver(
    private val manager: VideoPlayerManager,
) {

    fun onTracksChanged(tracks: Tracks) {
        val videoFormat = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            ?.getTrackFormat(0)
        // WHY: VR_QUALITY_DEBUG - log selected track to diagnose S0041 pixelization regression.
        // Remove after root cause is confirmed (Phase 2 of S0041 investigation).
        Timber.d("VR_QUALITY_DEBUG: selected track format=%s", videoFormat)
        if (videoFormat != null) {
            val detectionPath = manager.currentFilePath ?: return
            manager.managerScope.launch {
                // MP4 spatial metadata can live in moov at EOF, so detection cannot block main.
                val detected = withContext(Dispatchers.IO) {
                    manager.stereoDetector.detectForVideo(detectionPath, videoFormat)
                }
                if (detectionPath == manager.currentFilePath && detected != StereoMode.UNKNOWN) {
                    Timber.d("VideoPlayerManager: onTracksChanged → detected stereo=$detected path=$detectionPath")
                    manager.playerCallback.onStereoDetected(detected, detectionPath)
                }
            }
        }
        val path = manager.currentFilePath ?: return
        val isMts = path.endsWith(".m2ts", ignoreCase = true) || path.endsWith(".m2t", ignoreCase = true)
        if (isMts && manager.audioUnsupportedShownForPath != path) {
            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (audioGroups.isNotEmpty()) {
                val allUnsupported = audioGroups.all { group ->
                    (0 until group.length).none { i -> group.isTrackSupported(i) }
                }
                if (allUnsupported) {
                    manager.audioUnsupportedShownForPath = path
                    val codecs = audioGroups
                        .flatMap { group -> (0 until group.length).map { i -> group.getTrackFormat(i).sampleMimeType ?: "?" } }
                        .distinct()
                        .joinToString(", ")
                    val msg = manager.context.getString(R.string.warning_m2ts_audio_unsupported, codecs)
                    Timber.d("S0054: VideoPlayerManager all .m2ts audio tracks unsupported path=$path codecs=$codecs - showing warning toast")
                    Timber.w("VideoPlayerManager: all audio tracks unsupported for $path - codecs=$codecs")
                    Toast.makeText(manager.context, msg, Toast.LENGTH_LONG).show()
                } else {
                    // DefaultTrackSelector already skips unsupported tracks; log confirms this.
                    Timber.d("VideoPlayerManager: .m2ts has decodable audio - DefaultTrackSelector handles selection")
                }
            }
        }
    }
}
