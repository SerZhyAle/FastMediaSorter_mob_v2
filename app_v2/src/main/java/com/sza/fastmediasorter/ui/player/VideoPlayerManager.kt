package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.VideoPlaybackFailureSessionCache
import com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker
import com.sza.fastmediasorter.core.playback.RecentDecoderFailureTracker
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.dialog.PlayerSettingsDialog
import com.sza.fastmediasorter.ui.player.helpers.PanelStereoSingleEyeNotifier
import com.sza.fastmediasorter.ui.player.helpers.applyConfiguredVideoEffects
import com.sza.fastmediasorter.ui.player.helpers.brightnessAdjustmentToProgress
import com.sza.fastmediasorter.ui.player.helpers.brightnessProgressToAdjustment
import com.sza.fastmediasorter.ui.player.helpers.cancelPlaybackHealthCheck
import com.sza.fastmediasorter.ui.player.helpers.formatTime
import com.sza.fastmediasorter.core.memory.MemoryCheckpoint
import com.sza.fastmediasorter.core.memory.MemoryProfileCoordinator
import com.sza.fastmediasorter.core.memory.MemoryProbe
import com.sza.fastmediasorter.core.memory.MemoryScenario
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.ui.player.helpers.playCloudVideo
import com.sza.fastmediasorter.ui.player.helpers.playFtpVideo
import com.sza.fastmediasorter.ui.player.helpers.playLocalVideoInternal
import com.sza.fastmediasorter.ui.player.helpers.playWithMediaPlayer
import com.sza.fastmediasorter.ui.player.helpers.playSmbVideo
import com.sza.fastmediasorter.ui.player.helpers.playSftpVideo
import com.sza.fastmediasorter.ui.player.helpers.startPlaybackHealthCheck
import com.sza.fastmediasorter.ui.player.helpers.startPositionSaving
import com.sza.fastmediasorter.ui.player.helpers.stopPositionSaving
import com.sza.fastmediasorter.domain.models.TranslationFontFamily
import com.sza.fastmediasorter.domain.models.TranslationFontSize
import android.os.Debug
import com.bumptech.glide.Glide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.LazyThreadSafetyMode
import timber.log.Timber
/**
 * Orchestrator for video/audio playback using ExoPlayer (with MediaPlayer fallback).
 *
 * Protocol-specific setup, video-effect pipeline, position saving, and health monitoring
 * are extracted to extension-function files in [com.sza.fastmediasorter.ui.player.helpers]
 * to reduce per-file ControlFlowGraph size and prevent GC overhead limit crashes during
 * parallel flavor compilation.
 *
 * Responsibilities kept here:
 * - All shared mutable state fields
 * - [Player.Listener] (playerListener) — tightly coupled to all state, cannot be split cleanly
 * - [playVideo] dispatch (routes to protocol-specific extension)
 * - Public API surface (play/pause/seek/track selection)
 * - Lifecycle callbacks
 */
