package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.PlaybackControlPreferences
import com.sza.fastmediasorter.ui.player.VideoColorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val binding: ActivityPlayerUnifiedBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val googleDriveClient: GoogleDriveRestClient,
    private val dropboxClient: DropboxClient,
    private val oneDriveClient: OneDriveRestClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val smbFileOperationHandler: SmbFileOperationHandler,
    private val sftpFileOperationHandler: SftpFileOperationHandler,
    private val ftpFileOperationHandler: FtpFileOperationHandler,
    private val cloudFileOperationHandler: CloudFileOperationHandler,
    private val unifiedCache: UnifiedFileCache,
    private val settingsRepository: SettingsRepository,
    private val playbackPositionRepository: PlaybackPositionRepository
) {

    companion object {
        private const val DEFAULT_BRIGHTNESS_PROGRESS = 50
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
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
    // Media3 1.2.1 deferral flags — mirrors VideoPlayerManager logic.
    private var standaloneVideoSizeKnown = false
    private var standalonePendingEffects = false
    private var audioServiceController: AudioServiceController? = null
    private var audioFocusManager: AudioFocusManager? = null

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

    fun show(mediaFile: MediaFile, mediaType: MediaType, onVideoReady: ((PlayerView) -> Unit)? = null) {
        Timber.d("StandaloneViewManager: showing $mediaType — ${mediaFile.name}")
        hidePhotoAndPlayerViews()
        when (mediaType) {
            MediaType.IMAGE -> showImage(mediaFile)
            MediaType.GIF   -> showGif(mediaFile)
            MediaType.VIDEO -> playVideo(mediaFile, onVideoReady)
            MediaType.AUDIO -> playAudio(mediaFile)
            MediaType.PDF   -> showPdf(mediaFile)
            MediaType.EPUB  -> showEpub(mediaFile)
            MediaType.TEXT  -> showText(mediaFile)
            else -> {
                Timber.w("StandaloneViewManager: binary type $mediaType not supported standalone")
                showToastError(activity.getString(R.string.unsupported_format_use_external_player))
                activity.finish()
            }
        }
    }

    // ── Lifecycle hooks ──────────────────────────────────────────────────────

    fun onResume() {
        exoPlayer?.playWhenReady = true
        audioServiceController?.player?.playWhenReady = true
        acquireWakeLock()
    }

    fun onPause() {
        exoPlayer?.playWhenReady = false
        audioServiceController?.player?.playWhenReady = false
        releaseWakeLock()
        lifecycleScope.launch { saveCurrentPosition() }
        stopPositionAutoSave()
    }

    fun release() {
        Timber.d("StandaloneViewManager: release")
        stopPositionAutoSave()
        // Capture position before releasing the player — a coroutine launched after release() would arrive too late.
        val pathToSave = currentVideoFilePath
        val pos = exoPlayer?.currentPosition ?: -1L
        val dur = exoPlayer?.duration ?: -1L
        if (pathToSave != null && pos > 0L && dur > 0L && pos != lastSavedPosition) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    playbackPositionRepository.savePosition(pathToSave, pos, dur)
                    Timber.d("StandaloneViewManager: Saved position on release ${pos}ms/${dur}ms")
                } catch (e: Exception) {
                    Timber.e(e, "StandaloneViewManager: Failed to save position on release")
                }
            }
        }
        releaseWakeLock()
        audioFocusManager?.releaseFocus()
        audioFocusManager = null
        exoPlayer?.release()
        exoPlayer = null
        // Standalone mode must never continue audio in background — stop before releasing the service controller.
        audioServiceController?.player?.stop()
        audioServiceController?.release()
        audioServiceController = null
        _pdfViewerManager?.close()
        _pdfViewerManager = null
        _epubViewerManager?.release()
        _epubViewerManager = null
        _textViewerManager?.release()
        _textViewerManager = null
    }

    // ── Image ───────────────────────────────────────────────────────────────

    private fun showImage(mediaFile: MediaFile) {
        // photoView lives inside photoDualSurfaceContainer — both must be visible
        // Container is nullable in the binding (config-variant view)
        binding.photoDualSurfaceContainer?.let { it.isVisible = true }
        binding.photoView.isVisible = true
        Glide.with(activity.applicationContext)
            .load(mediaFile.path.toUri())
            .into(binding.photoView)
    }

    private fun showGif(mediaFile: MediaFile) {
        binding.photoDualSurfaceContainer?.let { it.isVisible = true }
        binding.photoView.isVisible = true
        Glide.with(activity.applicationContext)
            .asGif()
            .load(mediaFile.path.toUri())
            .into(binding.photoView)
    }

    // ── Video ───────────────────────────────────────────────────────────────

    private fun playVideo(mediaFile: MediaFile, onVideoReady: ((PlayerView) -> Unit)? = null) {
        binding.playerView.isVisible = true
        binding.playerView.controllerShowTimeoutMs = 5000
        standaloneVideoSizeKnown = false
        standalonePendingEffects = false
        val player = ExoPlayer.Builder(activity).build()
        exoPlayer = player
        binding.playerView.player = player
        applyVideoColorEffects()
        player.addListener(createPlayerErrorListener())
        audioFocusManager = AudioFocusManager(activity) { isPermanent ->
            if (isPermanent) player.stop() else player.pause()
        }
        audioFocusManager?.requestFocus()
        currentVideoFilePath = mediaFile.path
        lastSavedPosition = -1L
        player.setMediaItem(MediaItem.fromUri(mediaFile.path.toUri()))
        player.prepare()
        lifecycleScope.launch {
            restorePlaybackPosition(mediaFile.path)
            startPositionAutoSave(mediaFile.path)
        }
        player.playWhenReady = true
        acquireWakeLock()
        onVideoReady?.invoke(binding.playerView)
    }

    private fun applyVideoColorEffects() {
        // Standalone mode owns a separate ExoPlayer instance, so the full color chain must be
        // restored after every player recreation to keep Control dialog and gestures consistent.
        val effects = listOfNotNull(
            videoColorProcessor.buildHueEffect(),
            videoColorProcessor.buildBrightnessEffect()
        )
        // Media3 1.2.1 deferral: Presentation.createForWidthAndHeight crashes with -1,-1 when
        // setVideoEffects() is called before the decoder emits the first frame.
        if (!standaloneVideoSizeKnown && effects.isNotEmpty()) {
            standalonePendingEffects = true
            Timber.d("StandaloneViewManager: applyVideoColorEffects deferred — video size not yet known")
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
        binding.playerView.isVisible = true
        // For audio: controls must always be visible (no tap needed)
        binding.playerView.controllerShowTimeoutMs = Int.MAX_VALUE
        val controller = AudioServiceController(activity)
        audioServiceController = controller
        // Do NOT use AudioFocusManager here — ExoPlayer inside AudioPlaybackService is built with
        // setAudioAttributes(..., handleAudioFocus=true) and manages focus automatically.
        // Creating a separate AudioFocusManager in the Activity would cause a double-focus conflict:
        // the Activity's manager would receive AUDIOFOCUS_LOSS as soon as the service's ExoPlayer
        // requests focus, triggering player.stop() and immediately killing playback.
        controller.playAudioWithMetadata(mediaFile.path.toUri(), mediaFile.name.substringBeforeLast('.')) { player ->
            binding.playerView.player = player
            binding.playerView.showController()
            acquireWakeLock()
        }
    }

    // ── PDF ─────────────────────────────────────────────────────────────────

    private fun showPdf(mediaFile: MediaFile) {
        pdfViewerManager.displayPdf(mediaFile)
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

    // Viewer manager providers — for SearchControlsManager wiring in StandalonePlayerActivity
    fun epubViewerManagerProvider(): EpubViewerManager = epubViewerManager
    fun pdfViewerManagerProvider(): PdfViewerManager   = pdfViewerManager
    fun textViewerManagerProvider(): TextViewerManager = textViewerManager

    /** Returns true when an EPUB file is currently loaded (used for orientation-aware button visibility). */
    fun isEpubActive(): Boolean   = _epubViewerManager != null

    /** Returns the EPUB WebView selection ActionMode callback, or null if no EPUB is loaded. */
    fun getEpubSelectionActionModeCallback(): DocumentSelectionActionModeCallback? =
        _epubViewerManager?.getSelectionActionModeCallback()

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

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Hide views the standalone manager controls directly.
     * PDF/EPUB/TEXT managers handle their own view visibility internally.
     */
    private fun hidePhotoAndPlayerViews() {
        binding.photoView.isVisible = false
        binding.photoDualSurfaceContainer?.let { it.isVisible = false }
        binding.playerView.isVisible = false
    }

    private fun showToastError(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    private fun showTranslatedTextDialog(text: String) {
        AlertDialog.Builder(activity)
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

    private fun acquireWakeLock() {
        if (binding.playerView.isVisible) {
            binding.playerView.keepScreenOn = true
            Timber.d("StandaloneViewManager: screen wake lock acquired")
        }
    }

    private fun releaseWakeLock() {
        binding.playerView.keepScreenOn = false
        Timber.d("StandaloneViewManager: screen wake lock released")
    }

    private fun createPlayerErrorListener(): Player.Listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val msg = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND           -> activity.getString(R.string.error_file_not_found)
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> activity.getString(R.string.error_network_connection)
                PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK        -> activity.getString(R.string.error_codec_unsupported)
                PlaybackException.ERROR_CODE_TIMEOUT                     -> activity.getString(R.string.error_playback_timeout)
                PlaybackException.ERROR_CODE_REMOTE_ERROR                -> activity.getString(R.string.error_network_playback)
                PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW          -> activity.getString(R.string.error_behind_live_window)
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED  -> activity.getString(R.string.error_invalid_format)
                else -> error.message ?: activity.getString(R.string.error_playback_failed)
            }
            showToastError(msg)
            Timber.e(error, "StandaloneViewManager: playback error — $msg")
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (videoSize.width <= 0 || videoSize.height <= 0) return
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

    private suspend fun restorePlaybackPosition(filePath: String) {
        try {
            val savedPos = playbackPositionRepository.getPosition(filePath)
            if (savedPos != null && savedPos > 0L) {
                exoPlayer?.seekTo(savedPos)
                Timber.d("StandaloneViewManager: Restored position ${savedPos}ms for $filePath")
            }
        } catch (e: Exception) {
            Timber.e(e, "StandaloneViewManager: Failed to restore position for $filePath")
        }
    }

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
        lastSavedPosition = position
        try {
            withContext(Dispatchers.IO) {
                playbackPositionRepository.savePosition(path, position, duration)
            }
            Timber.d("StandaloneViewManager: Saved position ${position}ms/${duration}ms")
        } catch (e: Exception) {
            Timber.e(e, "StandaloneViewManager: Failed to save position")
        }
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    private fun createPdfViewerManager(): PdfViewerManager {
        return PdfViewerManager(
            binding = binding,
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
                override fun onEnterFullscreenMode() { /* not exposed in standalone */ }
                override fun onExitFullscreenMode() { /* not exposed in standalone */ }
            },
            translationManager = translationManager,
            playbackPositionRepository = playbackPositionRepository
        )
    }

    private fun createEpubViewerManager(): EpubViewerManager {
        return EpubViewerManager(
            binding = binding,
            networkFileManager = networkFileManager,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = object : EpubViewerManager.EpubViewerCallback {
                override fun showError(message: String) = showToastError(message)
                override fun displayTranslatedText(text: String) = showTranslatedTextDialog(text)
                override fun onEnterFullscreenMode() { /* not exposed in standalone */ }
                override fun onExitFullscreenMode() { /* not exposed in standalone */ }
            },
            playbackPositionRepository = playbackPositionRepository,
            translationManager = translationManager
        )
    }

    private fun createTextViewerManager(): TextViewerManager {
        return TextViewerManager(
            context = activity,
            binding = binding,
            networkFileManager = networkFileManager,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = object : TextViewerManager.TextViewerCallback {
                override fun showError(message: String) = showToastError(message)
                override fun showTranslationSettingsDialog() { /* not exposed in standalone */ }
                override fun exitFullscreenMode() { /* not exposed in standalone */ }
                override fun setTouchZonesEnabled(enabled: Boolean) { /* not exposed in standalone */ }
                override fun showEncodingDialog() { /* not exposed in standalone */ }
            },
            translationManager = translationManager
        )
    }
}
