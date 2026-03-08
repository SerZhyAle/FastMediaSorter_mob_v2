package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import timber.log.Timber
import kotlin.random.Random

/**
 * Controls the lifecycle of audio empty-state animations when no cover art is available.
 *
 * Supported modes (values stored in [AppSettings.audioEmptyStateMode]):
 *  - [MODE_NONE]           — black background; shows static music-note icon
 *  - [MODE_AVD_PULSE]      — AudioBreathingBarsView with pulsing music-note icon
 *  - [MODE_CANVAS_BARS]    — AudioBreathingBarsView with 9 sine-animated bars
 *  - [MODE_VISUALIZATION]  — looping MP4 video via MediaPlayer + TextureView (API 16+)
 *
 * [MODE_GIF_LOOP] is kept as a legacy alias for [MODE_VISUALIZATION] (DataStore compat).
 *
 * Lifecycle hooks expected from the caller:
 *  - [show]                 called when no cover art found
 *  - [hide]                 called when real cover art is available
 *  - [onIsPlayingChanged]   pauses/resumes animation on player state change
 *  - [onPause] / [onResume] pass Activity lifecycle through
 *  - [release]              cancel animators and MediaPlayer on cleanup
 */
class AudioEmptyStateController(
    private val context: Context,
    private val audioCoverArtView: ImageView,
    private val barsView: AudioBreathingBarsView,
    private val videoView: TextureView
) {

    companion object {
        const val MODE_NONE = "NONE"
        const val MODE_AVD_PULSE = "AVD_PULSE"
        const val MODE_CANVAS_BARS = "CANVAS_BARS"
        const val MODE_VISUALIZATION = "VISUALIZATION"
        /** Legacy DataStore value — treated identically to [MODE_VISUALIZATION]. */
        const val MODE_GIF_LOOP = "GIF_LOOP"
    }

    private var currentMode: String = MODE_NONE
    private var isPlaying: Boolean = false
    private var mediaPlayer: MediaPlayer? = null
    private var pendingResId: Int = 0

    // ────────────────────────── Public API ──────────────────────────

    fun show(mode: String) {
        Timber.d("AudioEmptyStateController: show(mode=$mode)")
        currentMode = mode
        hideAll()
        when (mode) {
            MODE_NONE -> showStaticNote()
            MODE_AVD_PULSE -> showPulseNote()
            MODE_CANVAS_BARS -> showBars()
            MODE_VISUALIZATION, MODE_GIF_LOOP -> showVideo()
            else -> showStaticNote()
        }
    }

    fun hide() {
        Timber.d("AudioEmptyStateController: hide()")
        stopBars()
        releaseMediaPlayer()
        videoView.isVisible = false
        // audioCoverArtView visibility managed by ImageLoadingManager
    }

    fun onIsPlayingChanged(playing: Boolean) {
        Timber.d("AudioEmptyStateController: onIsPlayingChanged(playing=$playing)")
        isPlaying = playing
        when (currentMode) {
            MODE_CANVAS_BARS, MODE_AVD_PULSE -> {
                if (playing) barsView.startAnimation() else barsView.pauseAnimation()
            }
            MODE_VISUALIZATION, MODE_GIF_LOOP -> {
                if (playing) mediaPlayer?.start() else mediaPlayer?.pause()
            }
        }
    }

    fun onPause() {
        Timber.d("AudioEmptyStateController: onPause()")
        barsView.pauseAnimation()
        if (currentMode == MODE_VISUALIZATION || currentMode == MODE_GIF_LOOP) {
            mediaPlayer?.pause()
        }
    }

    fun onResume() {
        Timber.d("AudioEmptyStateController: onResume()")
        if (isPlaying && (currentMode == MODE_CANVAS_BARS || currentMode == MODE_AVD_PULSE)) {
            barsView.startAnimation()
        }
        if (isPlaying && (currentMode == MODE_VISUALIZATION || currentMode == MODE_GIF_LOOP)) {
            mediaPlayer?.start()
        }
    }

    fun release() {
        Timber.d("AudioEmptyStateController: release()")
        barsView.stopAndReset()
        releaseMediaPlayer()
    }

    // ────────────────────────── Private ──────────────────────────

    private fun hideAll() {
        audioCoverArtView.isVisible = false
        barsView.isVisible = false
        videoView.isVisible = false
    }

    private fun showStaticNote() {
        audioCoverArtView.setImageResource(R.drawable.ic_music_note)
        audioCoverArtView.isVisible = true
    }

    /**
     * AVD_PULSE mode: music-note icon behind transparent rings view.
     */
    private fun showPulseNote() {
        barsView.renderMode = AudioBreathingBarsView.RenderMode.RINGS
        barsView.setBackgroundColor(Color.TRANSPARENT)
        audioCoverArtView.setImageResource(R.drawable.ic_music_note)
        audioCoverArtView.isVisible = true
        barsView.isVisible = true
        if (isPlaying) barsView.startAnimation()
    }

    private fun showBars() {
        barsView.renderMode = AudioBreathingBarsView.RenderMode.BARS
        barsView.setBackgroundColor(Color.BLACK)
        barsView.isVisible = true
        if (isPlaying) barsView.startAnimation()
    }

    /**
     * VISUALIZATION mode: muted looping MP4 via MediaPlayer + TextureView.
     * Works on API 16+. Falls back to CANVAS_BARS on any error.
     */
    private fun showVideo() {
        val backgrounds = intArrayOf(R.raw.anim_audio_bg_1, R.raw.anim_audio_bg_2, R.raw.anim_audio_bg_3)
        pendingResId = backgrounds[Random.nextInt(backgrounds.size)]
        Timber.d("AudioEmptyStateController: showVideo resId=$pendingResId")
        videoView.isVisible = true

        if (videoView.isAvailable) {
            startMediaPlayer(Surface(videoView.surfaceTexture!!), pendingResId)
        } else {
            videoView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, w: Int, h: Int) {
                    startMediaPlayer(Surface(texture), pendingResId)
                }
                override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean = false
                override fun onSurfaceTextureUpdated(t: SurfaceTexture) {}
            }
        }
    }

    private fun startMediaPlayer(surface: Surface, resId: Int) {
        releaseMediaPlayer()
        try {
            val afd = context.resources.openRawResourceFd(resId)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setSurface(surface)
                setVolume(0f, 0f)   // muted: decorative background only
                isLooping = true
                setOnPreparedListener { mp ->
                    Timber.d("AudioEmptyStateController: MediaPlayer prepared")
                    if (isPlaying && (currentMode == MODE_VISUALIZATION || currentMode == MODE_GIF_LOOP)) {
                        mp.start()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Timber.e("AudioEmptyStateController: MediaPlayer error what=$what extra=$extra — fallback")
                    videoView.isVisible = false
                    showBars()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            surface.release()
            Timber.e(e, "AudioEmptyStateController: startMediaPlayer failed — fallback to CANVAS_BARS")
            videoView.isVisible = false
            showBars()
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            it.reset()
            it.release()
        }
        mediaPlayer = null
    }

    // ──────────────────────── Private helpers ────────────────────────

    private fun stopBars() {
        barsView.stopAndReset()
        barsView.isVisible = false
    }
}