class VideoPlayerManager(
    internal val context: Context,
    private val lifecycle: Lifecycle,
    internal val playerCallback: PlayerCallback,
    internal val credentialsRepository: NetworkCredentialsRepository,
    internal val smbClient: SmbClient,
    internal val sftpClient: SftpClient,
    internal val ftpClient: FtpClient,
    internal val googleDriveClient: GoogleDriveRestClient,
    internal val oneDriveClient: OneDriveRestClient,
    internal val dropboxClient: DropboxClient,
    internal val playbackPositionRepository: PlaybackPositionRepository,
    internal val settingsRepository: SettingsRepository,
    internal val panelStereoSingleEyeNotifier: PanelStereoSingleEyeNotifier,
    // S0207 Phase 01: PRE_PLAY / AFTER_STATE_READY checkpoints emitted from playVideo / playerListener.
    internal val memoryProbe: MemoryProbe,
    // S0207 Phase 03: runtime scenario source of truth for browse/player memory defaults.
    internal val memoryProfileCoordinator: MemoryProfileCoordinator,
    // S0213 Pillar A: cooldown tracker consulted by PlayerMediaLoaderManager before replays.
    internal val decoderFailureTracker: RecentDecoderFailureTracker,
) : DefaultLifecycleObserver {

    // ═══════════════════════════════════════════════════════════════════════
    // Callback interface
    // ═══════════════════════════════════════════════════════════════════════

    interface PlayerCallback {
        fun onPlaybackReady()
        fun onPlaybackError(error: Throwable, userMessage: String? = null)
        fun onBuffering(isBuffering: Boolean)
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onPlaybackEnded()
        fun onAudioFormatChanged(format: AudioFormat?)
        fun showError(message: String)
        fun showFileNotFound(fileName: String)
        fun isActivityDestroyed(): Boolean
        fun showUnsupportedFormatError(message: String, filePath: String, isLocalFile: Boolean)
        fun onBdTsFormatError()
        /** Fired when a network VOB/DVD route error is detected; bypasses generic auto-next skip. */
        fun onNetworkContainerRouteError(path: String, hint: com.sza.fastmediasorter.ui.player.helpers.NetworkPlaybackContainerHint)
        /** Fired before a new video starts loading so session-only 3D state can reset per file. */
        fun onBeforeVideoLoad(path: String) {}
        /** Fired once per video load when a stereo format is detected. Default no-op. */
        fun onStereoDetected(mode: StereoMode, forFilePath: String) {}
        /**
         * S0213 Pillar A: fired when a manual replay request hits a path that is still inside the
         * decoder cooldown window (slideshow context auto-skips before this is reached). UI layer
         * is expected to render a snackbar with a "Skip" action; default no-op for non-UI impls.
         */
        fun onDecoderCooldownReentry(path: String, remainingSec: Int) {}
    }

    /** Audio format information exposed via [getAudioFormat]. */
    data class AudioFormat(
        val codec: String,
        val sampleRate: Int,
        val channelCount: Int,
        val bitrate: Int
    )

    // ═══════════════════════════════════════════════════════════════════════
    // Constants
    // ═══════════════════════════════════════════════════════════════════════

    companion object {
        private const val MAX_EOF_RETRIES = 3

        // Buffer configuration (ms) — default for SMB/SFTP/FTP.
        // Reduced to prevent OOM on 4K content (50-120 s at 100 Mbps = 625 MB-1.5 GB).
        internal const val MIN_BUFFER_MS = 15_000
        internal const val MAX_BUFFER_MS = 30_000
        internal const val BUFFER_FOR_PLAYBACK_MS = 5_000
        internal const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 8_000

        // Buffer configuration for Cloud (slower; higher initial-latency overhead)
        internal const val CLOUD_MIN_BUFFER_MS = 20_000
        internal const val CLOUD_MAX_BUFFER_MS = 45_000
        internal const val CLOUD_BUFFER_FOR_PLAYBACK_MS = 8_000
        internal const val CLOUD_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 12_000

        // Audio playback uses a smaller buffer than video — codec/decoder allocations are
        // much lower, so large video-oriented buffers waste memory without audible benefit.
        internal const val AUDIO_MIN_BUFFER_MS = 5_000
        internal const val AUDIO_MAX_BUFFER_MS = 12_000
        internal const val AUDIO_BUFFER_FOR_PLAYBACK_MS = 2_000
        internal const val AUDIO_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 4_000

        // Network audio still needs more cushion than local because protocol latency is higher.
        internal const val AUDIO_NETWORK_MIN_BUFFER_MS = 10_000
        internal const val AUDIO_NETWORK_MAX_BUFFER_MS = 20_000
        internal const val AUDIO_NETWORK_BUFFER_FOR_PLAYBACK_MS = 4_000
        internal const val AUDIO_NETWORK_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 6_000

        internal const val POSITION_SAVE_INTERVAL_MS = 15_000L

        // ExoPlayer reuse across track changes accumulates native codec/decoder allocations.
        // Force a full recreation every N tracks or when native heap free falls below the threshold.
        private const val PLAYER_RECREATE_INTERVAL = 4
        private const val NATIVE_HEAP_RECREATE_THRESHOLD_BYTES = 15L * 1024 * 1024
        // S0168 §5.3: VP9/other codec decoders allocate 20-30 MB native at init.
        // 30 MB free is the minimum safe margin before we attempt Glide eviction + GC.
        private const val NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES = 30L * 1024 * 1024
        // WHY: GC cannot reclaim native codec allocations — free rarely rises above ~20 MB on
        // constrained devices. Toast fires only when free < 10 MB after GC (actual failure risk),
        // not at the 30 MB eviction threshold where it was becoming chronic noise.
        private const val NATIVE_HEAP_CRITICAL_TOAST_THRESHOLD_BYTES = 10L * 1024 * 1024
        internal const val DEFAULT_BRIGHTNESS_PROGRESS = 50

        // Playback health-check — thresholds for detecting stuck / white-noise audio
        internal const val PLAYBACK_HEALTH_CHECK_DELAY_MS = 2_000L
        internal const val MAX_PLAYBACK_STUCK_COUNT = 2
    }
    // ═══════════════════════════════════════════════════════════════════════
    // Mutable state — internal so extension functions in helpers/ can access
    // ═══════════════════════════════════════════════════════════════════════

    internal var exoPlayer: ExoPlayer? = null
    internal var mediaPlayer: MediaPlayer? = null
    internal var currentPlayerView: PlayerView? = null
    internal var isUsingMediaPlayer = false

    private val playbackControlPrefs =
        context.getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, Context.MODE_PRIVATE)

    // Stereo detection runs once per video load inside onTracksChanged
    private val stereoDetector = StereoDetector()

    // S0032: resilient poster-frame extractor with preventive heap/decoder guards
    // and (in phase 02) a fallback hierarchy to Glide memory cache / placeholder.
    private val posterExtractor = VideoPosterExtractor()

    // Stereo GL effect builder — Phase 2: builds Crop effects for ExoPlayer.setVideoEffects()
    internal val stereoVideoProcessor = StereoVideoProcessor()
    internal val videoColorProcessor = VideoColorProcessor(
        initialHueDegrees = playbackControlPrefs.getFloat(PlaybackControlPreferences.KEY_HUE_DEGREES, 0f),
        initialBrightnessAdjustment = brightnessProgressToAdjustment(
            playbackControlPrefs.getInt(
                PlaybackControlPreferences.KEY_BRIGHTNESS_PERCENT,
                DEFAULT_BRIGHTNESS_PROGRESS
            )
        )
    )

    private val trackSelectionManager = VideoTrackSelectionManager(
        getPlayer = { exoPlayer },
        getPlayerView = { currentPlayerView }
    )

    private val playbackControlsHelper by lazy(LazyThreadSafetyMode.NONE) {
        VideoPlaybackControlsHelper(
            manager = this,
            context = context,
            playbackControlPrefs = playbackControlPrefs,
            trackSelectionManager = trackSelectionManager
        )
    }

    private val lifecycleHelper by lazy(LazyThreadSafetyMode.NONE) {
        VideoPlayerLifecycleHelper(
            manager = this,
            lifecycle = lifecycle
        )
    }

    // Retry logic for EOF exceptions (used only inside playerListener — stays private)
    private var playbackRetryCount = 0
    private var lastPlaybackPosition = 0L
    internal val retryHandler = Handler(Looper.getMainLooper())
    internal var retryRunnable: Runnable? = null

    // Playback position saving
    internal var currentFilePath: String? = null
    internal var positionSaveLoop: com.sza.fastmediasorter.ui.player.helpers.PositionSaveLoop? = null

    // S0029: idempotency guard for STATE_ENDED → markPlaybackCompleted.
    // Reset on every load of a new media file so re-opening the same path re-arms the path.
    @Volatile
    private var lastCompletedPath: String? = null

    // Guards the one-shot audio-unsupported toast; reset per file load in playVideo().
    @Volatile private var audioUnsupportedShownForPath: String? = null

    // One-shot low-memory toast guard — shown at most once per PlayerActivity session.
    // Not reset on periodic ExoPlayer recreation — only resets when the Activity is destroyed
    // (new VideoPlayerManager instance). Prevents chronic spam on constrained-memory devices.
    @Volatile private var lowMemoryToastShownThisSession = false

    // Playback health monitoring (detect "white noise" / stuck playback)
    internal var playbackHealthCheckRunnable: Runnable? = null
    internal var lastCheckedPosition = 0L
    internal var playbackStuckCount = 0

    // Connection throttling — resource key of the currently streaming server
    internal var activeResourceKey: String? = null

    // Counts track changes on the current ExoPlayer instance; triggers recreation at PLAYER_RECREATE_INTERVAL.
    private var trackChangesSinceRecreate = 0

    // Adaptive pre-cache plan for the currently-loading session. Set by PlayerActivity
    // from PlayerViewModel.prefetchPlan before createPlayer/play*Video. When null, the
    // playback helpers fall back to legacy per-protocol buffer constants.
    // See PLAN/spec_adaptive-playback-strategy.md §5.5.
    internal var activePrefetchPlan: com.sza.fastmediasorter.domain.model.PrefetchPlan? = null

    /**
     * Set the plan used for the next [createPlayer] / play*Video invocation. Idempotent;
     * does not touch the currently-running player (swap requires a fresh LoadControl, and
     * Media3 ties LoadControl to the player instance).
     */
    fun setPrefetchPlan(plan: com.sza.fastmediasorter.domain.model.PrefetchPlan?) {
        activePrefetchPlan = plan
    }

    internal val managerScope = CoroutineScope(Dispatchers.Main + Job())

    // Panel single-eye crop flag — see spec_panel-stereo-single-eye.md.
    // Default at construction matches the AppSettings default (flavor-aware via BuildConfig);
    // overridden as soon as the first DataStore emission arrives below.
    @Volatile
    internal var panelStereoSingleEyeEnabled: Boolean = !BuildConfig.SUPPORT_VR_PLAYER

    // VR-only override: when VrPlayerActivity enters immersive rendering, the immersive
    // renderer (VrStereoRenderer) owns per-eye crop; suppress panel single-eye crop here
    // to avoid double-cropping. Toggled by VrPlayerActivity.setVrRenderingActive().
    @Volatile
    internal var vrImmersiveActive: Boolean = false

    /**
     * Set by VrPlayerActivity when the OpenXR immersive render loop becomes active or inactive.
     * While active, panel single-eye crop is suppressed regardless of the user setting.
     */
    fun setVrImmersiveActive(active: Boolean) {
        if (vrImmersiveActive == active) return
        vrImmersiveActive = active
        Timber.d("VideoPlayerManager: vrImmersiveActive=$active — re-applying video effects")
        // Re-apply on the main thread to honour the toggle for the currently-loaded media.
        applyConfiguredVideoEffects()
    }

    init {
        settingsRepository.getSettings()
            .map { it.panelStereoSingleEye }
            .distinctUntilChanged()
            .onEach { enabled ->
                panelStereoSingleEyeEnabled = enabled
                Timber.d("VideoPlayerManager: panelStereoSingleEye=$enabled — re-applying video effects")
                applyConfiguredVideoEffects()
            }
            .launchIn(managerScope)
    }

    /**
     * Optional callback invoked with the first decoded video frame bitmap.
     * Only fires for local files — network/cloud paths are short-circuited inside
     * [onRenderedFirstFrame] because the underlying retriever has no streaming support.
     * The Boolean flag is true when the bitmap is a static placeholder (S0032 fallback).
     */
    var onFirstFrameReady: ((android.graphics.Bitmap, Boolean) -> Unit)? = null

    /** Callback invoked after each periodic position save (every 5 s). */
    var onPositionSaved: (() -> Unit)? = null

    /**
     * Invoked on the main thread after every fresh ExoPlayer instance is created.
     * Used by VrPlayerActivity to flush a pending VR surface redirect when the XR
     * session became ready before ExoPlayer existed.
     */
    var onPlayerCreated: ((ExoPlayer) -> Unit)? = null

    // Tracks whether a previous setVideoEffects() call installed a non-empty pipeline.
    // Skipping redundant setVideoEffects(emptyList()) calls avoids a black screen at pause
    // on some devices/emulators.
    internal var effectsPipelineActive = false

    // Media3 1.2.1: setVideoEffects() crashes (errorCode=7001, Presentation.createForWidthAndHeight
    // with -1,-1) when called before the decoder emits the first frame size. These two flags
    // defer the pipeline installation until onVideoSizeChanged delivers valid dimensions.
    @Volatile internal var videoSizeKnown: Boolean = false
    @Volatile internal var pendingEffectsApply: Boolean = false

    // Debounce handler for applyConfiguredVideoEffects() — see PlayerSetupHelper.kt.
    // 80 ms window coalesces rapid slider drags into a single pipeline rebuild,
    // preventing TexturePool race crash in Media3 1.2.x.
    internal val effectsHandler = Handler(Looper.getMainLooper())
    internal var pendingEffectsRunnable: Runnable? = null
    // ═══════════════════════════════════════════════════════════════════════
    // Player.Listener — stays in orchestrator (tightly coupled to all state)
    // ═══════════════════════════════════════════════════════════════════════

    internal val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playerCallback.isActivityDestroyed()) {
                Timber.w("VideoPlayerManager: Activity destroyed, ignoring state change")
                return
            }
            when (playbackState) {
                Player.STATE_READY -> {
                    Timber.d("VideoPlayerManager: Playback ready")
                    // S0207 Phase 01: post-ready memory checkpoint. scenarioTag is derived from the
                    // current file path so audio vs video can be filtered in MEM_PROBE log lines.
                    memoryProbe.record(
                        MemoryCheckpoint.AFTER_STATE_READY,
                        scenarioTag = currentFilePath?.let(::scenarioTagFor),
                    )
                    playbackRetryCount = 0
                    currentFilePath?.let(VideoPlaybackFailureSessionCache::clear)
                    // S0213 Pillar A: any source playing successfully proves the native graph
                    // recovered, so prior cooldown entries are no longer needed.
                    decoderFailureTracker.clearAll()
                    playerCallback.onBuffering(false)
                    playerCallback.onPlaybackReady()
                    startPlaybackHealthCheck()
                }
                Player.STATE_BUFFERING -> {
                    Timber.d("VideoPlayerManager: Buffering..")
                    playerCallback.onBuffering(true)
                    cancelPlaybackHealthCheck()
                }
                Player.STATE_ENDED -> {
                    Timber.d("VideoPlayerManager: Playback ended")
                    cancelPlaybackHealthCheck()
                    val completedPath = currentFilePath
                    if (completedPath != null && completedPath != lastCompletedPath) {
                        lastCompletedPath = completedPath
                        managerScope.launch(Dispatchers.IO) {
                            try {
                                playbackPositionRepository.markPlaybackCompleted(
                                    completedPath,
                                    reason = "playback-completed"
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "VideoPlayerManager: markPlaybackCompleted failed")
                            }
                        }
                    }
                    playerCallback.onPlaybackEnded()
                }
                Player.STATE_IDLE -> {
                    Timber.d("VideoPlayerManager: Player idle")
                    cancelPlaybackHealthCheck()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playerCallback.onPlaybackStateChanged(isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            // ExoPlayer interrupts the IO thread during its own shutdown (e.g. fast track switch,
            // file-move while switching). That InterruptedException is not a real playback failure.
            val isThreadInterrupted = generateSequence<Throwable>(error) { it.cause }
                .any { it is InterruptedException }
            if (isThreadInterrupted) {
                Timber.d("VideoPlayerManager: ignoring playback error caused by thread interrupt (ExoPlayer shutdown)")
                return
            }

            // Suppress recoverable SFTP IO errors while ExoPlayer is still in retry state (S0113)
            val isSftpIoError = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED &&
                generateSequence<Throwable>(error) { it.cause }
                    .any { it is java.io.IOException && it.message?.contains("SFTP", ignoreCase = true) == true }
            if (isSftpIoError && exoPlayer?.playbackState != Player.STATE_IDLE) {
                Timber.w("VideoPlayerManager: suppressing SFTP IO error toast — player not idle (will retry)")
                return
            }

            val isEOFException = error.cause is java.io.EOFException ||
                error.cause?.cause is java.io.EOFException
            val isMediaCodecError = error.errorCode >= 4000 && error.errorCode < 5000

            if (isEOFException && playbackRetryCount < MAX_EOF_RETRIES && !playerCallback.isActivityDestroyed()) {
                playbackRetryCount++
                lastPlaybackPosition = exoPlayer?.currentPosition ?: 0L
                Timber.w("VideoPlayerManager: EOFException, retry $playbackRetryCount/$MAX_EOF_RETRIES, position=$lastPlaybackPosition")
                retryRunnable?.let { retryHandler.removeCallbacks(it) }
                retryRunnable = Runnable {
                    if (!playerCallback.isActivityDestroyed()) retryPlayback()
                }
                retryHandler.postDelayed(retryRunnable!!, 1000)
                return
            } else if (isEOFException) {
                Timber.e("VideoPlayerManager: EOFException — max retries exceeded")
            }

            if (isMediaCodecError) {
                // S0213 Pillar A: arm cooldown so the very next replay of this same source is
                // short-circuited by PlayerMediaLoaderManager before media3 rebuilds its graph.
                currentFilePath?.let(decoderFailureTracker::markFailed)
                Timber.w("VideoPlayerManager: MediaCodec error — errorCode=${error.errorCode}, cause=${error.cause?.javaClass?.simpleName}")
            } else {
                Timber.e(error, "VideoPlayerManager: Playback error — errorCode=${error.errorCode}")
            }

            if (playerCallback.isActivityDestroyed()) return

            // --- Variant B: graceful audio-decoder fallback ---
            // When the audio renderer fails to initialize (e.g., DTS on Quest 3 — no platform
            // decoder, and FFmpeg extension also unavailable for this specific codec),
            // disable the audio track and resume video playback instead of skipping the file.
            // This prevents jarring auto-navigation away from a perfectly valid video file.
            val isAudioRendererFailure = run {
                val exoEx = error as? ExoPlaybackException ?: return@run false
                if (exoEx.type != ExoPlaybackException.TYPE_RENDERER) return@run false
                val rendererType = try { exoPlayer?.getRendererType(exoEx.rendererIndex) } catch (_: Exception) { null }
                rendererType == C.TRACK_TYPE_AUDIO
            }
            if (isAudioRendererFailure) {
                // S0213 Pillar A: 4003 audio-renderer failure is the canonical crash-loop entry —
                // mark the source so retries are throttled even if the user dismisses our toast.
                currentFilePath?.let(decoderFailureTracker::markFailed)
                val savedPosition = exoPlayer?.currentPosition ?: 0L
                Timber.w("VideoPlayerManager: Audio renderer failed (errorCode=${error.errorCode}) — disabling audio, resuming video at ${savedPosition}ms")
                Toast.makeText(
                    context,
                    context.getString(R.string.warning_audio_format_unsupported),
                    Toast.LENGTH_LONG
                ).show()
                playerCallback.onBuffering(false)
                exoPlayer?.let { player ->
                    // Disable all audio tracks so ExoPlayer skips audio renderer on next prepare
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .build()
                    // Re-prepare from IDLE and seek back to the position before the error
                    player.prepare()
                    player.seekTo(savedPosition)
                    player.play()
                }
                return
            }
            // --- end Variant B ---

            val isFormatError = error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED

            // MediaPlayer cannot handle network protocols (smb://, sftp://, ftp://, etc.)
            // setDataSource() with such URIs fails immediately (what=1, extra=Integer.MIN_VALUE).
            // Only attempt fallback for local file:// paths.
            val isLocalPath = currentFilePath?.let { p ->
                !p.startsWith("smb://") && !p.startsWith("sftp://") &&
                    !p.startsWith("ftp://") && !p.startsWith("ftps://") &&
                    !p.startsWith("http://") && !p.startsWith("https://") &&
                    !p.startsWith("gdrive://") && !p.startsWith("onedrive://") &&
                    !p.startsWith("dropbox://")
            } ?: false

            if (isFormatError && currentFilePath != null && !isUsingMediaPlayer && isLocalPath) {
                Timber.i("VideoPlayerManager: ExoPlayer format error, trying MediaPlayer fallback..")
                Handler(Looper.getMainLooper()).post { playWithMediaPlayer(currentFilePath!!) }
                return
            }
            if (isFormatError && currentFilePath != null && !isLocalPath) {
                Timber.w("VideoPlayerManager: ExoPlayer format error on network path — MediaPlayer fallback skipped")
                val lowerPath = currentFilePath!!.lowercase()
                if (lowerPath.endsWith(".m2ts") || lowerPath.endsWith(".m2t")) {
                    Timber.i("VideoPlayerManager: BD-TS format error — showing informative dialog")
                    playerCallback.onBdTsFormatError()
                    return
                }
                if (lowerPath.endsWith(".vob")) {
                    Timber.i("VideoPlayerManager: DVD_PS_VOB route error — stopping cascade on current file")
                    playerCallback.onNetworkContainerRouteError(
                        currentFilePath!!,
                        com.sza.fastmediasorter.ui.player.helpers.NetworkPlaybackContainerHint.DVD_PS_VOB
                    )
                    return
                }
            }

            if (error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT) {
                currentFilePath?.let(VideoPlaybackFailureSessionCache::markFailed)
                val userMessage = currentFilePath
                    ?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() }
                    ?.let { context.getString(R.string.video_playback_failed_with_name, it) }
                    ?: context.getString(R.string.error_loading_media)
                playerCallback.onBuffering(false)
                playerCallback.onPlaybackError(error, userMessage)
                return
            }

            playerCallback.onBuffering(false)
            playerCallback.onPlaybackError(error)
        }

        override fun onRenderedFirstFrame() {
            // S0196: primary-content timing probe — fires when ExoPlayer renders the first video
            // frame to the surface. Phase 04 reads this tag from logcat as the "video firstContent"
            // timestamp. Intentionally placed before early returns so all sources are covered.
            Timber.d("VideoPlayerManager: onRenderedFirstFrame path=${currentFilePath ?: "(null)"}")
            val path = currentFilePath ?: return
            val callback = onFirstFrameReady ?: return
            // Poster extractor only supports local files — skip for network/cloud sources
            if (path.startsWith("smb://") || path.startsWith("sftp://") ||
                path.startsWith("ftp://") || path.startsWith("cloud://")) {
                Timber.d("VideoPlayerManager: Skipping first-frame capture for network/cloud source")
                return
            }
            managerScope.launch(Dispatchers.IO) {
                val isBusy = withContext(Dispatchers.Main) {
                    exoPlayer?.let { it.isPlaying || it.isLoading } == true
                }
                val result = posterExtractor.extract(context, path, isBusy)
                val bitmap = result.bitmap ?: return@launch
                val isPlaceholder = result.source == VideoPosterExtractor.Source.PLACEHOLDER
                if (!isPlaceholder) posterExtractor.rememberDelivered(bitmap)
                withContext(Dispatchers.Main) { callback(bitmap, isPlaceholder) }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val videoFormat = tracks.groups
                .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                ?.getTrackFormat(0)
            // WHY: VR_QUALITY_DEBUG — log selected track to diagnose S0041 pixelization regression.
            // Remove after root cause is confirmed (Phase 2 of S0041 investigation).
            Timber.d("VR_QUALITY_DEBUG: selected track format=%s", videoFormat)
            if (videoFormat != null) {
                val detectionPath = currentFilePath ?: return
                managerScope.launch {
                    // MP4 spatial metadata can live in moov at EOF, so detection cannot block main.
                    val detected = withContext(Dispatchers.IO) {
                        stereoDetector.detectForVideo(detectionPath, videoFormat)
                    }
                    if (detectionPath == currentFilePath && detected != StereoMode.UNKNOWN) {
                        Timber.d("VideoPlayerManager: onTracksChanged → detected stereo=$detected path=$detectionPath")
                        playerCallback.onStereoDetected(detected, detectionPath)
                    }
                }
            }
            val path = currentFilePath ?: return
            val isMts = path.endsWith(".m2ts", ignoreCase = true) || path.endsWith(".m2t", ignoreCase = true)
            if (isMts && audioUnsupportedShownForPath != path) {
                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                if (audioGroups.isNotEmpty()) {
                    val allUnsupported = audioGroups.all { group ->
                        (0 until group.length).none { i -> group.isTrackSupported(i) }
                    }
                    if (allUnsupported) {
                        audioUnsupportedShownForPath = path
                        val codecs = audioGroups
                            .flatMap { group -> (0 until group.length).map { i -> group.getTrackFormat(i).sampleMimeType ?: "?" } }
                            .distinct()
                            .joinToString(", ")
                        val msg = context.getString(R.string.warning_m2ts_audio_unsupported, codecs)
                        Timber.d("S0054: VideoPlayerManager all .m2ts audio tracks unsupported path=$path codecs=$codecs — showing warning toast")
                        Timber.w("VideoPlayerManager: all audio tracks unsupported for $path — codecs=$codecs")
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    } else {
                        // DefaultTrackSelector already skips unsupported tracks; log confirms this.
                        Timber.d("VideoPlayerManager: .m2ts has decodable audio — DefaultTrackSelector handles selection")
                    }
                }
            }
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (videoSize.width <= 0 || videoSize.height <= 0) return
            if (!videoSizeKnown) {
                videoSizeKnown = true
                Timber.d("VideoPlayerManager: onVideoSizeChanged ${videoSize.width}x${videoSize.height} — size known")
                if (pendingEffectsApply) {
                    pendingEffectsApply = false
                    applyConfiguredVideoEffects()
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Init
    // ═══════════════════════════════════════════════════════════════════════

    init {
        lifecycle.addObserver(this)
    }
    // ═══════════════════════════════════════════════════════════════════════
    // Public API — PlayerView
    // ═══════════════════════════════════════════════════════════════════════

    /** Attach the [PlayerView] used for video rendering. Must be called before playback. */
    fun setPlayerView(playerView: PlayerView) {
        currentPlayerView = playerView
        Timber.d("VideoPlayerManager: PlayerView set")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Public API — Stereo / color adjustments
    // ═══════════════════════════════════════════════════════════════════════

    /** Apply stereo crop effect matching [mode], with VR paths deferring per-eye crop to VrStereoRenderer. */
    fun applyStereoEffect(mode: StereoMode) = playbackControlsHelper.applyStereoEffect(mode)

    fun setHueAdjustmentDegrees(hueDegrees: Float) = playbackControlsHelper.setHueAdjustmentDegrees(hueDegrees)

    fun getHueAdjustmentDegrees(): Float = playbackControlsHelper.getHueAdjustmentDegrees()

    fun setBrightnessAdjustment(brightnessAdjustment: Float) =
        playbackControlsHelper.setBrightnessAdjustment(brightnessAdjustment)

    fun getBrightnessAdjustment(): Float = playbackControlsHelper.getBrightnessAdjustment()

    fun setBrightnessProgress(progress: Int) = playbackControlsHelper.setBrightnessProgress(progress)

    fun getBrightnessProgress(): Int = playbackControlsHelper.getBrightnessProgress()

    fun getBrightnessPercentOffset(): Int = playbackControlsHelper.getBrightnessPercentOffset()

    // ═══════════════════════════════════════════════════════════════════════
    // Public API — Playback dispatch
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Start playback for [path], routing to the correct protocol handler based on [resourceType].
     * Restores a previously saved position and starts the auto-save loop after setup.
     */
    fun playVideo(
        path: String,
        resourceType: ResourceType,
        credentialsId: String?,
        playWhenReady: Boolean = true,
        onComplete: () -> Unit = {}
    ) {
        Timber.d("VideoPlayerManager: playVideo - path=$path, type=$resourceType")

        // S0120: establish BASELINE before first media load; endScenario() fires in releasePlayer()
        if (exoPlayer == null) MemoryEnduranceTracker.startScenario("VID-playback")

        // Reset per-file stereo detection before every new video
        playerCallback.onBeforeVideoLoad(path)
        // Reset the panel single-eye toast one-shot for the new media session.
        panelStereoSingleEyeNotifier.resetForNewSession()
        val scenarioTag = scenarioTagFor(path)
        val playbackScenario = if (scenarioTag == "audio") {
            MemoryScenario.AUDIO_PLAYBACK
        } else {
            MemoryScenario.VIDEO_PLAYBACK
        }
        memoryProfileCoordinator.enter(playbackScenario)
        if (playbackScenario == MemoryScenario.AUDIO_PLAYBACK) {
            // Audio playback should not inherit browse thumbnails as warm image memory.
            Glide.get(context).clearMemory()
        }
        // S0207 Phase 01: PRE_PLAY checkpoint — captures native heap right before ExoPlayer starts.
        memoryProbe.record(MemoryCheckpoint.PRE_PLAY, scenarioTag = scenarioTag)

        // Reset effects-deferral gate for the new file — size will be reported
        // again via onVideoSizeChanged once the decoder decodes the first frame.
        videoSizeKnown = false
        pendingEffectsApply = false

        currentFilePath = path
        if (VideoPlaybackFailureSessionCache.hasFailure(path)) {
            // WHY: a previous timeout would otherwise look like a random skip again on retry.
            Toast.makeText(
                context,
                context.getString(R.string.video_playback_failed_session_warning),
                Toast.LENGTH_SHORT
            ).show()
        }
        posterExtractor.reset()
        lastCompletedPath = null
        audioUnsupportedShownForPath = null
        stopPositionSaving()

        // Recreate ExoPlayer periodically to release accumulated native codec/decoder allocations.
        if (exoPlayer != null) {
            trackChangesSinceRecreate++
            val nativeFree = Debug.getNativeHeapFreeSize()
            if (trackChangesSinceRecreate >= PLAYER_RECREATE_INTERVAL || nativeFree < NATIVE_HEAP_RECREATE_THRESHOLD_BYTES) {
                Timber.i("VideoPlayerManager: recreating ExoPlayer — trackChanges=$trackChangesSinceRecreate, nativeFree=${nativeFree / 1024 / 1024}MB")
                releasePlayer()
                trackChangesSinceRecreate = 0
            }
        }

        // WHY S0168 §5.3: VP9/other codec decoders allocate 20–30 MB native at init.
        // When native heap is critically low, buffer allocation stalls immediately → errorCode=1004.
        // Run Glide eviction + GC before ExoPlayer starts to maximise available native memory.
        val nativeFreePrePlay = Debug.getNativeHeapFreeSize()
        if (nativeFreePrePlay < NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES) {
            Timber.w(
                "VideoPlayerManager: native heap low before playback — free=%dMB, running Glide eviction + GC (S0168 §5.3)",
                nativeFreePrePlay / 1024 / 1024
            )
            Glide.get(context).clearMemory()
            Runtime.getRuntime().gc()
            val nativeFreeAfterGc = Debug.getNativeHeapFreeSize()
            if (nativeFreeAfterGc < NATIVE_HEAP_CRITICAL_TOAST_THRESHOLD_BYTES && !lowMemoryToastShownThisSession) {
                lowMemoryToastShownThisSession = true
                Timber.w(
                    "VideoPlayerManager: native heap critically low after GC — free=%dMB — showing toast once this session",
                    nativeFreeAfterGc / 1024 / 1024
                )
                Toast.makeText(context, context.getString(R.string.warning_low_memory_playback), Toast.LENGTH_SHORT).show()
            } else {
                Timber.i(
                    "VideoPlayerManager: native heap after GC — free=%dMB",
                    nativeFreeAfterGc / 1024 / 1024
                )
            }
        }

        managerScope.launch {
            try {
                val savedPosition = playbackPositionRepository.getPosition(path)

                // MIDI requires MediaPlayer — ExoPlayer has no native MIDI decoder.
                // Restrict to LOCAL files: MediaPlayer cannot handle network URIs.
                val isLocalFile = resourceType == ResourceType.LOCAL
                val shouldUseMediaPlayer = isLocalFile && (
                    path.endsWith(".mid", ignoreCase = true) ||
                        path.endsWith(".midi", ignoreCase = true)
                    )

                if (shouldUseMediaPlayer) {
                    Timber.i("VideoPlayerManager: Using MediaPlayer for ${path.substringAfterLast('/')} (MIDI)")
                    withContext(Dispatchers.Main) { playWithMediaPlayer(path) }
                    onComplete()
                    return@launch
                }

                when (resourceType) {
                    ResourceType.CLOUD -> playCloudVideo(path, playWhenReady)
                    ResourceType.SMB -> playSmbVideo(path, credentialsId, playWhenReady)
                    ResourceType.SFTP -> playSftpVideo(path, credentialsId, playWhenReady)
                    ResourceType.FTP -> playFtpVideo(path, credentialsId, playWhenReady)
                    ResourceType.LOCAL -> playLocalVideoInternal(path, playWhenReady)
                }

                if (savedPosition != null && savedPosition > 0 && !isUsingMediaPlayer) {
                    withContext(Dispatchers.Main) {
                        exoPlayer?.seekTo(savedPosition)
                        Timber.d("VideoPlayerManager: Restored playback position: ${savedPosition}ms")
                        Toast.makeText(
                            context,
                            context.getString(R.string.playback_resumed_from, formatTime(savedPosition)),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                startPositionSaving()
                onComplete()
            } catch (e: CancellationException) {
                // Lifecycle/scope cancel (activity destroy, file switch, player release) — not a playback failure.
                Timber.d("VideoPlayerManager: playVideo cancelled (lifecycle/scope cancel) — path=%s", path)
                playerCallback.onBuffering(false)
                throw e
            } catch (e: Exception) {
                Timber.e(e, "VideoPlayerManager: Failed to play video")
                playerCallback.onBuffering(false)
                playerCallback.showError(context.getString(R.string.error_playback_failed))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Public API — Player controls
    // ═══════════════════════════════════════════════════════════════════════

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun setRepeatMode(repeatMode: Int) = playbackControlsHelper.setRepeatMode(repeatMode)

    fun pause() = playbackControlsHelper.pause()

    fun play() = playbackControlsHelper.play()

    fun setPlaybackSpeed(speed: Float) = playbackControlsHelper.setPlaybackSpeed(speed)

    /** Apply [PlayerSettingsDialog.PlayerSettings] to the active ExoPlayer instance. */
    fun applyPlayerSettings(settings: PlayerSettingsDialog.PlayerSettings, appLanguage: String) =
        playbackControlsHelper.applyPlayerSettings(settings, appLanguage)

    // ═══════════════════════════════════════════════════════════════════════
    // Public API — Track selection (delegated to VideoTrackSelectionManager)
    // ═══════════════════════════════════════════════════════════════════════

    /** Track info for display in quick-switcher dialogs. */
    data class TrackInfo(
        val groupIndex: Int,
        val trackIndex: Int,
        val label: String,
        val isSelected: Boolean
    )

    fun applySubtitleStyle(fontSize: TranslationFontSize, fontFamily: TranslationFontFamily) =
        playbackControlsHelper.applySubtitleStyle(fontSize, fontFamily)

    fun getAvailableAudioTracks(): List<TrackInfo> = playbackControlsHelper.getAvailableAudioTracks()

    fun getAvailableSubtitleTracks(): List<TrackInfo> = playbackControlsHelper.getAvailableSubtitleTracks()

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) =
        playbackControlsHelper.selectAudioTrack(groupIndex, trackIndex)

    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) =
        playbackControlsHelper.selectSubtitleTrack(groupIndex, trackIndex)

    fun hasMultipleAudioTracks(): Boolean = playbackControlsHelper.hasMultipleAudioTracks()

    fun hasSubtitleTracks(): Boolean = playbackControlsHelper.hasSubtitleTracks()

    // ═══════════════════════════════════════════════════════════════════════
    // Audio format info
    // ═══════════════════════════════════════════════════════════════════════

    fun getAudioFormat(): AudioFormat? = playbackControlsHelper.getAudioFormat()

    // ═══════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═══════════════════════════════════════════════════════════════════════

    /** Re-prepare and resume from [lastPlaybackPosition] after an EOF retry. */
    private fun retryPlayback() {
        exoPlayer?.seekTo(lastPlaybackPosition)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }

    /** Release ExoPlayer and cancel all pending callbacks / throttle modes. */
    fun releasePlayer() = lifecycleHelper.releasePlayer()

    // ═══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    override fun onPause(owner: LifecycleOwner) = lifecycleHelper.onPause()

    override fun onResume(owner: LifecycleOwner) = lifecycleHelper.onResume()

    override fun onDestroy(owner: LifecycleOwner) {
        memoryProfileCoordinator.enter(MemoryScenario.IDLE)
        lifecycleHelper.onDestroy()
    }

    // S0207 Phase 01: simple audio/video classifier for MEM_PROBE scenarioTag.
    // The full scenario taxonomy is introduced by Phase 03 (memory-profile-abstraction);
    // until then this two-bucket split is enough to filter audio-only sessions in logs.
    private fun scenarioTagFor(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext in MediaTypeUtils.AUDIO_EXTENSIONS) "audio" else "video"
    }
}