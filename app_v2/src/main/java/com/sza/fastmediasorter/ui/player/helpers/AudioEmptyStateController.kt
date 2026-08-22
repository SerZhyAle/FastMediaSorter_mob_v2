package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.delivery.DeliveredAudioVisualizationSource
import timber.log.Timber
import java.io.File
import kotlin.random.Random

/**
 * Controls the lifecycle of audio empty-state animations when no cover art is available.
 *
 * Supported modes (values stored in [AppSettings.audioEmptyStateMode]):
 *  - [MODE_NONE]           - black background; shows static music-note icon
 *  - [MODE_AVD_PULSE]      - AudioBreathingBarsView with pulsing music-note icon
 *  - [MODE_CANVAS_BARS]    - AudioBreathingBarsView with 9 sine-animated bars
 *  - [MODE_CANVAS_WAVES]   - AudioWaveParticleView: procedural wave + particle animation
 *  - [MODE_VISUALIZATION]  - looping MP4 video via MediaPlayer + TextureView (API 16+)
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
    // S1549: vars, not vals - the host re-inflates the layout on rotation and re-points this
    // controller via [rebindViews]; the instance survives because its lifecycle observer
    // registration and playback state must not be rebuilt.
    private var audioCoverArtView: ImageView,
    private var barsView: AudioBreathingBarsView,
    private var videoView: TextureView,
    private var wavesView: AudioWaveParticleView,
    private val deliveredSource: DeliveredAudioVisualizationSource,
    // S0893: self-registers so onStop()/onStart() release/rebuild the video-visualization codec on
    // the API24+ multi-window edge, mirroring VideoPlayerManager's own DefaultLifecycleObserver use.
    private val lifecycle: Lifecycle
) : DefaultLifecycleObserver {

    companion object {
        const val MODE_NONE = "NONE"
        const val MODE_AVD_PULSE = "AVD_PULSE"
        const val MODE_CANVAS_BARS = "CANVAS_BARS"
        const val MODE_CANVAS_WAVES = "CANVAS_WAVES"
        const val MODE_VISUALIZATION = "VISUALIZATION"
        /** Legacy DataStore value - treated identically to [MODE_VISUALIZATION]. */
        const val MODE_GIF_LOOP = "GIF_LOOP"
    }

    private var currentMode: String = MODE_NONE
    private var isPlaying: Boolean = false
    private var mediaPlayer: MediaPlayer? = null
    private var currentSurface: Surface? = null
    private var pendingFile: File? = null
    // True while a video visualization is live (set in showVideo(), cleared by hide()/release()/error).
    // Gates show() so the per-track double invocation (eager + post-cover-check re-show) does not
    // rebuild the MediaPlayer twice. A real hide() or mode switch clears it and lets show() re-init.
    private var videoActive = false
    // Guards against calling MediaPlayer.start() before onPrepared (prepareAsync is in flight).
    // A premature start() raises MediaPlayer error -38 (INVALID_OPERATION), whose onError handler
    // hides the video view -> the looping background never renders (S0407). onPrepared is the sole
    // entry point for the initial start; external start() calls only resume after a pause.
    private var isPrepared = false

    init {
        lifecycle.addObserver(this)
    }

    // ────────────────────────── Public API ──────────────────────────

    fun show(mode: String) {
        // De-dupe the expensive video visualization. AudioCoverArtLoader fires show() twice per track:
        // once eagerly, then again ~1.5s later when the cover-check resolves to "no cover". Each call
        // re-picks a random clip and spins up a fresh MediaPlayer, so the background visibly swaps
        // mid-track and a codec is allocated needlessly. Skip when the same video mode is already live
        // (no hide() since); hide()/release()/mode switch clear videoActive and allow a genuine re-init.
        if (mode.isVideoMode() && currentMode.isVideoMode() && videoActive) {
            Timber.d("AudioEmptyStateController: show(mode=$mode) skipped - video already active")
            return
        }
        Timber.d("AudioEmptyStateController: show(mode=$mode)")
        // S0863: leaving video mode must release the muted looping player - hideAll() below only
        // flips View visibility (the TextureView's SurfaceTexture keeps decoding behind a GONE
        // view), and once currentMode changes below, onPause()/onIsPlayingChanged() no longer gate
        // on the video branch, so an unreleased player becomes an orphan unreachable by backgrounding.
        if (currentMode.isVideoMode() && !mode.isVideoMode()) {
            releaseMediaPlayer()
            videoActive = false
        }
        currentMode = mode
        hideAll()
        when (mode) {
            MODE_NONE -> showStaticNote()
            MODE_AVD_PULSE -> showPulseNote()
            MODE_CANVAS_BARS -> showBars()
            MODE_CANVAS_WAVES -> showWaves()
            MODE_VISUALIZATION, MODE_GIF_LOOP -> showVideo()
            else -> showStaticNote()
        }
    }

    fun hide() {
        Timber.d("AudioEmptyStateController: hide()")
        videoActive = false
        stopBars()
        stopWaves()
        releaseMediaPlayer()
        videoView.isVisible = false
        // S0893: symmetric with release() - without this, a stale listener (closing over the
        // just-finished track's file) stays attached past the point video visualization is done.
        videoView.surfaceTextureListener = null
        // audioCoverArtView visibility managed by ImageLoadingManager
    }

    fun onIsPlayingChanged(playing: Boolean) {
        Timber.d("AudioEmptyStateController: onIsPlayingChanged(playing=$playing)")
        isPlaying = playing
        when (currentMode) {
            MODE_CANVAS_BARS, MODE_AVD_PULSE -> {
                if (playing) barsView.startAnimation() else barsView.pauseAnimation()
            }
            MODE_CANVAS_WAVES -> {
                if (playing) wavesView.startAnimation() else wavesView.pauseAnimation()
            }
            MODE_VISUALIZATION, MODE_GIF_LOOP -> {
                if (playing) {
                    // Skip until prepared; onPrepared starts playback honoring the latest isPlaying.
                    // S0863: symmetric with the pause() guard below - start() throws on an Error-state
                    // player (onError no longer leaves it half-alive, but guard defensively per fix hint).
                    when {
                        isPrepared -> {
                            try {
                                mediaPlayer?.start()
                            } catch (e: IllegalStateException) {
                                Timber.w(e, "AudioEmptyStateController: start() failed on Error-state player")
                                releaseMediaPlayer()
                            }
                        }
                        // S0893: released by onStop() while backgrounded-and-paused; rebuild now that
                        // playback resumed (onStart() only rebuilds when isPlaying was already true).
                        !videoActive -> showVideo()
                    }
                } else {
                    mediaPlayer?.let { mp ->
                        try { if (mp.isPlaying) mp.pause() } catch (_: IllegalStateException) {}
                    }
                }
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        Timber.d("AudioEmptyStateController: onPause()")
        barsView.pauseAnimation()
        wavesView.pauseAnimation()
        if (currentMode == MODE_VISUALIZATION || currentMode == MODE_GIF_LOOP) {
            mediaPlayer?.let { mp ->
                try { if (mp.isPlaying) mp.pause() } catch (_: IllegalStateException) {}
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        Timber.d("AudioEmptyStateController: onResume()")
        if (isPlaying && (currentMode == MODE_CANVAS_BARS || currentMode == MODE_AVD_PULSE)) {
            barsView.startAnimation()
        }
        if (isPlaying && currentMode == MODE_CANVAS_WAVES) {
            wavesView.startAnimation()
        }
        if (isPlaying && (currentMode == MODE_VISUALIZATION || currentMode == MODE_GIF_LOOP)) {
            // S0863: symmetric with the onPause() guard above - start() throws on an Error-state player.
            if (isPrepared) {
                try {
                    mediaPlayer?.start()
                } catch (e: IllegalStateException) {
                    Timber.w(e, "AudioEmptyStateController: start() failed on Error-state player")
                    releaseMediaPlayer()
                }
            }
        }
    }

    // S0893: API24+ release edge - the prepared video MediaPlayer (hardware codec) must not be
    // retained for the unbounded duration the host sits stopped in background (CODE_AUDIT_PROTOCOL
    // contract item 2, "Player/Glide ownership"). Only the video path is released; bars/waves hold
    // no OS resource. videoActive=false lets a later show()/onIsPlayingChanged() know a rebuild is due.
    override fun onStop(owner: LifecycleOwner) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (currentMode.isVideoMode()) {
            releaseMediaPlayer()
            videoActive = false
        }
    }

    // S0893: rebuild only if the video background was actually playing when the host stopped - a
    // paused-then-backgrounded session waits for the next onIsPlayingChanged(true) instead (its
    // !videoActive branch above), avoiding an eager rebuild of a still-paused background.
    override fun onStart(owner: LifecycleOwner) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (isPlaying && currentMode.isVideoMode() && !videoActive) {
            showVideo()
        }
    }

    fun release() {
        Timber.d("AudioEmptyStateController: release()")
        videoActive = false
        barsView.stopAndReset()
        wavesView.stopAndReset()
        releaseMediaPlayer()
        videoView.surfaceTextureListener = null
        lifecycle.removeObserver(this)
    }

    /**
     * S1549: re-point at freshly inflated views after a layout re-inflate. Unlike [release], the
     * lifecycle observer registration and the playing/mode state survive; a live visualization is
     * rebuilt on the new views through the normal [show] path, because the old tree is discarded.
     */
    fun rebindViews(
        audioCoverArtView: ImageView,
        barsView: AudioBreathingBarsView,
        videoView: TextureView,
        wavesView: AudioWaveParticleView,
    ) {
        this.barsView.stopAndReset()
        this.wavesView.stopAndReset()
        releaseMediaPlayer()
        this.videoView.surfaceTextureListener = null
        videoActive = false
        this.audioCoverArtView = audioCoverArtView
        this.barsView = barsView
        this.videoView = videoView
        this.wavesView = wavesView
        if (currentMode != MODE_NONE) {
            val modeToRestore = currentMode
            currentMode = MODE_NONE
            show(modeToRestore)
        }
    }

    // ────────────────────────── Private ──────────────────────────

    private fun String.isVideoMode(): Boolean =
        this == MODE_VISUALIZATION || this == MODE_GIF_LOOP

    private fun hideAll() {
        audioCoverArtView.isVisible = false
        barsView.isVisible = false
        videoView.isVisible = false
        wavesView.isVisible = false
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

    private fun showWaves() {
        wavesView.setBackgroundColor(Color.BLACK)
        wavesView.isVisible = true
        wavesView.stopAndReset()
        if (isPlaying) wavesView.startAnimation()
    }

    /**
     * VISUALIZATION mode: muted looping MP4 via MediaPlayer + TextureView.
     * Works on API 16+. Falls back to no background (static note) when no clip is available or on any error.
     */
    private fun showVideo() {
        val file = deliveredSource.getRandomBackgroundFile()
        if (file == null) {
            videoActive = false
            Timber.i("AudioEmptyStateController: no delivered video backgrounds found, no background shown")
            videoView.isVisible = false
            showStaticNote()
            return
        }
        videoActive = true
        pendingFile = file
        Timber.d("AudioEmptyStateController: showVideo file=${file.absolutePath}, " +
            "textureAvailable=${videoView.isAvailable}, " +
            "viewSize=${videoView.width}x${videoView.height}, " +
            "surfaceTexture=${videoView.surfaceTexture}")
        videoView.isVisible = true
        // Drop any listener captured by a prior per-track re-pick so only the current file's callback is live.
        videoView.surfaceTextureListener = null

        if (videoView.isAvailable) {
            Timber.d("AudioEmptyStateController: surface already available - starting immediately")
            startMediaPlayer(Surface(videoView.surfaceTexture!!), file)
        } else {
            Timber.d("AudioEmptyStateController: surface NOT available - waiting for callback")
            videoView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, w: Int, h: Int) {
                    Timber.d("AudioEmptyStateController: onSurfaceTextureAvailable ${w}x${h}")
                    startMediaPlayer(Surface(texture), file)
                }
                override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) {
                    Timber.d("AudioEmptyStateController: onSurfaceTextureSizeChanged ${w}x${h}")
                }
                override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean {
                    Timber.d("AudioEmptyStateController: onSurfaceTextureDestroyed")
                    // S0893: the TextureView is tearing down its surface - release the MediaPlayer
                    // bound to it now (its Surface would otherwise decode into a dead target) and
                    // return true so the TextureView releases the SurfaceTexture itself. Returning
                    // false claims manual-release ownership per the SurfaceTextureListener contract,
                    // but no code path ever called SurfaceTexture.release() - a real leak.
                    releaseMediaPlayer()
                    videoActive = false
                    return true
                }
                override fun onSurfaceTextureUpdated(t: SurfaceTexture) {}
            }
        }
    }

    private fun startMediaPlayer(surface: Surface, file: File) {
        releaseMediaPlayer()
        currentSurface = surface
        Timber.d("AudioEmptyStateController: startMediaPlayer file=${file.absolutePath}, surface.isValid=${surface.isValid}")
        // S0893: hold the reference outside the field assignment - if any apply{} statement throws
        // (setDataSource/prepareAsync), `mediaPlayer` (the field) never gets assigned, but this local
        // `player` still points at the constructed instance so the catch block can release it instead
        // of orphaning it (previously only the Surface was released on such a throw).
        var player: MediaPlayer? = null
        try {
            player = MediaPlayer()
            player.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                setDataSource(file.absolutePath)
                setSurface(surface)
                setVolume(0f, 0f)   // muted: decorative background only
                isLooping = true
                setOnPreparedListener { mp ->
                    isPrepared = true
                    Timber.d("AudioEmptyStateController: MediaPlayer prepared, " +
                        "isPlaying=$isPlaying, " +
                        "video=${mp.videoWidth}x${mp.videoHeight}, " +
                        "duration=${mp.duration}ms")
                    // Apply CENTER_CROP transform: scale video so it fills the TextureView
                    // while preserving the original aspect ratio (crop edges, not stretch).
                    applyCenterCropTransform(mp)
                    // Always start the muted looping background animation.
                    // If audio is currently paused, immediately pause the video too.
                    mp.start()
                    Timber.d("AudioEmptyStateController: MediaPlayer started OK")
                    if (!isPlaying) {
                        try { mp.pause() } catch (_: IllegalStateException) {}
                    }
                }
                setOnErrorListener { _, what, extra ->
                    val whatStr = when (what) {
                        MediaPlayer.MEDIA_ERROR_UNKNOWN -> "UNKNOWN($what)"
                        MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "SERVER_DIED($what)"
                        else -> "CODE_$what"
                    }
                    val extraStr = when (extra) {
                        MediaPlayer.MEDIA_ERROR_IO -> "IO($extra)"
                        MediaPlayer.MEDIA_ERROR_MALFORMED -> "MALFORMED($extra)"
                        MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "UNSUPPORTED($extra)"
                        MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "TIMED_OUT($extra)"
                        else -> "EXTRA_$extra"
                    }
                    Timber.e("AudioEmptyStateController: MediaPlayer error what=$whatStr extra=$extraStr file=${file.absolutePath} - no background shown")
                    // S0863: MediaPlayer.OnErrorListener contract - the object enters Error state and
                    // reset() must be called before reuse; releaseMediaPlayer() also clears isPrepared so
                    // the unguarded start() sites in onIsPlayingChanged/onResume never see a dead player.
                    releaseMediaPlayer()
                    videoActive = false
                    videoView.isVisible = false
                    showStaticNote()
                    true
                }
                setOnInfoListener { _, what, extra ->
                    Timber.d("AudioEmptyStateController: MediaPlayer info what=$what extra=$extra")
                    false
                }
                prepareAsync()
                Timber.d("AudioEmptyStateController: prepareAsync() called")
            }
            mediaPlayer = player
        } catch (e: Exception) {
            player?.release()
            surface.release()
            currentSurface = null
            videoActive = false
            Timber.e(e, "AudioEmptyStateController: startMediaPlayer EXCEPTION - no background shown, file=${file.absolutePath}")
            videoView.isVisible = false
            showStaticNote()
        }
    }

    /**
     * Scales the TextureView transform matrix so the video fills the view via CENTER_CROP:
     * the video is uniformly scaled up until the shorter dimension matches the view,
     * then centered. Edges on the longer axis are cropped. No stretching.
     */
    private fun applyCenterCropTransform(mp: MediaPlayer) {
        val videoW = mp.videoWidth.takeIf { it > 0 } ?: return
        val videoH = mp.videoHeight.takeIf { it > 0 } ?: return
        val viewW = videoView.width.takeIf { it > 0 } ?: return
        val viewH = videoView.height.takeIf { it > 0 } ?: return

        val scaleX: Float
        val scaleY: Float
        val dx: Float
        val dy: Float

        val videoAspect = videoW.toFloat() / videoH
        val viewAspect = viewW.toFloat() / viewH

        if (videoAspect > viewAspect) {
            // Video is wider than view → fit height, crop sides
            scaleY = 1f
            scaleX = videoAspect / viewAspect
        } else {
            // Video is taller than view → fit width, crop top/bottom
            scaleX = 1f
            scaleY = viewAspect / videoAspect
        }
        dx = (viewW * (1f - scaleX)) / 2f
        dy = (viewH * (1f - scaleY)) / 2f

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY)
        matrix.postTranslate(dx, dy)
        videoView.setTransform(matrix)
    }

    private fun releaseMediaPlayer() {
        isPrepared = false
        mediaPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            it.reset()
            it.release()
        }
        mediaPlayer = null
        currentSurface?.release()
        currentSurface = null
    }

    // ──────────────────────── Private helpers ────────────────────────

    private fun stopBars() {
        barsView.stopAndReset()
        barsView.isVisible = false
    }

    private fun stopWaves() {
        wavesView.stopAndReset()
        wavesView.isVisible = false
    }
}
