package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.VideoPlaybackFailureSessionCache
import com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker
import com.sza.fastmediasorter.core.memory.MemoryCheckpoint
import com.sza.fastmediasorter.core.memory.MemoryProbe
import com.sza.fastmediasorter.core.memory.MemoryProfileCoordinator
import com.sza.fastmediasorter.core.memory.MemoryScenario
import com.sza.fastmediasorter.core.playback.RecentDecoderFailureTracker
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpEndpointResolver
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.models.TranslationFontFamily
import com.sza.fastmediasorter.domain.models.TranslationFontSize
import com.sza.fastmediasorter.domain.player.StreamProtocolSupport
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.stats.ViewKind
import com.sza.fastmediasorter.domain.streams.StreamFrameIngestor
import com.sza.fastmediasorter.domain.usecase.streams.StreamTrackPreferenceUseCase
import com.sza.fastmediasorter.ui.dialog.PlayerSettingsDialog
import com.sza.fastmediasorter.ui.player.helpers.PanelStereoSingleEyeNotifier
import com.sza.fastmediasorter.ui.player.helpers.applyConfiguredVideoEffects
import com.sza.fastmediasorter.ui.player.helpers.brightnessAdjustmentToProgress
import com.sza.fastmediasorter.ui.player.helpers.brightnessProgressToAdjustment
import com.sza.fastmediasorter.ui.player.helpers.cancelPlaybackHealthCheck
import com.sza.fastmediasorter.ui.player.helpers.formatTime
import com.sza.fastmediasorter.ui.player.helpers.playCloudVideo
import com.sza.fastmediasorter.ui.player.helpers.playFtpVideo
import com.sza.fastmediasorter.ui.player.helpers.playLocalVideoInternal
import com.sza.fastmediasorter.ui.player.helpers.playSftpVideo
import com.sza.fastmediasorter.ui.player.helpers.playSmbVideo
import com.sza.fastmediasorter.ui.player.helpers.playStreamVideo
import com.sza.fastmediasorter.ui.player.helpers.resetStreamFrameCapture
import com.sza.fastmediasorter.ui.player.helpers.startPlaybackHealthCheck
import com.sza.fastmediasorter.ui.player.helpers.startPositionSaving
import com.sza.fastmediasorter.ui.player.helpers.stopPositionSaving
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.LazyThreadSafetyMode
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
 * - [Player.Listener] (playerListener) - tightly coupled to all state, cannot be split cleanly
 * - [playVideo] dispatch (routes to protocol-specific extension)
 * - Public API surface (play/pause/seek/track selection)
 * - Lifecycle callbacks
 */
