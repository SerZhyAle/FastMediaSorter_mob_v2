package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.github.chrisbanes.photoview.OnSingleFlingListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.MidiPlaybackPolicy
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.ui.player.PlaybackControlPreferences
import com.sza.fastmediasorter.ui.player.VideoColorProcessor
import dagger.Lazy
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Routes media files received from external intents to the appropriate viewer.
 *
 * Standalone constraints (no resource DB, no playlists, no history):
 *   IMAGE/GIF → Glide into photoView
 *   VIDEO     → ExoPlayer into playerView
 *   AUDIO     → AudioServiceController → AudioPlaybackService → playerView
 *   PDF       → PdfViewerManager
 *   EPUB      → EpubViewerManager
 *   TEXT      → TextViewerManager
 *
 * NetworkFileManager is created with injected network clients but will not be
 * exercised for local/content-URI files (which is all standalone ever receives).
 */
class StandaloneViewManager(
    private val activity: Activity,
    private val root: View,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val smbClient: Lazy<SmbClient>,
    private val sftpClient: Lazy<SftpClient>,
    private val ftpClient: Lazy<FtpClient>,
    private val googleDriveClient: Lazy<GoogleDriveRestClient>,
    private val dropboxClient: Lazy<DropboxClient>,
    private val oneDriveClient: Lazy<OneDriveRestClient>,
    private val credentialsRepository: Lazy<NetworkCredentialsRepository>,
    private val smbFileOperationHandler: Lazy<SmbFileOperationHandler>,
    private val sftpFileOperationHandler: Lazy<SftpFileOperationHandler>,
    private val ftpFileOperationHandler: Lazy<FtpFileOperationHandler>,
    private val cloudFileOperationHandler: Lazy<CloudFileOperationHandler>,
    private val unifiedCache: Lazy<UnifiedFileCache>,
    private val settingsRepository: SettingsRepository,
    private val playbackPositionRepository: PlaybackPositionRepository
) {

    companion object {
        private const val DEFAULT_BRIGHTNESS_PROGRESS = 50
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
    }

    // S0380: root-based view seam for every media path (image/gif/video/audio/pdf/epub/office).
    // Works on the full unified layout and on a trimmed specialized standalone layout (id lookup).
    private val safeViews = PlayerBindingSafeViews(root)

    private var onDocumentEnterFullscreen: (() -> Unit)? = null
    private var onDocumentExitFullscreen: (() -> Unit)? = null

    fun setFullscreenCallbacks(onEnter: () -> Unit, onExit: () -> Unit) {
        onDocumentEnterFullscreen = onEnter
        onDocumentExitFullscreen = onExit
    }

    private val networkFileManager: NetworkFileManager by lazy {
        NetworkFileManager(
            context = activity,
            smbClient = smbClient,
            sftpClient = sftpClient,
            ftpClient = ftpClient,
            googleDriveClient = googleDriveClient,
            dropboxClient = dropboxClient,
            oneDriveClient = oneDriveClient,
            credentialsRepository = credentialsRepository,
            smbFileOperationHandler = smbFileOperationHandler,
            sftpFileOperationHandler = sftpFileOperationHandler,
            ftpFileOperationHandler = ftpFileOperationHandler,
            cloudFileOperationHandler = cloudFileOperationHandler,
            unifiedCache = unifiedCache,
            callback = object : NetworkFileManager.NetworkFileCallback {
                override fun getCurrentResource() = null
                override fun showError(message: String) = showToastError(message)
            }
        )
    }

    // S0473: resolved from the application Hilt graph (the host Activity is @AndroidEntryPoint) so
    // the document-opened stat can be recorded without threading StatsSink through all three
    // standalone-Activity construction sites. Same pattern as AddResourceConnectionManager.
    private val statsSink: StatsSink by lazy {
        EntryPointAccessors.fromApplication(
            activity.applicationContext,
            StandaloneViewManagerEntryPoint::class.java
        ).statsSink()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StandaloneViewManagerEntryPoint {
        fun statsSink(): StatsSink
    }

    private val translationManager: TranslationManager by lazy {
        TranslationManager(
            context = activity,
            settingsRepository = settingsRepository,
            callback = object : TranslationManager.TranslationCallback {
                override fun showError(message: String) = showToastError(message)
                override fun showModelDownloadPrompt(
                    languageName: String,
                    onConfirm: () -> Unit,
                    onCancel: () -> Unit
                ) { /* Translation UI not exposed in standalone mode */ }
            }
        )
    }

    private var exoPlayer: ExoPlayer? = null

    // S0908: kept so releaseVideoPlayer() can remove this exact instance before release().
    private var videoErrorListener: Player.Listener? = null
    private val playbackControlPrefs =
        activity.getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    private val videoColorProcessor = VideoColorProcessor(
        initialHueDegrees = playbackControlPrefs.getFloat(PlaybackControlPreferences.KEY_HUE_DEGREES, 0f),
        initialBrightnessAdjustment = brightnessProgressToAdjustment(
            playbackControlPrefs.getInt(
                PlaybackControlPreferences.KEY_BRIGHTNESS_PERCENT,
                DEFAULT_BRIGHTNESS_PROGRESS
            )
        )
    )
    private var videoPositionSaveJob: Job? = null
    private var lastSavedPosition: Long = -1L
    private var currentVideoFilePath: String? = null

    // S0953: the media type currently on screen. photoView is shared IMAGE/GIF/PDF, so the PDF fling
    // listener must be gated on this to avoid paging an image on a vertical swipe.
    private var currentMediaType: MediaType? = null
    // Media3 1.2.1 deferral flags - mirrors VideoPlayerManager logic.
    private var standaloneVideoSizeKnown = false
    private var standalonePendingEffects = false
    // S0995: decoded video dimensions from onVideoSizeChanged; used to refit the frame at 90/270.
    private var standaloneVideoWidth = 0
    private var standaloneVideoHeight = 0
    // S0995: cumulative visual frame rotation (0/90/180/270), applied to photoView (image/gif) and to
    // the video effect chain. Carried across folder paging by re-applying after each image load.
    private var contentRotationDegrees = 0
    private var audioServiceController: AudioServiceController? = null
    private var audioFocusManager: AudioFocusManager? = null
    private var resumeVideoOnResume = false
    private var resumeAudioOnResume = false

    private var _pdfViewerManager: PdfViewerManager? = null
    private val pdfViewerManager: PdfViewerManager
        get() = _pdfViewerManager ?: createPdfViewerManager().also { _pdfViewerManager = it }

    private var _epubViewerManager: EpubViewerManager? = null
    private val epubViewerManager: EpubViewerManager
        get() = _epubViewerManager ?: createEpubViewerManager().also { _epubViewerManager = it }

    private var _textViewerManager: TextViewerManager? = null
    private val textViewerManager: TextViewerManager
        get() = _textViewerManager ?: createTextViewerManager().also { _textViewerManager = it }

    // ── Public API ──────────────────────────────────────────────────────────

    fun getExoPlayer(): ExoPlayer? = exoPlayer

    fun getPlayer(mediaType: MediaType?): Player? = activePlayer(mediaType)

    fun isVideoPlaying(): Boolean = exoPlayer?.isPlaying == true

    fun setHueAdjustmentDegrees(hueDegrees: Float) {
        videoColorProcessor.setHueAdjustmentDegrees(hueDegrees)
        playbackControlPrefs.edit()
            .putFloat(PlaybackControlPreferences.KEY_HUE_DEGREES, videoColorProcessor.getHueAdjustmentDegrees())
            .apply()
        applyVideoColorEffects()
    }

    fun getHueAdjustmentDegrees(): Float = videoColorProcessor.getHueAdjustmentDegrees()

    fun setBrightnessProgress(progress: Int) {
        videoColorProcessor.setBrightnessAdjustment(brightnessProgressToAdjustment(progress))
        playbackControlPrefs.edit()
            .putInt(
                PlaybackControlPreferences.KEY_BRIGHTNESS_PERCENT,
                brightnessAdjustmentToProgress(videoColorProcessor.getBrightnessAdjustment())
            )
            .apply()
        applyVideoColorEffects()
    }

    fun getBrightnessProgress(): Int =
        brightnessAdjustmentToProgress(videoColorProcessor.getBrightnessAdjustment())

    fun getBrightnessPercentOffset(): Int =
        ((getBrightnessProgress() - DEFAULT_BRIGHTNESS_PROGRESS) * 100f / DEFAULT_BRIGHTNESS_PROGRESS).toInt()

    /**
     * S0995: set the session frame rotation and apply it to the active surface. Image/gif rotate via
     * PhotoView's matrix; video re-composes its effect chain with a rotation effect. Pure visual - no
     * bitmap edit, no screen sensor. The stored angle is re-applied after each image load (folder paging).
     */
    fun setContentRotationDegrees(degrees: Int) {
        contentRotationDegrees = degrees
        when (currentMediaType) {
            MediaType.IMAGE, MediaType.GIF -> applyPhotoViewRotation()
            MediaType.VIDEO -> applyVideoColorEffects()
            else -> Unit
        }
    }

    fun getContentRotationDegrees(): Int = contentRotationDegrees

    /** S0995: PhotoView recomputes its fit around the rotation via its own matrix. */
    private fun applyPhotoViewRotation() {
        safeViews.photoView.setRotationTo(contentRotationDegrees.toFloat())
    }

    fun getPlaybackSpeed(mediaType: MediaType?): Float =
        activePlayer(mediaType)?.playbackParameters?.speed ?: 1.0f

    fun setPlaybackSpeed(mediaType: MediaType?, speed: Float) {
        activePlayer(mediaType)?.setPlaybackSpeed(speed)
    }

    /** Returns true if any media (video or audio) is currently playing. */
    fun isMediaPlaying(): Boolean =
        exoPlayer?.isPlaying == true || audioServiceController?.player?.isPlaying == true

    /** Resumes playback for whichever media type is currently active. */
    fun play() {
        exoPlayer?.play()
        audioServiceController?.player?.play()
    }

    /** Pauses playback for whichever media type is currently active. */
    fun pause() {
        exoPlayer?.pause()
        audioServiceController?.player?.pause()
    }

    private fun activePlayer(mediaType: MediaType?): Player? =
        if (mediaType == MediaType.AUDIO) audioServiceController?.player else exoPlayer

    fun show(
        mediaFile: MediaFile,
        mediaType: MediaType,
        onVideoReady: ((PlayerView) -> Unit)? = null,
        onImageReady: (() -> Unit)? = null,
    ) {
        Timber.d("StandaloneViewManager: showing $mediaType - ${mediaFile.name}")
        currentMediaType = mediaType
        // S0859: playVideo()/playAudio() are only reachable through here - release any previous
        // video player / audio controller before dispatching, so folder paging, slideshow and
        // rename-triggered re-show never orphan a live player (and its bound focus/service).
        releaseVideoPlayer()
        releaseAudioController()
        hidePhotoAndPlayerViews()
        when (mediaType) {
            MediaType.IMAGE -> showImage(mediaFile, onImageReady)
            MediaType.GIF   -> showGif(mediaFile)
            MediaType.VIDEO -> playVideo(mediaFile, onVideoReady)
            MediaType.AUDIO -> playAudio(mediaFile)
            MediaType.PDF   -> showPdf(mediaFile)
            MediaType.EPUB  -> showEpub(mediaFile)
            MediaType.TEXT  -> showText(mediaFile)
            MediaType.OFFICE_DOCUMENT -> displayOfficeDocument(mediaFile)
            else -> {
                Timber.w("StandaloneViewManager: binary type $mediaType not supported standalone")
                showToastError(activity.getString(R.string.unsupported_format_use_external_player))
                activity.finish()
            }
        }
    }

    // ── Lifecycle hooks ──────────────────────────────────────────────────────

    fun onResume() {
        exoPlayer?.playWhenReady = resumeVideoOnResume
        audioServiceController?.player?.playWhenReady = resumeAudioOnResume
    }

    fun onPause() {
        resumeVideoOnResume = exoPlayer?.playWhenReady == true
        resumeAudioOnResume = audioServiceController?.player?.playWhenReady == true
        exoPlayer?.playWhenReady = false
        audioServiceController?.player?.playWhenReady = false
        lifecycleScope.launch { saveCurrentPosition() }
        stopPositionAutoSave()
    }

    fun release() {
        Timber.d("StandaloneViewManager: release")
        stopPositionAutoSave()
        // Capture position before releasing the player - a coroutine launched after release() would arrive too late.
        val pathToSave = currentVideoFilePath
        val pos = exoPlayer?.currentPosition ?: -1L
        val dur = exoPlayer?.duration ?: -1L
        if (pathToSave != null && pos > 0L && dur > 0L && pos != lastSavedPosition) {
            persistPositionOnRelease(pathToSave, pos, dur)
        }
        releaseVideoPlayer()
        releaseAudioController()
        // Activity-scoped Glide.with() crashes here: Activity.performDestroy() flips isDestroyed()
        // to true BEFORE invoking onDestroy(), so assertNotDestroyed() always throws on this path.
        // applicationContext skips that check entirely; clear() still finds/cancels the request
        // that was registered under the activity-scoped RequestManager.
        Glide.with(activity.applicationContext).clear(safeViews.photoView)
        _pdfViewerManager?.close()
        _pdfViewerManager = null
        _epubViewerManager?.release()
        _epubViewerManager = null
        _textViewerManager?.release()
        _textViewerManager = null
    }

    /**
     * S0893: API24+ multi-window release edge for the video path only - the audio controller and
     * document viewers are untouched (audio may legitimately continue via AudioPlaybackService;
     * PDF/EPUB/text hold no comparable OS codec resource). Callers gate this call on
     * Build.VERSION_CODES.N and rebuild via [show] from their own onStart - only the host Activity
     * knows which MediaFile is currently active, so no resume state is tracked here.
     */
    fun onStopVideo() {
        if (exoPlayer != null) {
            Timber.d("StandaloneViewManager: onStopVideo - releasing backgrounded video player")
            releaseVideoPlayer()
        }
    }

    /**
     * S0859: tear down any live video player and its audio focus request. Called both from
     * [release] (host destroy) and from [show] before every dispatch (folder paging / slideshow /
     * rename re-show / swap to a non-video type) - the previous player must never be orphaned.
     */
    private fun releaseVideoPlayer() {
        stopPositionAutoSave()
        resumeVideoOnResume = false
        audioFocusManager?.releaseFocus()
        audioFocusManager = null
        // Media3 1.2.1: ExoPlayer.release() can block the main thread indefinitely when a
        // setVideoEffects() GL pipeline is active - the DefaultVideoFrameProcessor GL thread join
        // is not bounded by the release timeout (androidx/media #1139, #2098). Drain the effects and
        // detach the output surface first so the GL pipeline terminates while EGL is still valid,
        // then stop before release. Otherwise the next activity never gets a window -> input ANR.
        exoPlayer?.let { player ->
            listOfNotNull(videoErrorListener).forEach(player::removeListener)
            player.setVideoEffects(emptyList())
            player.clearVideoSurface()
            player.stop()
            player.release()
        }
        exoPlayer = null
        videoErrorListener = null
        currentVideoFilePath = null
    }

    /**
     * S0859: tear down any live audio controller. Each [AudioServiceController] binds a
     * MediaController connection to AudioPlaybackService that must be released before the field
     * is overwritten by the next file, or it leaks a bound ServiceConnection per paging step.
     */
    private fun releaseAudioController() {
        resumeAudioOnResume = false
        // Standalone mode must never continue audio in background - stop before releasing the service controller.
        audioServiceController?.player?.stop()
        audioServiceController?.release()
        audioServiceController = null
    }

    // ── Image ───────────────────────────────────────────────────────────────

    private fun showImage(mediaFile: MediaFile, onImageReady: (() -> Unit)? = null) {
        // photoView lives inside photoDualSurfaceContainer - both must be visible
        // Container is nullable (config-variant view, absent in some layouts)
        safeViews.photoDualSurfaceContainerOrNull?.let { it.isVisible = true }
        safeViews.photoView.isVisible = true
        Glide.with(safeViews.photoView)
            .load(mediaFile.path.toUri())
            // S1041: fire onImageReady only once the drawable is decoded AND bound to the view, so a
            // caller's auto-action (OCR/translate) reads a non-null photoView.drawable instead of racing
            // the async load. A failed decode must not trigger the action.
            .listener(imageBoundListener(onImageReady))
            .into(safeViews.photoView)
    }

    /**
     * S1041/S0995: fires [onImageReady] once the decoded drawable is bound AND re-applies the session
     * frame rotation so it carries to the next paged file. A failed decode triggers neither.
     */
    private fun imageBoundListener(onImageReady: (() -> Unit)?): RequestListener<Drawable> =
        object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean,
            ): Boolean = false

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean,
            ): Boolean {
                // RequestListener.onResourceReady runs BEFORE Glide binds the drawable into the
                // ImageView (the target's onResourceReady sets it right after this returns false).
                // post() defers to the next main-thread message, by which point photoView.drawable is
                // non-null - reading/rotating it earlier reintroduces the S1041 race.
                safeViews.photoView.post {
                    applyPhotoViewRotation()
                    onImageReady?.invoke()
                }
                return false
            }
        }

    /**
     * S0390: re-decode an image whose bytes were overwritten in place (crop). Glide keys on the URI,
     * so both caches are skipped and the signature varied to force a fresh decode of the new bytes.
     */
    fun reloadImage(mediaFile: MediaFile) {
        safeViews.photoDualSurfaceContainerOrNull?.let { it.isVisible = true }
        safeViews.photoView.isVisible = true
        Glide.with(safeViews.photoView)
            .load(mediaFile.path.toUri())
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .signature(ObjectKey(System.currentTimeMillis()))
            .listener(imageBoundListener(null))
            .into(safeViews.photoView)
    }

    private fun showGif(mediaFile: MediaFile) {
        safeViews.photoDualSurfaceContainerOrNull?.let { it.isVisible = true }
        safeViews.photoView.isVisible = true
        Glide.with(safeViews.photoView)
            .asGif()
            .load(mediaFile.path.toUri())
            // S0995: re-apply the session rotation once the animated drawable is bound.
            .listener(object : RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<com.bumptech.glide.load.resource.gif.GifDrawable>,
                    isFirstResource: Boolean,
                ): Boolean = false

                override fun onResourceReady(
                    resource: com.bumptech.glide.load.resource.gif.GifDrawable,
                    model: Any,
                    target: Target<com.bumptech.glide.load.resource.gif.GifDrawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean,
                ): Boolean {
                    safeViews.photoView.post { applyPhotoViewRotation() }
                    return false
                }
            })
            .into(safeViews.photoView)
    }

    // ── Video ───────────────────────────────────────────────────────────────

    private fun playVideo(mediaFile: MediaFile, onVideoReady: ((PlayerView) -> Unit)? = null) {
        val playerView = safeViews.playerView
        playerView.isVisible = true
        playerView.controllerShowTimeoutMs = 5000
        standaloneVideoSizeKnown = false
        standalonePendingEffects = false
        val player = ExoPlayer.Builder(activity).build()
        exoPlayer = player
        playerView.player = player
        applyVideoColorEffects()
        val errorListener = createPlayerErrorListener()
        videoErrorListener = errorListener
        player.addListener(errorListener)
        audioFocusManager = AudioFocusManager(activity) { isPermanent ->
            if (isPermanent) player.stop() else player.pause()
        }
        audioFocusManager?.requestFocus()
        currentVideoFilePath = mediaFile.path
        lastSavedPosition = -1L
        player.setMediaItem(MediaItem.fromUri(mediaFile.path.toUri()))
        player.playWhenReady = true
        onVideoReady?.invoke(playerView)
        // Fetch saved position before prepare() so seekTo() runs before the playback thread
        // starts, preventing DefaultVideoFrameProcessor.flush() from firing on an uninitialised
        // InputSwitcher (Media3 1.2.1: activeTextureManager is null before the first frame).
        lifecycleScope.launch {
            val savedPos = try {
                playbackPositionRepository.getPosition(mediaFile.path) ?: -1L
            } catch (e: Exception) {
                Timber.e(e, "StandaloneViewManager: Failed to restore position for ${mediaFile.path}")
                -1L
            }
            if (player != exoPlayer) return@launch  // player released while we were querying DB
            if (savedPos > 0L) {
                player.seekTo(savedPos)
                Timber.d("StandaloneViewManager: Restored position ${savedPos}ms for ${mediaFile.path}")
            }
            player.prepare()
            startPositionAutoSave(mediaFile.path)
        }
    }

    private fun applyVideoColorEffects() {
        // Standalone mode owns a separate ExoPlayer instance, so the full color chain must be
        // restored after every player recreation to keep Control dialog and gestures consistent.
        val effects = listOfNotNull(
            videoColorProcessor.buildHueEffect(),
            videoColorProcessor.buildBrightnessEffect(),
            // S0995: manual clockwise frame rotation, composed with the colour chain (single
            // setVideoEffects call). Null (omitted) at angle 0 - keeps the existing zero-cost path.
            VideoColorProcessor.buildRotationEffect(
                contentRotationDegrees, standaloneVideoWidth, standaloneVideoHeight
            )
        )
        // Media3 1.2.1 deferral: Presentation.createForWidthAndHeight crashes with -1,-1 when
        // setVideoEffects() is called before the decoder emits the first frame.
        if (!standaloneVideoSizeKnown && effects.isNotEmpty()) {
            standalonePendingEffects = true
            Timber.d("StandaloneViewManager: applyVideoColorEffects deferred - video size not yet known")
            return
        }
        exoPlayer?.setVideoEffects(effects)
    }

    private fun brightnessProgressToAdjustment(progress: Int): Float =
        ((progress.coerceIn(0, 100) - DEFAULT_BRIGHTNESS_PROGRESS) / DEFAULT_BRIGHTNESS_PROGRESS.toFloat())

    private fun brightnessAdjustmentToProgress(adjustment: Float): Int =
        ((adjustment.coerceIn(-1f, 1f) * DEFAULT_BRIGHTNESS_PROGRESS) + DEFAULT_BRIGHTNESS_PROGRESS).toInt()

    // ── Audio ───────────────────────────────────────────────────────────────

    private fun playAudio(mediaFile: MediaFile) {
        val playerView = safeViews.playerView
        playerView.isVisible = true
        // For audio: controls must always be visible (no tap needed)
        playerView.controllerShowTimeoutMs = Int.MAX_VALUE
        val controller = AudioServiceController(activity)
        audioServiceController = controller
        // Do NOT use AudioFocusManager here - ExoPlayer inside AudioPlaybackService is built with
        // setAudioAttributes(..., handleAudioFocus=true) and manages focus automatically.
        // Creating a separate AudioFocusManager in the Activity would cause a double-focus conflict:
        // the Activity's manager would receive AUDIOFOCUS_LOSS as soon as the service's ExoPlayer
        // requests focus, triggering player.stop() and immediately killing playback.
        val mimeType = if (MidiPlaybackPolicy.isMidiPath(mediaFile.path)) MimeTypes.AUDIO_MIDI else null
        controller.playAudioWithMetadata(mediaFile.path.toUri(), mediaFile.name.substringBeforeLast('.'), mimeType = mimeType) { player ->
            playerView.player = player
            playerView.showController()
        }
    }

    // ── PDF ─────────────────────────────────────────────────────────────────

    private fun showPdf(mediaFile: MediaFile) {
        pdfViewerManager.displayPdf(mediaFile)
        // S0953: legacy PDF vertical-swipe paging parity with the in-app player. photoView is shared
        // IMAGE/GIF/PDF, so the guard drops the fling unless a PDF is on screen. Native fling callback
        // (not setOnTouchListener) keeps the PhotoView attacher's pinch/pan intact.
        safeViews.photoView.setOnSingleFlingListener(
            OnSingleFlingListener { e1, e2, velocityX, velocityY ->
                if (currentMediaType != MediaType.PDF) return@OnSingleFlingListener false
                pdfViewerManager.handlePdfFling(e1, e2, velocityX, velocityY)
            }
        )
    }

    fun showPdfPreviousPage() { _pdfViewerManager?.showPreviousPage() }
    fun showPdfNextPage()     { _pdfViewerManager?.showNextPage() }
    fun showPdfFirstPage()    { _pdfViewerManager?.showFirstPage() }

    // ── EPUB ─────────────────────────────────────────────────────────────────

    private fun showEpub(mediaFile: MediaFile) {
        epubViewerManager.displayEpub(mediaFile)
    }

    fun showEpubPreviousChapter() { _epubViewerManager?.showPreviousChapter() }
    fun showEpubNextChapter()     { _epubViewerManager?.showNextChapter() }
    fun showEpubFirstChapter()    { _epubViewerManager?.showFirstChapter() }
    fun showEpubTableOfContents() { _epubViewerManager?.showTableOfContents() }
    fun decreaseEpubFontSize()    { _epubViewerManager?.decreaseFontSize() }
    fun increaseEpubFontSize()    { _epubViewerManager?.increaseFontSize() }
    fun showEpubReaderSettings()  { _epubViewerManager?.showReaderSettingsDialog() }
    fun showEpubCrossSearch()     { _epubViewerManager?.showCrossChapterSearch() }
    fun exitEpubFullscreen()      { _epubViewerManager?.exitFullscreenMode() }
    fun toggleEpubTranslation()   { _epubViewerManager?.toggleTranslation() }
    fun togglePdfTranslation()    { _pdfViewerManager?.toggleTranslation() }

    // Viewer manager providers - for SearchControlsManager wiring in StandalonePlayerActivity
    fun epubViewerManagerProvider(): EpubViewerManager = epubViewerManager
    fun pdfViewerManagerProvider(): PdfViewerManager   = pdfViewerManager
    fun textViewerManagerProvider(): TextViewerManager = textViewerManager

    /** Returns true when an EPUB file is currently loaded (used for orientation-aware button visibility). */
    fun isEpubActive(): Boolean   = _epubViewerManager != null

    /** Returns the EPUB WebView selection ActionMode callback, or null if no EPUB is loaded. */
    fun getEpubSelectionActionModeCallback(): DocumentSelectionActionModeCallback? =
        _epubViewerManager?.getSelectionActionModeCallback()

    /** Returns the active document WebView selection callback, prioritizing Office over EPUB. */
    fun getDocumentSelectionActionModeCallback(): DocumentSelectionActionModeCallback? {
        val officeCallback = if (officeDocumentViewerHostDelegate.isInitialized() && officeDocumentViewerHost.isActive) {
            officeDocumentViewerHost.getSelectionActionModeCallback()
        } else {
            null
        }
        // S0380: EPUB selection requires the epub web view to be present + visible in the current
        // layout root; absent on layouts that omit the epub subtree (epubWebViewOrNull is null).
        return officeCallback ?: _epubViewerManager
            ?.takeIf { safeViews.epubWebViewOrNull?.isVisible == true }
            ?.getSelectionActionModeCallback()
    }

    /**
     * Updates the ExoPlayer media item in AudioPlaybackService after a SAF rename.
     * Uses replaceMediaItem() so playback continues without interruption.
     * No-op if no audio is currently playing.
     */
    fun updateAudioMediaItem(newUri: Uri) {
        val player = audioServiceController?.player ?: return
        val currentPosition = player.currentPosition
        player.replaceMediaItem(0, MediaItem.fromUri(newUri))
        // replaceMediaItem preserves position in Media3; explicit seek as safety net.
        player.seekTo(currentPosition)
        Timber.d("StandaloneViewManager: audio MediaItem updated to $newUri pos=${currentPosition}ms")
    }

    // ── TEXT ─────────────────────────────────────────────────────────────────

    private fun showText(mediaFile: MediaFile) {
        textViewerManager.displayText(mediaFile, isWritable = false)
    }

    // S0301 Phase 02: flavor-safe Office viewer decision provider for standalone mode.
    // Market flavors delegate externally (S0299); noLegal plugs an embedded viewer in Phase 03.
    private val officeDocumentViewerProvider by lazy {
        OfficeDocumentViewerProviderFactory().create()
    }

    // S0301 Phase 03: embedded Office viewer host for standalone mode. Market = no-op host,
    // noLegal = engine-backed read-only in-app viewer. Fullscreen toggling is a no-op here;
    // standalone mode has no system-bars manager wired.
    private val officeDocumentViewerHostDelegate = lazy {
        OfficeDocumentViewerProviderFactory().createViewerHost(
            // S0380: Office host is decoupled to a layout root; works on any standalone layout that
            // includes the officeDocumentViewerContainer surface (full unified or trimmed document).
            root = root,
            coroutineScope = lifecycleScope,
            callback = object : OfficeDocumentViewerHost.Callback {
                override fun showError(message: String) = showToastError(message)
                override fun onEnterFullscreenMode() { onDocumentEnterFullscreen?.invoke() }
                override fun onExitFullscreenMode() { onDocumentExitFullscreen?.invoke() }
                override fun onTranslateSelection(text: String) = translateOfficeSelection(text)
                override fun onRequireExternalFallback(mediaFile: MediaFile) {
                    // S0301 Phase 05: engine could not render internally - show the explicit
                    // external / share / cancel dialog. Cancel keeps the current screen open per
                    // the approved fallback contract.
                    showOfficeFallbackDialog(mediaFile)
                }
            },
        )
    }
    private val officeDocumentViewerHost: OfficeDocumentViewerHost by officeDocumentViewerHostDelegate

    private fun displayOfficeDocument(mediaFile: MediaFile) {
        val session = officeDocumentViewerProvider.resolve(mediaFile)
        when (session.outcome) {
            OfficeDocumentViewerOutcome.DISPLAY_INTERNALLY ->
                displayOfficeDocumentInternally(mediaFile)
            // S0301 Phase 05: provider asked for the explicit external / share / cancel dialog.
            // Cancel keeps the standalone screen open, matching the shared Office UX contract.
            OfficeDocumentViewerOutcome.SHOW_FALLBACK_DIALOG ->
                showOfficeFallbackDialog(mediaFile)
            OfficeDocumentViewerOutcome.DELEGATE_EXTERNAL ->
                displayOfficeDocumentExternally(mediaFile, finishAfterHandoff = true)
        }
    }

    /**
     * Explicit Office fallback dialog for standalone mode (S0301 Phase 05). Offers opening in
     * another app or sharing the file. Cancel keeps the screen open per the approved fallback
     * contract. Strings stay factual (COMMUNICATION_POLICY §6).
     */
    private fun showOfficeFallbackDialog(mediaFile: MediaFile) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.office_viewer_fallback_title)
            .setMessage(R.string.office_viewer_fallback_message)
            .setPositiveButton(R.string.office_viewer_fallback_open_external) { _, _ ->
                displayOfficeDocumentExternally(mediaFile, finishAfterHandoff = true)
            }
            .setNeutralButton(R.string.office_viewer_fallback_share) { _, _ ->
                shareOfficeDocument(mediaFile)
            }
            .setNegativeButton(R.string.office_viewer_fallback_cancel, null)
            .show()
    }

    /**
     * Share the prepared Office [mediaFile] through the unified «Send to..» menu (S0459 Phase 06,
     * was a standalone ACTION_SEND chooser in S0301 Phase 05). [prepareFileForRead] keeps network
     * fetches off the main thread as before; the resulting FileProvider Uri is handed to the menu.
     */
    private fun shareOfficeDocument(mediaFile: MediaFile) {
        val host = activity as? androidx.fragment.app.FragmentActivity ?: run {
            Timber.w("Office share host is not a FragmentActivity - cannot open send-to menu")
            return
        }
        lifecycleScope.launch {
            try {
                val preparedFile = networkFileManager.prepareFileForRead(mediaFile)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    preparedFile
                )
                val settings = settingsRepository.getSettings().first()
                val content = ShareableContent(
                    uris = listOf(uri),
                    mime = "application/octet-stream",
                    mediaType = MediaType.OFFICE_DOCUMENT,
                    displayName = mediaFile.name,
                    mediaFile = mediaFile,
                )
                sendToMenuManager().show(host, content, settings)
            } catch (e: Exception) {
                Timber.e(e, "StandaloneViewManager: failed to share Office document")
                showToastError(activity.getString(R.string.error_opening_file_simple))
            }
        }
    }

    /** S0459: app-scoped accessor for [SendToMenuManager] (Singleton) from the manually-built view manager. */
    private fun sendToMenuManager(): com.sza.fastmediasorter.ui.share.SendToMenuManager =
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(activity.applicationContext, SendToMenuEntryPoint::class.java)
            .sendToMenuManager()

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    internal interface SendToMenuEntryPoint {
        fun sendToMenuManager(): com.sza.fastmediasorter.ui.share.SendToMenuManager
    }

    /**
     * Render an Office document inside the in-app viewer (S0301 Phase 03, noLegal standalone).
     * Falls back to the explicit Office dialog when the host cannot accept the prepared file.
     */
    private fun displayOfficeDocumentInternally(mediaFile: MediaFile) {
        lifecycleScope.launch {
            try {
                val preparedFile = networkFileManager.prepareFileForRead(mediaFile)
                val started = officeDocumentViewerHost.open(mediaFile, preparedFile)
                if (!started) showOfficeFallbackDialog(mediaFile)
            } catch (e: Exception) {
                Timber.e(e, "StandaloneViewManager: failed to render Office document internally")
                showOfficeFallbackDialog(mediaFile)
            }
        }
    }

    private fun displayOfficeDocumentExternally(mediaFile: MediaFile, finishAfterHandoff: Boolean) {
        lifecycleScope.launch {
            try {
                val preparedFile = networkFileManager.prepareFileForRead(mediaFile)
                val opened = OfficeDocumentOpenManager.openPreparedFile(
                    activity = activity,
                    file = preparedFile,
                    displayName = mediaFile.name
                )
                if (!opened) {
                    showToastError(activity.getString(R.string.no_app_to_open))
                }
            } catch (e: Exception) {
                Timber.e(e, "StandaloneViewManager: failed to open Office document externally")
                showToastError(activity.getString(R.string.error_opening_file_simple))
            } finally {
                if (finishAfterHandoff) activity.finish()
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Hide views the standalone manager controls directly.
     * PDF/EPUB/TEXT managers handle their own view visibility internally.
     */
    private fun hidePhotoAndPlayerViews() {
        safeViews.photoView.isVisible = false
        safeViews.photoDualSurfaceContainerOrNull?.let { it.isVisible = false }
        safeViews.playerView.isVisible = false
    }

    private fun showToastError(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    private fun showTranslatedTextDialog(text: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.translation_result_title)
            .setMessage(text)
            .setPositiveButton(R.string.copy) { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("translation", text))
                Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun translateOfficeSelection(text: String) {
        if (text.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
            val targetLang = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
            val translated = translationManager.translate(text, sourceLang, targetLang)
            withContext(Dispatchers.Main) {
                if (translated != null) {
                    showTranslatedTextDialog(translated)
                } else {
                    showToastError(activity.getString(R.string.translation_error))
                }
            }
        }
    }

    private fun createPlayerErrorListener(): Player.Listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val msg = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND           -> activity.getString(R.string.error_file_not_found)
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> activity.getString(R.string.error_network_connection)
                PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK        -> activity.getString(R.string.error_playback_failed)
                PlaybackException.ERROR_CODE_TIMEOUT                     -> activity.getString(R.string.error_playback_timeout)
                PlaybackException.ERROR_CODE_REMOTE_ERROR                -> activity.getString(R.string.error_network_playback)
                PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW          -> activity.getString(R.string.error_behind_live_window)
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED  -> activity.getString(R.string.error_invalid_format)
                else -> activity.getString(R.string.error_playback_failed)
            }
            showToastError(msg)
            Timber.e(error, "StandaloneViewManager: playback error - $msg")
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (videoSize.width <= 0 || videoSize.height <= 0) return
            // S0995: remember decoded dims so the rotation effect can refit the frame at 90/270.
            standaloneVideoWidth = videoSize.width
            standaloneVideoHeight = videoSize.height
            if (!standaloneVideoSizeKnown) {
                standaloneVideoSizeKnown = true
                Timber.d("StandaloneViewManager: onVideoSizeChanged ${videoSize.width}x${videoSize.height}")
                if (standalonePendingEffects) {
                    standalonePendingEffects = false
                    applyVideoColorEffects()
                }
            }
        }
    }

    // ── Position tracking ────────────────────────────────────────────────────

    private fun startPositionAutoSave(filePath: String) {
        stopPositionAutoSave()
        videoPositionSaveJob = lifecycleScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                saveCurrentPosition()
            }
        }
    }

    private fun stopPositionAutoSave() {
        videoPositionSaveJob?.cancel()
        videoPositionSaveJob = null
    }

    private suspend fun saveCurrentPosition() {
        val path = currentVideoFilePath ?: return
        val player = exoPlayer ?: return
        val position = player.currentPosition
        val duration = player.duration
        if (position == lastSavedPosition || duration <= 0L || position < 0L) return
        try {
            withContext(Dispatchers.IO + NonCancellable) {
                playbackPositionRepository.savePosition(path, position, duration)
            }
            lastSavedPosition = position
            Timber.d("StandaloneViewManager: Saved position ${position}ms/${duration}ms")
        } catch (e: Exception) {
            Timber.e(e, "StandaloneViewManager: Failed to save position")
        }
    }

    private fun persistPositionOnRelease(path: String, position: Long, duration: Long) {
        CoroutineScope(Dispatchers.IO + NonCancellable).launch {
            try {
                playbackPositionRepository.savePosition(path, position, duration)
                Timber.d("StandaloneViewManager: Saved position on release ${position}ms/${duration}ms")
            } catch (e: Exception) {
                Timber.e(e, "StandaloneViewManager: Failed to save position on release")
            }
        }
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    private fun createPdfViewerManager(): PdfViewerManager {
        // S0380: PdfViewerManager is decoupled to a layout root, so this works on any standalone
        // layout that includes the pdf view subtree (full unified or trimmed document layout).
        return PdfViewerManager(
            root = root,
            networkFileManager = networkFileManager,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = object : PdfViewerManager.PdfViewerCallback {
                override fun showError(message: String) = showToastError(message)
                override fun displayOcrText(text: String) { /* not exposed in standalone */ }
                override fun displayTranslatedText(text: String) = showTranslatedTextDialog(text)
                override fun shareFileToGoogleLens(file: File) { /* not exposed in standalone */ }
                override fun isLandscapeMode(): Boolean =
                    activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                override fun onEnterFullscreenMode() { onDocumentEnterFullscreen?.invoke() }
                override fun onExitFullscreenMode() { onDocumentExitFullscreen?.invoke() }
            },
            translationManager = translationManager,
            playbackPositionRepository = playbackPositionRepository,
            statsSink = statsSink
        )
    }

    private fun createEpubViewerManager(): EpubViewerManager {
        // S0380: EpubViewerManager is decoupled to a layout root, so this works on any standalone
        // layout that includes the epub view subtree (full unified or trimmed document layout).
        return EpubViewerManager(
            root = root,
            networkFileManager = networkFileManager,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = object : EpubViewerManager.EpubViewerCallback {
                override fun showError(message: String) = showToastError(message)
                override fun displayTranslatedText(text: String) = showTranslatedTextDialog(text)
                override fun onEnterFullscreenMode() { onDocumentEnterFullscreen?.invoke() }
                override fun onExitFullscreenMode() { onDocumentExitFullscreen?.invoke() }
            },
            playbackPositionRepository = playbackPositionRepository,
            translationManager = translationManager
        )
    }

    private fun createTextViewerManager(): TextViewerManager {
        return TextViewerManager(
            context = activity,
            root = root,
            networkFileManager = networkFileManager,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = object : TextViewerManager.TextViewerCallback {
                override fun showError(message: String) = showToastError(message)
                override fun showTranslationSettingsDialog() { /* not exposed in standalone */ }
                override fun exitFullscreenMode() { /* not exposed in standalone */ }
                override fun setTouchZonesEnabled(enabled: Boolean) { /* not exposed in standalone */ }
                override fun showEncodingDialog() { /* not exposed in standalone */ }
                override fun launchEditorCalculator(initialInput: String) { /* Standalone text viewing is read-only */ }
                override fun launchSelectionCalculator(initialInput: String) {
                    openCalculatorForSelection(activity, initialInput)
                }
                override fun finishActivity() { /* StandalonePlayer does not create notes */ }
            },
            translationManager = translationManager
        )
    }
}
