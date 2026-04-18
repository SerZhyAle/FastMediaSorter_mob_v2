package com.sza.fastmediasorter.vr.playback

import android.content.Context
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ExoPlayer-based implementation of VrPlaybackEngine.
 * Uses the same Media3 engine as the standard player — no second media engine (spec decision).
 * Surface is set in prepare() once the OpenXR swapchain is ready.
 */
@Singleton
class ExoVrPlaybackEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : VrPlaybackEngine {

    private var player: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override suspend fun prepare(source: VrPlaybackSource, outputSurface: Surface) {
        Timber.d("ExoVrPlaybackEngine: preparing source=${source.uri}")

        // ExoPlayer must be created and accessed on the main thread
        withContext(Dispatchers.Main) {
            val exoPlayer = ExoPlayer.Builder(context).build().also {
                player = it
            }

            // Render to the XR-owned Surface instead of a SurfaceView/TextureView
            exoPlayer.setVideoSurface(outputSurface)

            val mediaItem = MediaItem.Builder()
                .setUri(source.uri)
                .apply { source.mimeType?.let { setMimeType(it) } }
                .build()

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    override fun play() {
        player?.playWhenReady = true
    }

    override fun pause() {
        player?.playWhenReady = false
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    override suspend fun release() {
        Timber.d("ExoVrPlaybackEngine: releasing player")
        // ExoPlayer.release() must be called on the main thread
        withContext(Dispatchers.Main) {
            player?.release()
            player = null
        }
    }

    @OptIn(UnstableApi::class)
    override fun getAvailableAudioTracks(): List<PlaybackTrack> {
        val p = player ?: return emptyList()
        val tracks = mutableListOf<PlaybackTrack>()
        for (groupIndex in 0 until p.currentTracks.groups.size) {
            val group = p.currentTracks.groups[groupIndex]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    tracks.add(
                        PlaybackTrack(
                            index = tracks.size,
                            label = format.label ?: "Audio ${tracks.size + 1}",
                            language = format.language,
                            isSelected = group.isTrackSelected(trackIndex)
                        )
                    )
                }
            }
        }
        return tracks
    }

    @OptIn(UnstableApi::class)
    override fun getAvailableSubtitleTracks(): List<PlaybackTrack> {
        val p = player ?: return emptyList()
        val tracks = mutableListOf<PlaybackTrack>()
        for (groupIndex in 0 until p.currentTracks.groups.size) {
            val group = p.currentTracks.groups[groupIndex]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    tracks.add(
                        PlaybackTrack(
                            index = tracks.size,
                            label = format.label ?: "Subtitle ${tracks.size + 1}",
                            language = format.language,
                            isSelected = group.isTrackSelected(trackIndex)
                        )
                    )
                }
            }
        }
        return tracks
    }

    override fun selectAudioTrack(index: Int) {
        // TODO: Implement track selection via TrackSelectionParameters
        Timber.d("ExoVrPlaybackEngine: selectAudioTrack($index) — not yet implemented")
    }

    override fun selectSubtitleTrack(index: Int) {
        // TODO: Implement track selection via TrackSelectionParameters
        Timber.d("ExoVrPlaybackEngine: selectSubtitleTrack($index) — not yet implemented")
    }
}