class VideoPlayerManager(
    hostDependencies: VideoPlayerHostDependencies,
    networkDependencies: VideoPlayerNetworkDependencies,
    storeDependencies: VideoPlayerStoreDependencies,
) : DefaultLifecycleObserver {

    internal val context: Context = hostDependencies.context
    private val lifecycle: Lifecycle = hostDependencies.lifecycle
    internal val playerCallback: PlayerCallback = hostDependencies.playerCallback
    internal val panelStereoSingleEyeNotifier: PanelStereoSingleEyeNotifier =
        hostDependencies.panelStereoSingleEyeNotifier
    internal val memoryProbe: MemoryProbe = hostDependencies.memoryProbe
    internal val memoryProfileCoordinator: MemoryProfileCoordinator = hostDependencies.memoryProfileCoordinator
    internal val decoderFailureTracker: RecentDecoderFailureTracker = hostDependencies.decoderFailureTracker
    internal val remoteSourceGate = hostDependencies.remoteSourceGate
    internal val statsSink: StatsSink = hostDependencies.statsSink
    internal val streamProtocolSupport: StreamProtocolSupport = hostDependencies.streamProtocolSupport

    internal val credentialsRepository: NetworkCredentialsRepository = networkDependencies.credentialsRepository
    internal val smbClient: SmbClient = networkDependencies.smbClient
    internal val sftpClient: SftpClient = networkDependencies.sftpClient
    internal val endpointResolver: SftpEndpointResolver = networkDependencies.endpointResolver
    internal val ftpClient: FtpClient = networkDependencies.ftpClient
    internal val googleDriveClient: GoogleDriveRestClient = networkDependencies.googleDriveClient
    internal val oneDriveClient: OneDriveRestClient = networkDependencies.oneDriveClient
    internal val dropboxClient: DropboxClient = networkDependencies.dropboxClient

    internal val playbackPositionRepository: PlaybackPositionRepository =
        storeDependencies.playbackPositionRepository
    internal val settingsRepository: SettingsRepository = storeDependencies.settingsRepository

    // S1144 (ADR-6): read by the stream-start path to overlay the channel's remembered track languages.
    internal val streamTrackPreferenceUseCase: StreamTrackPreferenceUseCase =
        storeDependencies.streamTrackPreferenceUseCase

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

        /**
         * S0685: stream-only wait-phase hint shown next to the buffering spinner so the user can tell a
         * normal buffer fill from an active reconnection. `null` clears the label. Default no-op - only the
         * full player UI renders it; other [PlayerCallback] impls keep the plain spinner.
         */
        fun onStreamWaitPhase(phase: StreamWaitPhase?) {}

        /**
         * S1158: name of the programme currently on air, taken from the stream's ICY metadata. `null`
         * clears the caption. Default no-op - only the full player UI renders it, like
         * [onStreamWaitPhase].
         */
        fun onStreamProgramName(name: String?) {}
    }

    /**
     * S0685: distinguishes the two stream wait phases the user sees as a spinner. [BUFFERING] is a routine
     * buffer fill; [RECONNECTING] means the S0634 recovery path (live-edge re-anchor or classified retry)
     * is actively re-establishing the stream.
     */
    enum class StreamWaitPhase { BUFFERING, RECONNECTING }

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
        // Buffer configuration (ms) - default for SMB/SFTP/FTP.
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

        // Audio playback uses a smaller buffer than video - codec/decoder allocations are
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

        internal const val DEFAULT_BRIGHTNESS_PROGRESS = 50

        // Playback health-check - thresholds for detecting stuck / white-noise audio
        internal const val PLAYBACK_HEALTH_CHECK_DELAY_MS = 2_000L
        internal const val MAX_PLAYBACK_STUCK_COUNT = 2
    }
    // ═══════════════════════════════════════════════════════════════════════
    // Mutable state - internal so extension functions in helpers/ can access
    // ═══════════════════════════════════════════════════════════════════════

    internal var exoPlayer: ExoPlayer? = null
    internal var mediaPlayer: MediaPlayer? = null
    internal var currentPlayerView: PlayerView? = null
    internal var isUsingMediaPlayer = false

    private val playbackControlPrefs =
        context.getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, Context.MODE_PRIVATE)

    // Stereo detection runs once per video load inside onTracksChanged.
    // S0274 Wave 01: widened to internal so VideoPlayerTracksObserver can call detectForVideo().
    internal val stereoDetector = StereoDetector()

    // S0032: resilient poster-frame extractor with preventive heap/decoder guards
    // and (in phase 02) a fallback hierarchy to Glide memory cache / placeholder.
    // S0274 Wave 01: widened to internal so VideoPlaybackPreflightHelper can call reset().
    internal val posterExtractor = VideoPosterExtractor()

    // Stereo GL effect builder - Phase 2: builds Crop effects for ExoPlayer.setVideoEffects()
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

    // S1144: internal so the stream-start extension in StreamPlaybackHelper can seat the channel
    // preference on it before prepare().
    internal val trackSelectionManager = VideoTrackSelectionManager(
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

    // S0274 Wave 01: error classification ladder extracted into a dedicated helper.
    private val errorHandler by lazy(LazyThreadSafetyMode.NONE) {
        com.sza.fastmediasorter.ui.player.helpers.VideoPlayerErrorHandler(this)
    }

    // S0274 Wave 01: per-file pre-flight pipeline extracted into a dedicated helper.
    private val preflightHelper by lazy(LazyThreadSafetyMode.NONE) {
        com.sza.fastmediasorter.ui.player.helpers.VideoPlaybackPreflightHelper(this)
    }

    // S0274 Wave 01: onTracksChanged body extracted into a dedicated helper.
    private val tracksObserver by lazy(LazyThreadSafetyMode.NONE) {
        com.sza.fastmediasorter.ui.player.helpers.VideoPlayerTracksObserver(this)
    }

    // Retry logic for EOF exceptions - S0274 Wave 01: widened to internal so the extracted
    // VideoPlayerErrorHandler can drive the retry state without re-exporting the field through
    // a setter on every error.
    internal var playbackRetryCount = 0
    internal var lastPlaybackPosition = 0L

    // Last position captured while the player was genuinely playing (not a mid-seek artifact).
    // Used to recover playback in place when a seek crashes the extractor (e.g. AVI with an
    // empty/absent index), so a failed seek resumes near the prior position instead of skipping
    // the file. Updated by the position auto-save loop only when the player is actively playing.
    internal var lastGoodPositionMs = 0L
    internal val retryHandler = Handler(Looper.getMainLooper())
    internal var retryRunnable: Runnable? = null

    // Wall-clock accumulator for "player time": counts time the video player is on screen with media
    // loaded, including paused/buffering, and excluding time spent in the background. elapsedRealtime
    // is monotonic so wall-clock changes can't corrupt it. 0 = not running. Banked as
    // StatsEvent.PlaybackTime on pause/release/destroy.
    internal var watchClockStartMs = 0L

    // Playback position saving
    internal var currentFilePath: String? = null
    internal var positionSaveLoop: com.sza.fastmediasorter.ui.player.helpers.PositionSaveLoop? = null

    // S0029: idempotency guard for STATE_ENDED → markPlaybackCompleted.
    // Reset on every load of a new media file so re-opening the same path re-arms the path.
    // S0274 Wave 01: widened so VideoPlaybackPreflightHelper can reset it.
    @Volatile
    var lastCompletedPath: String? = null

    // Guards the one-shot audio-unsupported toast; reset per file load in playVideo().
    // S0274 Wave 01: widened so VideoPlaybackPreflightHelper can reset it.
    @Volatile var audioUnsupportedShownForPath: String? = null

    // Playback health monitoring (detect "white noise" / stuck playback)
    internal var playbackHealthCheckRunnable: Runnable? = null
    internal var lastCheckedPosition = 0L
    internal var playbackStuckCount = 0

    // Stream stall watchdog (S0936): detects a silent freeze the stream-listener's error-driven
    // recovery cannot see (no PlaybackException thrown) and heals it with a bounded re-prepare.
    internal var streamStallRunnable: Runnable? = null
    internal var streamStallLastPosition = 0L
    internal var streamStallLastRenderedFrames: Int? = null
    internal var streamStallPolls = 0
    internal var streamBufferingSince = 0L

    // Watchdog recovery budget - separate from the error-driven behindLiveRecoveries/transientRetries
    // in streamPlaybackListener, so a stall storm and an error storm cannot mask each other's
    // exhaustion. Attempts expire as a group after stable playback rather than being refilled by READY.
    internal val streamWatchdogRecoveryWindow =
        com.sza.fastmediasorter.ui.player.helpers.StreamStallRecoveryWindow()

    // True while a watchdog-triggered re-prepare is in flight, so the listener labels the resulting
    // BUFFERING as RECONNECTING (owner-ratified: same label as error-driven recovery).
    internal var streamWatchdogReconnecting = false

    // Connection throttling - resource key of the currently streaming server
    internal var activeResourceKey: String? = null

    @Volatile internal var activeSourceIsStream: Boolean = false

    // S0893: the one extra Player.Listener a playback session adds beyond playerListener - either
    // PauseAwareLoadControl (local/cloud/ftp/sftp/smb) or the per-stream listener (StreamPlaybackHelper).
    // Tracked here because every add site builds it as a local val; without a field, releasePlayer()/
    // onDestroy() have no reference to remove it symmetrically.
    internal var activeExtraPlayerListener: Player.Listener? = null

    // S1127: the stream player's AnalyticsListener (dropped frames / decoder / TTFF / stall metrics) and
    // its aggregator, tracked so both teardown paths remove the listener symmetrically and log the summary.
    internal var activeStreamAnalyticsListener:
        com.sza.fastmediasorter.ui.player.helpers.StreamDiagnosticsAnalyticsListener? = null
    internal var activeStreamDiagnostics: com.sza.fastmediasorter.ui.player.helpers.StreamPlaybackDiagnostics? = null

    // S1510: the periodic counterpart of the listener above. Held here for the same reason: it captures
    // the session's player and bandwidth meter, so teardown needs a reference to stop it on both paths.
    internal var activeStreamStatsSampler: com.sza.fastmediasorter.ui.player.helpers.StreamStatsSampler? = null

    // S1128: the http(s) stream player's explicit track selector and the quality step-down policy, held so
    // the stream listener can cap the video ceiling on repeated stalls and teardown can null both. RTSP
    // keeps the implicit default selector (no rendition ladder), so both stay null for RTSP sessions.
    internal var activeStreamTrackSelector:
        androidx.media3.exoplayer.trackselection.DefaultTrackSelector? = null
    internal var activeStreamStepDownController:
        com.sza.fastmediasorter.ui.player.helpers.StreamQualityStepDownController? = null

    // S1129: one delayed TextureView capture attempt belongs to one active stream session.
    internal var streamFrameCaptureJob: Job? = null
    internal var streamFrameCaptureAttempted = false
    internal var streamFrameIngestor: StreamFrameIngestor? = null
    internal var onStreamFrameIngested: ((String) -> Unit)? = null

    // S0893: minimal state to recreate playback after an API24+ onStop release. Set at the top of
    // playVideo() so onStart() can call playVideo(..) again with the same routing.
    internal var lastResourceType: ResourceType? = null
    internal var lastCredentialsId: String? = null

    // Counts track changes on the current ExoPlayer instance; triggers recreation at PLAYER_RECREATE_INTERVAL.
    // S0274 Wave 01: widened so VideoPlaybackPreflightHelper can drive the counter.
    var trackChangesSinceRecreate = 0

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

    // Panel single-eye crop flag - see spec_panel-stereo-single-eye.md.
    // Default true; overridden as soon as the first DataStore emission arrives below.
    @Volatile
    internal var panelStereoSingleEyeEnabled: Boolean = true

    // S0326: global 3D/VR detection config (master + source-trust flags). Default ALL_ENABLED
    // (legacy behavior) until the first DataStore emission arrives in init below.
    @Volatile
    internal var stereoDetectionConfig: StereoDetectionConfig = StereoDetectionConfig.ALL_ENABLED

    // Override: when the VR flavor enters immersive rendering, the immersive renderer
    // owns per-eye crop; suppress panel single-eye crop here to avoid double-cropping.
    // Toggled by the VR flavor via setVrImmersiveActive().
    @Volatile
    internal var vrImmersiveActive: Boolean = false

    /**
     * Set by the VR flavor when its immersive render loop becomes active or inactive.
     * While active, panel single-eye crop is suppressed regardless of the user setting.
     */
    fun setVrImmersiveActive(active: Boolean) {
        if (vrImmersiveActive == active) return
        vrImmersiveActive = active
        Timber.d("VideoPlayerManager: vrImmersiveActive=$active - re-applying video effects")
        // Re-apply on the main thread to honour the toggle for the currently-loaded media.
        applyConfiguredVideoEffects()
        // S0264: immersive transition flips panel single-eye crop on/off; sync TextureView matrix.
        com.sza.fastmediasorter.ui.player.helpers.PanelStereoCropApplier.apply(
            playerView = currentPlayerView,
            mode = stereoVideoProcessor.getCurrentMode(),
            singleEyeEnabled = panelStereoSingleEyeEnabled && !vrImmersiveActive,
        )
    }

    init {
        settingsRepository.getSettings()
            .map { it.panelStereoSingleEye }
            .distinctUntilChanged()
            .onEach { enabled ->
                panelStereoSingleEyeEnabled = enabled
                Timber.d("VideoPlayerManager: panelStereoSingleEye=$enabled - re-applying video effects")
                applyConfiguredVideoEffects()
                // S0264: user toggled single-eye crop on/off mid-playback - push the matrix.
                com.sza.fastmediasorter.ui.player.helpers.PanelStereoCropApplier.apply(
                    playerView = currentPlayerView,
                    mode = stereoVideoProcessor.getCurrentMode(),
                    singleEyeEnabled = enabled && !vrImmersiveActive,
                )
            }
            .launchIn(managerScope)

        // S0326: keep the 3D/VR detection config in sync with user settings.
        settingsRepository.getSettings()
            .map { StereoDetectionConfig.from(it) }
            .distinctUntilChanged()
            .onEach { config ->
                stereoDetectionConfig = config
            }
            .launchIn(managerScope)
    }

    /**
     * Optional callback invoked with the first decoded video frame bitmap.
     * Only fires for local files - network/cloud paths are short-circuited inside
     * [onRenderedFirstFrame] because the underlying retriever has no streaming support.
     * The Boolean flag is true when the bitmap is a static placeholder (S0032 fallback).
     */
    var onFirstFrameReady: ((android.graphics.Bitmap, Boolean) -> Unit)? = null

    /** Callback invoked after each periodic position save (every 5 s). */
    var onPositionSaved: (() -> Unit)? = null

    /**
     * Invoked on the main thread after every fresh ExoPlayer instance is created.
     * Used by the VR flavor to flush a pending VR surface redirect when the VR
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

    // S0995: decoded video dimensions from onVideoSizeChanged; used to refit the frame at 90/270.
    @Volatile internal var lastVideoWidth: Int = 0

    @Volatile internal var lastVideoHeight: Int = 0

    // S0995: cumulative visual frame rotation (0/90/180/270) composed into the effect chain. Lives on
    // the manager (not the per-file ExoPlayer) so the angle carries to the next video in the session.
    @Volatile internal var contentRotationDegrees: Int = 0

    // Debounce handler for applyConfiguredVideoEffects() - see PlayerSetupHelper.kt.
    // 80 ms window coalesces rapid slider drags into a single pipeline rebuild,
    // preventing TexturePool race crash in Media3 1.2.x.
    internal val effectsHandler = Handler(Looper.getMainLooper())
    internal var pendingEffectsRunnable: Runnable? = null
    // ═══════════════════════════════════════════════════════════════════════
    // Player.Listener - stays in orchestrator (tightly coupled to all state)
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
                    startWatchClock()
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
                        // S0473: one video watched to the end. Watch time is accrued separately via
                        // the watch clock (StatsEvent.PlaybackTime), so this only bumps the count and
                        // must not also report a duration or the player time would be double-counted.
                        statsSink.record(StatsEvent.View(ViewKind.VIDEO))
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
            if (errorHandler.handlePlayerError(error)) return
            playerCallback.onBuffering(false)
            playerCallback.onPlaybackError(error)
        }

        override fun onRenderedFirstFrame() {
            // S0196: primary-content timing probe - fires when ExoPlayer renders the first video
            // frame to the surface. Phase 04 reads this tag from logcat as the "video firstContent"
            // timestamp. Intentionally placed before early returns so all sources are covered.
            Timber.d("VideoPlayerManager: onRenderedFirstFrame path=${currentFilePath ?: "(null)"}")
            val path = currentFilePath ?: return
            val callback = onFirstFrameReady ?: return
            // Poster extractor only supports local files - skip for network/cloud sources
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
            tracksObserver.onTracksChanged(tracks)
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (videoSize.width <= 0 || videoSize.height <= 0) return
            // S0995: remember decoded dims so the rotation effect can refit the frame at 90/270.
            lastVideoWidth = videoSize.width
            lastVideoHeight = videoSize.height
            if (!videoSizeKnown) {
                videoSizeKnown = true
                Timber.d("VideoPlayerManager: onVideoSizeChanged ${videoSize.width}x${videoSize.height} - size known")
                if (pendingEffectsApply) {
                    pendingEffectsApply = false
                    applyConfiguredVideoEffects()
                }
                // S0264: reapply single-eye TextureView matrix now that the surface is
                // sized to the new media. Without this, the crop set in applyStereoEffect
                // before the first decoded frame would be reset by PlayerView's own
                // applyTextureViewRotation on layout.
                com.sza.fastmediasorter.ui.player.helpers.PanelStereoCropApplier.apply(
                    playerView = currentPlayerView,
                    mode = stereoVideoProcessor.getCurrentMode(),
                    singleEyeEnabled = panelStereoSingleEyeEnabled && !vrImmersiveActive,
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ═══════════════════════════════════════════════════════════════════════

    init {
        lifecycle.addObserver(this)
    }
    // ═══════════════════════════════════════════════════════════════════════
    // Public API - PlayerView
    // ═══════════════════════════════════════════════════════════════════════

    /** Attach the [PlayerView] used for video rendering. Must be called before playback. */
    fun setPlayerView(playerView: PlayerView) {
        currentPlayerView = playerView
        Timber.d("VideoPlayerManager: PlayerView set")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Public API - Stereo / color adjustments
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Apply stereo crop matching [mode]. Under VR immersive the 2D crop is skipped (full SBS/OU frame
     * passed through) and per-eye crop is done by the vr-flavor OpenXR renderer - DiagnosticXrRuntime
     * over the native xr_session (per-eye swapchains).
     */
    fun applyStereoEffect(mode: StereoMode) = playbackControlsHelper.applyStereoEffect(mode)

    fun setHueAdjustmentDegrees(hueDegrees: Float) = playbackControlsHelper.setHueAdjustmentDegrees(hueDegrees)

    fun getHueAdjustmentDegrees(): Float = playbackControlsHelper.getHueAdjustmentDegrees()

    fun setBrightnessAdjustment(brightnessAdjustment: Float) =
        playbackControlsHelper.setBrightnessAdjustment(brightnessAdjustment)

    fun getBrightnessAdjustment(): Float = playbackControlsHelper.getBrightnessAdjustment()

    fun setBrightnessProgress(progress: Int) = playbackControlsHelper.setBrightnessProgress(progress)

    fun getBrightnessProgress(): Int = playbackControlsHelper.getBrightnessProgress()

    fun getBrightnessPercentOffset(): Int = playbackControlsHelper.getBrightnessPercentOffset()

    /**
     * S0995: set the pure-visual clockwise frame rotation and re-compose the effect chain. Idempotent
     * on an unchanged angle. The 80ms-debounce / defer-until-videoSizeKnown / drain-before-release
     * Media3 1.2.1 workarounds inside [applyConfiguredVideoEffects] cover the rotation effect too.
     */
    fun setContentRotationDegrees(degrees: Int) {
        if (contentRotationDegrees == degrees) return
        contentRotationDegrees = degrees
        applyConfiguredVideoEffects()
    }

    fun getContentRotationDegrees(): Int = contentRotationDegrees

    // ═══════════════════════════════════════════════════════════════════════
    // Public API - Playback dispatch
    // ═══════════════════════════════════════════════════════════════════════

    // S0854: tracks the in-flight playVideo() coroutine. Cancelling it at the top of a new call
    // serializes playback dispatch - without this, a rapid re-call (fast file switch) while the
    // previous coroutine is still suspended (position lookup, network TS-probe) let both coroutines
    // finish and interleave: the second startPositionSaving() call orphaned the first save loop
    // (P0, retains PlayerActivity) and the same race let a second player be assigned before the
    // first was released (S0865).
    private var activeLoadJob: Job? = null

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
        // S1158: every new file and every new channel passes through here, so this is the one point
        // where the previously announced programme name is guaranteed to be stale.
        playerCallback.onStreamProgramName(null)
        // S1144: same reasoning for the remembered channel track languages - the stream-start path
        // re-seats them, so anything left here belongs to the previous item.
        trackSelectionManager.channelPreference = null
        trackSelectionManager.streamDefaults = null
        // S0893: remembered so onStart() can recreate playback after an API24+ onStop release.
        lastResourceType = resourceType
        lastCredentialsId = credentialsId

        // S0120: establish BASELINE before first media load; endScenario() fires in releasePlayer()
        if (exoPlayer == null) MemoryEnduranceTracker.startScenario("VID-playback")

        // S0274 Wave 01: per-file pre-flight pipeline lives in VideoPlaybackPreflightHelper.
        preflightHelper.runPreflight(path, resourceType)

        // S0854: cancel any in-flight load before starting a new one - see activeLoadJob KDoc.
        activeLoadJob?.cancel()
        activeLoadJob = managerScope.launch {
            try {
                val savedPosition = playbackPositionRepository.getPosition(path)

                // S0391: refuse playback for a user-disabled source. LOCAL always proceeds; CLOUD
                // checks the cloud group; SMB/SFTP/FTP map to their RemoteSourceId. managerScope runs
                // on Dispatchers.Main, so onPlaybackError is already delivered on the main thread.
                val sourceEnabled = when (resourceType) {
                    ResourceType.LOCAL -> true
                    ResourceType.CLOUD -> remoteSourceGate.anyCloudEnabled()
                    else -> com.sza.fastmediasorter.core.capability.RemoteSourceId
                        .networkFromResourceType(resourceType)
                        ?.let { remoteSourceGate.isEnabled(it) } ?: true
                }
                if (!sourceEnabled) {
                    Timber.w("VideoPlayerManager: playback refused - source disabled, type=%s path=%s", resourceType, path)
                    playerCallback.onPlaybackError(
                        IllegalStateException("source disabled"),
                        context.getString(R.string.error_resource_unavailable, path.substringAfterLast('/')),
                    )
                    return@launch
                }

                when (resourceType) {
                    ResourceType.CLOUD -> playCloudVideo(path, playWhenReady)
                    ResourceType.SMB -> playSmbVideo(path, credentialsId, playWhenReady)
                    ResourceType.SFTP -> playSftpVideo(path, credentialsId, playWhenReady)
                    ResourceType.FTP -> playFtpVideo(path, credentialsId, playWhenReady)
                    ResourceType.LOCAL -> playLocalVideoInternal(path, playWhenReady)
                    ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> playStreamVideo(path, playWhenReady)
                }

                activeSourceIsStream = resourceType == ResourceType.HTTP_STREAM ||
                    resourceType == ResourceType.RTSP_STREAM

                // S0565: a live/dynamic stream has no meaningful saved position (C.TIME_UNSET), so
                // both restore and the auto-save loop are suppressed for stream resource types or any
                // dynamic timeline - otherwise an unset position would be persisted and restored.
                val isDynamicStream = resourceType == ResourceType.HTTP_STREAM ||
                    resourceType == ResourceType.RTSP_STREAM ||
                    exoPlayer?.isCurrentMediaItemDynamic == true

                if (savedPosition != null && savedPosition > 0 && !isUsingMediaPlayer && !isDynamicStream) {
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

                if (!isDynamicStream) startPositionSaving()
                onComplete()
            } catch (e: CancellationException) {
                // Lifecycle/scope cancel (activity destroy, file switch, player release) - not a playback failure.
                Timber.d("VideoPlayerManager: playVideo cancelled (lifecycle/scope cancel) - path=%s", path)
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
    // Public API - Player controls
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
    // Public API - Track selection (delegated to VideoTrackSelectionManager)
    // ═══════════════════════════════════════════════════════════════════════

    /** Track info for display in quick-switcher dialogs. */
    data class TrackInfo(
        val groupIndex: Int,
        val trackIndex: Int,
        val label: String,
        val isSelected: Boolean,
        // S1144 (ADR-8): mirrors VideoTrackSelectionManager.TrackInfo. The two classes are separate by
        // history; a field added to one but not the other silently breaks the mappers between them.
        val language: String? = null
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

    /** Re-prepare and resume from [lastPlaybackPosition] after an EOF retry. S0274 Wave 01: widened to internal so [com.sza.fastmediasorter.ui.player.helpers.VideoPlayerErrorHandler] can drive it. */
    internal fun retryPlayback() {
        exoPlayer?.seekTo(lastPlaybackPosition)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }

    /** Release ExoPlayer and cancel all pending callbacks / throttle modes. */
    fun releasePlayer() {
        resetStreamFrameCapture()
        activeSourceIsStream = false
        lifecycleHelper.releasePlayer()
    }

    /**
     * S0865: belt-and-braces guard against the duplicate-player race - a concurrent playVideo()
     * call may have raced through releasePlayer() + assignment while this coroutine was
     * suspended on a network TS-probe. Call immediately before assigning a freshly-built
     * ExoPlayer so a still-live player from that race gets released instead of orphaned.
     */
    internal fun releaseIfRacedPlayer() {
        if (exoPlayer != null) {
            Timber.w("VideoPlayerManager: duplicate-player race detected post-suspend - releasing stale player")
            releasePlayer()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Player-time accounting (S0473 follow-up)
    // ═══════════════════════════════════════════════════════════════════════

    /** Start the watch clock if a media player is loaded and the clock is not already running. */
    internal fun startWatchClock() {
        if (watchClockStartMs == 0L && exoPlayer != null) {
            watchClockStartMs = SystemClock.elapsedRealtime()
        }
    }

    /** Bank the elapsed watch time as a video PlaybackTime delta and stop the clock. */
    internal fun flushWatchClock() {
        val start = watchClockStartMs
        if (start == 0L) return
        watchClockStartMs = 0L
        val elapsed = SystemClock.elapsedRealtime() - start
        if (elapsed > 0L) {
            statsSink.record(StatsEvent.PlaybackTime(ViewKind.VIDEO, elapsed))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    override fun onPause(owner: LifecycleOwner) = lifecycleHelper.onPause()

    override fun onResume(owner: LifecycleOwner) = lifecycleHelper.onResume()

    override fun onStop(owner: LifecycleOwner) = lifecycleHelper.onStop()

    override fun onStart(owner: LifecycleOwner) = lifecycleHelper.onStart()

    override fun onDestroy(owner: LifecycleOwner) {
        memoryProfileCoordinator.enter(MemoryScenario.IDLE)
        lifecycleHelper.onDestroy()
    }

    // S0207 Phase 01: simple audio/video classifier for MEM_PROBE scenarioTag.
    // The full scenario taxonomy is introduced by Phase 03 (memory-profile-abstraction);
    // until then this two-bucket split is enough to filter audio-only sessions in logs.
    // S0274 Wave 01: widened so VideoPlaybackPreflightHelper can derive the tag.
    fun scenarioTagFor(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext in MediaTypeUtils.AUDIO_EXTENSIONS) "audio" else "video"
    }
}

internal fun VideoPlayerManager.isActiveSourceLive(): Boolean = exoPlayer?.isCurrentMediaItemLive == true
