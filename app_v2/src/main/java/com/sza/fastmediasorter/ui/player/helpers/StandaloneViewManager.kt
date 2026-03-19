package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
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
    private var audioServiceController: AudioServiceController? = null

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

    fun show(mediaFile: MediaFile, mediaType: MediaType) {
        Timber.d("StandaloneViewManager: showing $mediaType — ${mediaFile.name}")
        hidePhotoAndPlayerViews()
        when (mediaType) {
            MediaType.IMAGE -> showImage(mediaFile)
            MediaType.GIF   -> showGif(mediaFile)
            MediaType.VIDEO -> playVideo(mediaFile)
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

    fun release() {
        Timber.d("StandaloneViewManager: release")
        exoPlayer?.release()
        exoPlayer = null
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
            .load(Uri.parse(mediaFile.path))
            .into(binding.photoView)
    }

    private fun showGif(mediaFile: MediaFile) {
        binding.photoDualSurfaceContainer?.let { it.isVisible = true }
        binding.photoView.isVisible = true
        Glide.with(activity.applicationContext)
            .asGif()
            .load(Uri.parse(mediaFile.path))
            .into(binding.photoView)
    }

    // ── Video ───────────────────────────────────────────────────────────────

    private fun playVideo(mediaFile: MediaFile) {
        binding.playerView.isVisible = true
        val player = ExoPlayer.Builder(activity).build()
        exoPlayer = player
        binding.playerView.player = player
        player.setMediaItem(MediaItem.fromUri(Uri.parse(mediaFile.path)))
        player.prepare()
        player.playWhenReady = true
    }

    // ── Audio ───────────────────────────────────────────────────────────────

    private fun playAudio(mediaFile: MediaFile) {
        binding.playerView.isVisible = true
        val controller = AudioServiceController(activity)
        audioServiceController = controller
        controller.playAudio(Uri.parse(mediaFile.path)) { player ->
            binding.playerView.player = player
        }
    }

    // ── PDF ─────────────────────────────────────────────────────────────────

    private fun showPdf(mediaFile: MediaFile) {
        pdfViewerManager.displayPdf(mediaFile)
    }

    // ── EPUB ─────────────────────────────────────────────────────────────────

    private fun showEpub(mediaFile: MediaFile) {
        epubViewerManager.displayEpub(mediaFile)
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
                override fun displayTranslatedText(text: String) { /* not exposed in standalone */ }
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
                override fun displayTranslatedText(text: String) { /* not exposed in standalone */ }
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
