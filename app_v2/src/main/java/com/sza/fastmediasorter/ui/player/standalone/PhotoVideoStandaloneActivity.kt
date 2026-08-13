package com.sza.fastmediasorter.ui.player.standalone

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.ui.SelfManagedScreenOrientation
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
import com.sza.fastmediasorter.databinding.ActivityStandalonePhotoVideoBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.dialog.FileInfoDialog
import com.sza.fastmediasorter.ui.player.DefaultPlayerProbe
import com.sza.fastmediasorter.ui.player.PlaybackControlDialogFragment
import com.sza.fastmediasorter.ui.player.StandalonePlayerViewModel
import com.sza.fastmediasorter.ui.player.VideoTrackSelectionManager
import com.sza.fastmediasorter.ui.player.contracts.PlayerActionHost
import com.sza.fastmediasorter.ui.player.contracts.PlayerHostCapabilities
import com.sza.fastmediasorter.ui.player.contracts.VideoPlayerHandle
import com.sza.fastmediasorter.ui.player.helpers.ImageCropManager
import com.sza.fastmediasorter.ui.player.helpers.PhotoVideoStandaloneVideoHandle
import com.sza.fastmediasorter.ui.player.helpers.PlayerCropDelegate
import com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler
import com.sza.fastmediasorter.ui.player.helpers.ScreenRotationManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneFileOperationsHandler
import com.sza.fastmediasorter.ui.player.helpers.StandaloneFullscreenManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneKeyboardManager
import com.sza.fastmediasorter.ui.player.helpers.StandalonePlayerSettingsManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneVideoControlsManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneVideoTouchDelegate
import com.sza.fastmediasorter.ui.player.helpers.StandaloneViewManager
import com.sza.fastmediasorter.ui.player.print.PrintDispatchActivity
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.UserActionLogger
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

private const val PRINT_TEMP_PNG_QUALITY = 100

/**
 * S0380: specialized standalone activity for image/gif/video files opened from external intents.
 * Inflates a trimmed layout (no pdf/epub/text/audio-cover/office view hierarchies) and drives only
 * the image/gif/video paths of the shared [StandaloneViewManager] (ADR-2 Unified Playback Logic).
 * Audio is a separate lane: an audio URI is rejected with the unsupported-format toast.
 * File operations / favourite reuse the shared standalone helpers + ViewModel.
 */
@SuppressLint("UnsafeIntentLaunch")
@AndroidEntryPoint
class PhotoVideoStandaloneActivity :
    BaseActivity<ActivityStandalonePhotoVideoBinding>(), PlayerHostCapabilities, PlayerActionHost, SelfManagedScreenOrientation,
    com.sza.fastmediasorter.core.share.SharePrintHost {

    private val viewModel: StandalonePlayerViewModel by viewModels()

    private val batchDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> fileOperations.handleBatchDeleteResult(result.resultCode == RESULT_OK) }

    private val recoverableDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> fileOperations.handleRecoverableDeleteResult(result.resultCode == RESULT_OK) }

    // S0610: custom-path («..») destination for Copy/Move. The chosen SAF tree is persisted and the
    // pending operation type decides whether the current file is copied or moved into it.
    private var pendingCustomPathOp: com.sza.fastmediasorter.domain.model.FileOperationType? = null
    private val customPathPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val op = pendingCustomPathOp
        pendingCustomPathOp = null
        if (uri == null || op == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val label = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() } ?: getString(R.string.select_folder)
        when (op) {
            com.sza.fastmediasorter.domain.model.FileOperationType.MOVE ->
                fileOperations.moveCurrentFileToPath(uri.toString(), label)
            else ->
                fileOperations.copyCurrentFileToPath(uri.toString(), label)
        }
    }

    // Standalone opens local/content URIs far more often than network paths, so these heavy
    // collaborators stay behind dagger.Lazy until a network-only flow actually needs them.
    @Inject lateinit var smbClient: Lazy<SmbClient>
    @Inject lateinit var sftpClient: Lazy<SftpClient>
    @Inject lateinit var ftpClient: Lazy<FtpClient>
    @Inject lateinit var googleDriveClient: Lazy<GoogleDriveRestClient>
    @Inject lateinit var dropboxClient: Lazy<DropboxClient>
    @Inject lateinit var oneDriveClient: Lazy<OneDriveRestClient>
    @Inject lateinit var credentialsRepository: Lazy<NetworkCredentialsRepository>
    @Inject lateinit var smbFileOperationHandler: Lazy<SmbFileOperationHandler>
    @Inject lateinit var sftpFileOperationHandler: Lazy<SftpFileOperationHandler>
    @Inject lateinit var ftpFileOperationHandler: Lazy<FtpFileOperationHandler>
    @Inject lateinit var cloudFileOperationHandler: Lazy<CloudFileOperationHandler>
    @Inject lateinit var unifiedCache: Lazy<UnifiedFileCache>
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var imageClipboardWriter: com.sza.fastmediasorter.core.clipboard.ImageClipboardWriter
    @Inject lateinit var playbackPositionRepository: PlaybackPositionRepository
    @Inject lateinit var keyBindingManager: com.sza.fastmediasorter.core.input.KeyBindingManager

    // S1114: standalone VR entry (this host has no VR badge; the transport-row button uses this).
    @Inject lateinit var vrCinemaLaunchManager: com.sza.fastmediasorter.ui.player.helpers.StandaloneVrCinemaLaunchManager
    @Inject lateinit var resolveOpenInFmsTargetUseCase: com.sza.fastmediasorter.domain.usecase.ResolveOpenInFmsTargetUseCase
    @Inject lateinit var sendToMenuManager: com.sza.fastmediasorter.ui.share.SendToMenuManager
    @Inject lateinit var fileOperationUseCase: com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
    // S0610: global destination list (no resource context) for the Copy/Move bottom panels.
    @Inject lateinit var getDestinationsUseCase: com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
    // S0393 wave-C: image edit dialog (rotate/flip/filters/adjust) use-cases.
    @Inject lateinit var rotateImageUseCase: com.sza.fastmediasorter.domain.usecase.RotateImageUseCase
    @Inject lateinit var flipImageUseCase: com.sza.fastmediasorter.domain.usecase.FlipImageUseCase
    @Inject lateinit var networkImageEditUseCase: com.sza.fastmediasorter.domain.usecase.NetworkImageEditUseCase
    @Inject lateinit var applyImageFilterUseCase: com.sza.fastmediasorter.domain.usecase.ApplyImageFilterUseCase
    @Inject lateinit var adjustImageUseCase: com.sza.fastmediasorter.domain.usecase.AdjustImageUseCase
    @Inject lateinit var capabilityAvailability: CapabilityAvailability
    // S0410: draw overlay collaborators (shared with the in-app player).
    @Inject lateinit var drawKeepExportHelper: com.sza.fastmediasorter.ui.player.helpers.DrawKeepExportHelper
    @Inject lateinit var mergeDrawOverlayUseCase: com.sza.fastmediasorter.domain.usecase.MergeDrawOverlayUseCase

    // S0390: screen-rotation toggle for the Group A rotate button; capability hidden without a sensor.
    private val screenRotationManager = ScreenRotationManager()
    private val hasAccelerometer: Boolean by lazy { screenRotationManager.isAccelerometerPresent(this) }

    // S0410: created lazily on the first Draw tap; back-press only consults it if already created.
    private var drawSaveHelper: StandaloneDrawSaveHelper? = null

    private fun ensureDrawHelper(): StandaloneDrawSaveHelper =
        drawSaveHelper ?: StandaloneDrawSaveHelper(
            activity = this,
            imageContainer = binding.mediaContentArea,
            toolbarRoot = binding.root.findViewById(R.id.draw_overlay_toolbar_stub),
            screenRotationManager = screenRotationManager,
            hasAccelerometer = hasAccelerometer,
            keepExportHelper = drawKeepExportHelper,
            mergeDrawOverlayUseCase = mergeDrawOverlayUseCase,
            imageCropManager = ImageCropManager(this, lifecycleScope, fileOperationUseCase),
            lifecycleScope = lifecycleScope,
            getCurrentFile = { viewModel.state.value.mediaFile },
            getDisplayedBitmap = { binding.photoView.drawable?.toBitmap() },
            getImageDisplayRect = { binding.photoView.displayRect },
            setDisplayedBitmap = { binding.photoView.setImageBitmap(it) },
            onDelete = { fileOperations.deleteCurrentFile() },
            onDrawModeChanged = { drawing -> setBottomPanelsHiddenForDraw(drawing) },
            // S0837: overwrite the launched screenshot on a default save, but only while the editor is
            // still on that file - a folder-paged neighbour must not be overwritten.
            getOverwriteTargetUri = {
                drawOverwriteSourceUri?.takeIf {
                    viewModel.state.value.mediaFile?.contentUri == it.toString()
                }
            },
        ).also { drawSaveHelper = it }

    /**
     * S0676: the draw editor mounts over the image, so the self-managed Copy/Move destination panels
     * must step aside while it is active (mirrors the in-app PlayerImmersiveModeManager). On enter the
     * current panel visibility is captured and both panels hidden; on exit it is restored verbatim, so a
     * read-only source that never showed the Move panel does not gain it back.
     */
    private fun setBottomPanelsHiddenForDraw(drawing: Boolean) {
        destinationPanelsSuppressed = drawing
        val copyPanel = binding.root.findViewById<View>(R.id.copyToPanel)
        val movePanel = binding.root.findViewById<View>(R.id.moveToPanel)
        if (drawing) {
            copyPanelVisibleBeforeDraw = copyPanel?.isVisible == true
            movePanelVisibleBeforeDraw = movePanel?.isVisible == true
            copyPanel?.isVisible = false
            movePanel?.isVisible = false
        } else {
            copyPanel?.isVisible = copyPanelVisibleBeforeDraw
            movePanel?.isVisible = movePanelVisibleBeforeDraw
        }
    }

    // S0676: remembers Copy/Move panel visibility across a draw session so exit restores the exact state.
    private var copyPanelVisibleBeforeDraw = false
    private var movePanelVisibleBeforeDraw = false

    // S0676/S0741: true while a fullscreen overlay suppresses the Copy/Move destination panels.
    // The populate coroutine can finish after the overlay has already hidden the panels; this gate
    // stops it from re-showing them until the overlay exits and restores the captured state.
    private var destinationPanelsSuppressed = false

    // S0393: Group A image editing reuses the shared seam-based PlayerCropDelegate (replaces the
    // standalone-only StandaloneImageEditController). The PlayerActionHost members below supply the
    // resolved editable file, overlay mount and in-place re-render hook.
    private val cropDelegate: PlayerCropDelegate by lazy {
        PlayerCropDelegate(
            host = this,
            imageCropManager = ImageCropManager(this, lifecycleScope, fileOperationUseCase),
        )
    }

    private val viewManager: StandaloneViewManager by lazy {
        StandaloneViewManager(
            activity = this,
            root = binding.root,
            lifecycleScope = lifecycleScope,
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
            settingsRepository = settingsRepository,
            playbackPositionRepository = playbackPositionRepository
            // binding intentionally omitted: this trimmed layout never opens document viewers.
        )
    }

    private var videoControlsManager: StandaloneVideoControlsManager? = null
    private var fullscreenManager: StandaloneFullscreenManager? = null

    // S1115: the fullscreen-exit overlay is added via <include> without an include-tag id, so it is not a
    // generated binding field (same reason PlayerBindingSafeViews resolves it by findViewById). Resolve
    // it lazily from the root once the layout is inflated.
    private val btnFullscreenExit: ImageButton by lazy {
        binding.root.findViewById(R.id.btnDocumentFullscreenExit)
    }
    // S0393 U1: Picture-in-Picture, ported from legacy StandalonePlayerActivity.
    private var pipManager: com.sza.fastmediasorter.ui.player.helpers.PictureInPictureManager? = null

    // S0393 wave-C: black-screen overlay (dim screen during video playback). Generic manager.
    private val blackScreenManager by lazy {
        com.sza.fastmediasorter.ui.player.helpers.BlackScreenOverlayManager(
            java.lang.ref.WeakReference(this),
            com.sza.fastmediasorter.ui.player.helpers.SystemBarsManager(this),
        )
    }

    // S0393 wave-C: TranslationManager only for its OCR recognition facade (extractTextOnly).
    private val ocrTranslationManager by lazy {
        com.sza.fastmediasorter.ui.player.helpers.TranslationManager(
            context = this,
            settingsRepository = settingsRepository,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.TranslationManager.TranslationCallback {
                override fun showError(message: String) = runOnUiThread {
                    Toast.makeText(this@PhotoVideoStandaloneActivity, message, Toast.LENGTH_SHORT).show()
                }
                override fun showModelDownloadPrompt(languageName: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
                    if (isFinishing || isDestroyed) { onCancel(); return }
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@PhotoVideoStandaloneActivity)
                        .setTitle(R.string.download_translation_model_title)
                        .setMessage(getString(R.string.download_translation_model_message, languageName))
                        .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
                        .setOnCancelListener { onCancel() }
                        .showBoundTo(this@PhotoVideoStandaloneActivity)
                }
            },
        )
    }

    // S0393 wave-C: OCR the displayed image and show extracted text in a scrollable, copyable dialog.
    private fun ocrCurrentImage() {
        if (!capabilityAvailability.isTranslationAvailable()) return
        val bitmap = binding.photoView.drawable?.toBitmap() ?: run {
            Toast.makeText(this, R.string.ocr_extract_image_failed, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            val sourceLang = com.sza.fastmediasorter.ui.player.helpers.TranslationManager
                .languageCodeToMLKit(settings.translationSourceLanguage)
            val text = withContext(Dispatchers.IO) { ocrTranslationManager.extractTextOnly(bitmap, sourceLang) }
            if (text != null && text.isNotBlank()) {
                com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog.show(
                    this@PhotoVideoStandaloneActivity,
                    title = getString(R.string.camera_ocr_pane_original),
                    message = text,
                    monospace = true,
                )
            } else {
                Toast.makeText(this@PhotoVideoStandaloneActivity, R.string.ocr_no_text_found, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // S0393 wave-C: OCR + translate the displayed image, show the translation in a dialog.
    private fun translateCurrentImage() {
        if (!capabilityAvailability.isTranslationAvailable()) return
        val bitmap = binding.photoView.drawable?.toBitmap() ?: run {
            Toast.makeText(this, R.string.ocr_extract_image_failed, Toast.LENGTH_SHORT).show(); return
        }
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            val src = com.sza.fastmediasorter.ui.player.helpers.TranslationManager
                .languageCodeToMLKit(settings.translationSourceLanguage)
            val tgt = com.sza.fastmediasorter.ui.player.helpers.TranslationManager
                .languageCodeToMLKit(settings.translationTargetLanguage)
            val result = withContext(Dispatchers.IO) { ocrTranslationManager.recognizeAndTranslate(bitmap, src, tgt) }
            if (result != null) {
                com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog.show(
                    this@PhotoVideoStandaloneActivity,
                    title = getString(R.string.translate),
                    message = result.second,
                    details = result.first,
                )
            } else {
                Toast.makeText(this@PhotoVideoStandaloneActivity, R.string.ocr_no_text_found, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // S0610/S0919: Print receiver for the unified «Send to..» menu (S0459 ADR-10). Stages the displayed
    // bitmap to a private temp PNG and dispatches through PrintDispatchActivity so One UI reaches the
    // system print UI (BaseActivity's wrapped context otherwise trips "Can print only from an activity").
    // Returns false only when no rendered image is available, so the menu gate fails cleanly.
    override fun printMediaFile(mediaFile: MediaFile): Boolean {
        val bitmap = binding.photoView.drawable?.toBitmap() ?: return false
        lifecycleScope.launch {
            val staged = withContext(Dispatchers.IO) { stageBitmapForPrint(bitmap) }
            val dispatched = staged != null && PrintDispatchActivity.startImage(
                context = this@PhotoVideoStandaloneActivity,
                file = staged,
                jobLabel = mediaFile.name,
                displayName = mediaFile.name,
                chooserTitle = getString(R.string.print_share_chooser_title),
                fallbackMessage = getString(R.string.print_fallback_to_share),
                unavailableMessage = getString(R.string.error_print_unavailable),
            )
            if (!dispatched) {
                Toast.makeText(
                    this@PhotoVideoStandaloneActivity,
                    R.string.error_print_unavailable,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        return true
    }

    // Single-file staging dir cleared before each write - bounds temp usage to one PNG; cacheDir is
    // OS-reclaimable so no explicit lifecycle deletion is needed.
    private fun stageBitmapForPrint(bitmap: Bitmap): File? = runCatching {
        val dir = File(cacheDir, "print_tmp").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        File(dir, "print_${System.nanoTime()}.png").also { target ->
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PRINT_TEMP_PNG_QUALITY, out)
            }
        }
    }.getOrElse { error ->
        Timber.e(error, "PhotoVideoStandaloneActivity: failed to stage bitmap for print")
        null
    }

    // S0393 wave-C: capture the current video frame from the TextureView and save it to Pictures.
    private fun saveCurrentFrame() {
        val texture = findTextureView(binding.playerView) ?: run {
            Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_SHORT).show(); return
        }
        val bitmap = runCatching { texture.bitmap }.getOrNull() ?: run {
            Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_SHORT).show(); return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val name = "frame_${(viewModel.state.value.mediaFile?.name ?: "video").substringBeforeLast('.')}_${System.nanoTime()}.jpg"
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
            }
            val ok = runCatching {
                val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching false
                contentResolver.openOutputStream(uri)?.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
                true
            }.getOrDefault(false)
            // S0470: when enabled, also place the extracted frame on the system clipboard. This host has
            // the live bitmap (no encoded temp file), so copy it as a lossless PNG via the shared role.
            val copiedToClipboard = if (settingsRepository.getSettings().first().videoFrameCopyToClipboard) {
                imageClipboardWriter.copyBitmap(bitmap)
            } else false
            withContext(Dispatchers.Main) {
                Toast.makeText(this@PhotoVideoStandaloneActivity,
                    if (ok) R.string.save_frame_saved_to_downloads else R.string.error_unknown, Toast.LENGTH_SHORT).show()
                if (copiedToClipboard) {
                    Toast.makeText(this@PhotoVideoStandaloneActivity,
                        R.string.video_frame_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun findTextureView(view: android.view.View?): android.view.TextureView? = when (view) {
        is android.view.TextureView -> view
        is android.view.ViewGroup -> (0 until view.childCount)
            .firstNotNullOfOrNull { findTextureView(view.getChildAt(it)) }
        else -> null
    }

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    // S0393 wave-C: sleep timer - pause playback after the chosen interval.
    private fun showSleepTimerDialog() {
        val minutes = intArrayOf(15, 30, 45, 60)
        val labels = minutes.map { "$it ${getString(R.string.minutes)}" }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_sleep_timer)
            .setItems(labels) { _, which ->
                sleepTimerJob?.cancel()
                sleepTimerJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(minutes[which] * 60_000L)
                    viewManager.getExoPlayer()?.pause()
                    Toast.makeText(this@PhotoVideoStandaloneActivity, R.string.menu_sleep_timer, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundTo(this@PhotoVideoStandaloneActivity)
    }
    private var trackSelectionManager: VideoTrackSelectionManager? = null
    private var videoTouchDelegate: StandaloneVideoTouchDelegate? = null

    // S0908: kept so onDestroy() can remove this exact instance before viewManager.release().
    private var tracksChangedListener: Player.Listener? = null
    private var playerSettingsManager: StandalonePlayerSettingsManager? = null
    private lateinit var keyboardHandler: PlayerKeyboardHandler

    private val pagingControls: StandalonePagingControlsBinder by lazy {
        StandalonePagingControlsBinder(
            viewModel = viewModel,
            btnPrev = binding.btnPagePrev,
            btnNext = binding.btnPageNext,
            btnRandom = binding.btnPageRandom,
            btnSlideshow = binding.btnPageSlideshow,
        )
    }

    /** Path of the file last handed to the viewManager; lets folder paging re-render on change. */
    private var lastShownPath: String? = null

    /** S0763: cached 3D/VR master-toggle state; updated by the settings collector in [observeData]. */
    private var cached3dVrEnabled = true

    /** One-shot guard so an [EXTRA_AUTO_ACTION] launch fires its action a single time. */
    private var autoActionConsumed = false

    /**
     * S0837: the launched source URI to overwrite on a default draw save. Non-null only for the
     * screenshot OPEN_IN_DRAW gesture ([EXTRA_DRAW_OVERWRITE_SOURCE]); null otherwise.
     */
    private var drawOverwriteSourceUri: Uri? = null

    /** Set by the crop-and-share auto-action; consumed on crop success, cleared on crop cancel. */
    private var pendingShareAfterCrop = false

    /** Backs the runtime [supportsFolderPaging] capability; updated from VM state. */
    private var folderPagingEnabled = false

    private val fileOperations: StandaloneFileOperationsHandler by lazy {
        StandaloneFileOperationsHandler(
            activity = this,
            root = binding.root,
            getCurrentMediaFile = { viewModel.state.value.mediaFile },
            resolveOpenInFmsTarget = resolveOpenInFmsTargetUseCase,
            onRenameComplete = { newUri, newName -> viewModel.onRenameComplete(newUri, newName) },
            updateAudioMediaItem = { /* audio is a separate lane - never handled here */ },
            batchDeleteLauncher = batchDeleteLauncher,
            recoverableDeleteLauncher = recoverableDeleteLauncher,
            sendToMenuManager = sendToMenuManager,
            getCurrentSettings = { settingsRepository.getSettings().first() },
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            onPickCustomFolderForCopy = {
                pendingCustomPathOp = com.sza.fastmediasorter.domain.model.FileOperationType.COPY
                customPathPickerLauncher.launch(null)
            },
        )
    }

    // S0610: Copy/Move destination panels, reusing the shared in-app manager bound to this layout root.
    // No resource context: getCurrentResourceId returns -1 so the global destination list is shown intact.
    private val destinationButtonsManager: com.sza.fastmediasorter.ui.player.DestinationButtonsManager by lazy {
        com.sza.fastmediasorter.ui.player.DestinationButtonsManager(
            root = binding.root,
            settingsRepository = settingsRepository,
            getDestinationsUseCase = getDestinationsUseCase,
            lifecycleScope = lifecycleScope,
            callback = object : com.sza.fastmediasorter.ui.player.DestinationButtonsManager.DestinationButtonsCallback {
                override fun onCopyClicked(destination: MediaResource) = fileOperations.copyCurrentFileTo(destination)
                override fun onMoveClicked(destination: MediaResource) = fileOperations.moveCurrentFileTo(destination)
                override fun onCustomPathPickerRequested(operationType: com.sza.fastmediasorter.domain.model.FileOperationType) {
                    pendingCustomPathOp = operationType
                    customPathPickerLauncher.launch(null)
                }
                override fun getCurrentResourceId(): Long = -1L
                override fun onUpdateCommandAvailability() { /* panels are self-managed in standalone */ }
                override fun shouldShowDestinationPanels(): Boolean =
                    viewModel.state.value.mediaFile != null && !destinationPanelsSuppressed
            },
            shouldNumberSlots = { false },
            slotKeyGlyph = { null },
        )
    }

    // S0438: a player host keeps the screen on when either the global or the dependent player setting is on.
    override fun keepScreenAwakeFor(settings: AppSettings): Boolean =
        settings.preventSleep || settings.keepScreenOnPlayer

    override fun getViewBinding(): ActivityStandalonePhotoVideoBinding =
        ActivityStandalonePhotoVideoBinding.inflate(layoutInflater)

    // This activity owns its immersive insets handling - skip global edge-to-edge.
    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun getInitialFocusView(): View = binding.btnBack

    override fun setupViews() {
        setupWindowAndInsets()
        setupCloseButton()
        setupBackPressHandler()
        setupFileOperationButtons()
        pagingControls.setupClicks()
        setupKeyboardHandler()
        parseIncomingIntent()
    }

    private fun setupWindowAndInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // S0920: keep the OS status bar visible on the dark player chrome (see StandaloneSystemBars).
        com.sza.fastmediasorter.ui.player.helpers.StandaloneSystemBars.showStatusBarWithLightIcons(this)
        // Pad the command panel for status/caption bar (top) + nav bar (left/right in landscape)
        // so its buttons stay inside the system-bar safe area (Rule 18).
        ViewCompat.setOnApplyWindowInsetsListener(binding.topCommandPanel) { view, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.captionBar()
            )
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(nav.left, top.top, nav.right, view.paddingBottom)
            insets
        }
        // S0610: the Copy/Move panels container is the bottom-most child, so the nav-bar inset moves
        // here. When the panels are GONE the 0-height container still reserves the nav gap, keeping the
        // ExoPlayer button row above the nav bar; when visible, the destination grids clear it too.
        binding.root.findViewById<View>(R.id.bottomPanelsContainer)?.let { panels ->
            ViewCompat.setOnApplyWindowInsetsListener(panels) { view, insets ->
                val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.setPadding(0, 0, 0, nav.bottom)
                insets
            }
        }
        binding.topCommandPanel.post { binding.topCommandPanel.requestApplyInsets() }
    }

    private fun setupCloseButton() {
        binding.btnBack.setImageResource(R.drawable.ic_clear)
        binding.btnBack.setOnClickListener { finish() }
        binding.topCommandPanel.isVisible = true
        updateFullscreenExitButtonVisibility()
    }

    // S1115: the overlay fullscreen-exit button is the only visible way back to the command panel while
    // a video plays fullscreen (panel hidden). Show it only for video with the panel hidden; images/gifs
    // and the commands-visible state keep it gone. Call after every command-panel visibility change.
    private fun updateFullscreenExitButtonVisibility() {
        val isVideoFullscreen = !binding.topCommandPanel.isVisible &&
            viewModel.state.value.mediaType == MediaType.VIDEO
        btnFullscreenExit.isVisible = isVideoFullscreen
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // S0410: back cancels an active draw session before leaving the viewer.
                if (drawSaveHelper?.handleBackPress() == true) return
                finish()
            }
        })
    }

    private fun setupFileOperationButtons() {
        // S0920: wire the Copy/Move panel headers so a header tap expands/collapses its grid.
        destinationButtonsManager.bindHeaderToggles()
        binding.btnDeleteCmd.isVisible = true
        binding.btnDeleteCmd.setOnClickListener { fileOperations.deleteCurrentFile() }
        binding.btnShareCmd.isVisible = true
        binding.btnShareCmd.setOnClickListener { fileOperations.shareCurrentFile() }
        binding.btnFavorite.isVisible = true
        binding.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }
        binding.btnInfoCmd.isVisible = true
        binding.btnInfoCmd.setOnClickListener { showFileInfo() }
        // Rename stays hidden until the async capability check completes.
        binding.btnRenameCmd.isVisible = false
        binding.btnRenameCmd.setOnClickListener { fileOperations.showStandaloneRenameDialog() }
        // S0390: Group A bar buttons; visibility is driven from VM state in observeData.
        binding.btnEditCrop.setOnClickListener {
            cropDelegate.enterCropMode(ImageCropManager.CropMode.CROP)
        }
        binding.btnEditRotate.setOnClickListener { viewModel.toggleRotationSensor() }
        // S0393: the rotation toggle is a screen-orientation control (locks/unlocks the sensor),
        // independent of the file, so it is gated on the device sensor only - not on
        // editableImageFile, which would hide it for non-local content-URI images.
        binding.btnEditRotate.isVisible = hasAccelerometer
        binding.btnOverflowMenu.isVisible = true
        binding.btnOverflowMenu.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.inflate(R.menu.overflow_menu_standalone_player)
            // S1407: icons off by default on PopupMenu - match the embedded player's rendering.
            popup.applyStandaloneOverflowIcons()
            // S0393: crop/compress/edit/Lens overwrite or share the source file, so they need a
            // resolved local writable image (editableImageFile). OCR/translate/print operate purely on
            // the displayed bitmap, so they only need a rendered image - gate those on the drawable,
            // which also restores them for non-local content-URI images (e.g. a share from another app),
            // matching the in-app player where they are gated on isImage, not on write access.
            val editable = viewModel.editableImageFile.value != null
            val hasBitmap = binding.photoView.drawable != null
            // S0410: crop-to-file / compress produce a NEW file, so they apply to any static image -
            // a non-local source is materialized to a cache file on tap (ensureEditableImage). Gate
            // them on the static-image type (mirrors the in-app isStaticBitmap); edit-in-place and
            // Lens still need a resolved local file (editableImageFile).
            val isStaticImage = viewModel.state.value.mediaType == MediaType.IMAGE
            popup.menu.findItem(R.id.menu_edit_crop_to_file).isVisible = isStaticImage
            popup.menu.findItem(R.id.menu_edit_compress).isVisible = isStaticImage
            popup.menu.findItem(R.id.menu_draw_overlay).isVisible = isStaticImage
            popup.menu.findItem(R.id.menu_edit_image).isVisible = editable
            // S0459 §11.7: the per-file Google Lens overflow item is dropped - the unified Send-to
            // menu (btnShareCmd -> SendToMenuManager) already offers the Lens receiver for image/gif.
            // The item stays in the shared menu (other hosts reference it), so hide it explicitly.
            popup.menu.findItem(R.id.menu_google_lens).isVisible = false
            popup.menu.findItem(R.id.menu_ocr_image).isVisible =
                hasBitmap && capabilityAvailability.isTranslationAvailable()
            popup.menu.findItem(R.id.menu_translate_image).isVisible =
                hasBitmap && capabilityAvailability.isTranslationAvailable()
            popup.menu.findItem(R.id.menu_image_text_settings).isVisible =
                hasBitmap && capabilityAvailability.isTranslationAvailable()
            // S0610: print is now a receiver of the unified «Send to..» menu (btnShareCmd), so the
            // isolated overflow print item is dropped for this host to keep a single invocation point.
            popup.menu.findItem(R.id.menu_print).isVisible = false
            val isVideo = viewModel.state.value.mediaType == MediaType.VIDEO
            popup.menu.findItem(R.id.menu_black_screen).isVisible = isVideo
            popup.menu.findItem(R.id.menu_save_frame).isVisible = isVideo
            popup.menu.findItem(R.id.menu_sleep_timer).isVisible = isVideo
            // S0995: manual visual rotation applies to every surface this host renders (image/gif/video).
            val rotatable = viewModel.state.value.mediaType.let {
                it == MediaType.IMAGE || it == MediaType.GIF || it == MediaType.VIDEO
            }
            popup.menu.findItem(R.id.menu_rotate_content_standalone).let { rotateItem ->
                rotateItem.isVisible = rotatable
                // S0995: a11y description distinct from the visible title (direction/magnitude).
                androidx.core.view.MenuItemCompat.setContentDescription(
                    rotateItem, getString(R.string.rotate_content_90_desc)
                )
            }
            // S1364: the counter-clockwise twin follows the same rule as its forward partner.
            popup.menu.findItem(R.id.menu_rotate_content_ccw_standalone).let { rotateCcwItem ->
                rotateCcwItem.isVisible = rotatable
                androidx.core.view.MenuItemCompat.setContentDescription(
                    rotateCcwItem,
                    getString(R.string.rotate_content_ccw_desc)
                )
            }
            // S1364: parity items. Each mirrors the gate of the inline bar button that already
            // implements it, so the menu entry can never offer what the button would not.
            popup.menu.findItem(R.id.menu_edit_crop_standalone).isVisible = editable
            popup.menu.findItem(R.id.menu_rename_standalone).isVisible = binding.btnRenameCmd.isVisible
            popup.menu.findItem(R.id.menu_autorotate_standalone).let { autoRotateItem ->
                autoRotateItem.isVisible = hasAccelerometer
                autoRotateItem.isChecked = viewModel.rotationSensorEnabled.value
            }
            // S1364: hide the whole editing section when nothing inside it applies, because Android
            // does not remove an empty submenu - the same rule the embedded player follows.
            popup.menu.findItem(R.id.menu_edit_section_standalone).let { section ->
                val sub = section.subMenu
                section.isVisible = (0 until (sub?.size() ?: 0)).any { sub?.getItem(it)?.isVisible == true }
            }
            popup.menu.findItem(R.id.menu_youtube_music).isVisible = false // audio host only
            popup.menu.findItem(R.id.menu_lyrics).isVisible = false // audio host only
            popup.menu.findItem(R.id.menu_playback_speed).isVisible = false // audio host (video uses the control dialog)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_open_in_fms -> { fileOperations.openInFms(); true }
                    R.id.menu_edit_crop_to_file -> {
                        // S0410: materialize a non-local source to a cache file first, then crop.
                        lifecycleScope.launch {
                            viewModel.ensureEditableImage()
                            cropDelegate.enterCropMode(ImageCropManager.CropMode.CROP_TO_FILE)
                        }
                        true
                    }
                    R.id.menu_edit_compress -> {
                        lifecycleScope.launch {
                            viewModel.ensureEditableImage()
                            cropDelegate.startCompressedCopy()
                        }
                        true
                    }
                    R.id.menu_image_text_settings -> {
                        com.sza.fastmediasorter.ui.dialog.TranslationSettingsDialog.show(
                            context = this,
                            lifecycleOwner = this,
                            settingsRepository = settingsRepository,
                        )
                        true
                    }
                    R.id.menu_edit_image -> { openImageEditDialog(); true }
                    R.id.menu_draw_overlay -> {
                        ensureDrawHelper().enterDrawMode(); true
                    }
                    R.id.menu_ocr_image -> { ocrCurrentImage(); true }
                    R.id.menu_translate_image -> { translateCurrentImage(); true }
                    R.id.menu_save_frame -> { saveCurrentFrame(); true }
                    R.id.menu_sleep_timer -> { showSleepTimerDialog(); true }
                    R.id.menu_black_screen -> { blackScreenManager.show(); true }
                    R.id.menu_rotate_content_standalone -> {
                        viewModel.rotateSession90()
                        val newAngle = viewModel.state.value.sessionRotationAngle
                        photoVideoHandle.setContentRotationDegrees(newAngle)
                        true
                    }
                    R.id.menu_rotate_content_ccw_standalone -> {
                        viewModel.rotateSessionCounter90()
                        photoVideoHandle.setContentRotationDegrees(viewModel.state.value.sessionRotationAngle)
                        true
                    }
                    R.id.menu_edit_crop_standalone -> {
                        cropDelegate.enterCropMode(ImageCropManager.CropMode.CROP)
                        true
                    }
                    R.id.menu_rename_standalone -> {
                        fileOperations.showStandaloneRenameDialog()
                        true
                    }
                    R.id.menu_autorotate_standalone -> {
                        viewModel.toggleRotationSensor()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    // S0393: unified onto the shared StandaloneKeyboardManager (was PhotoVideoStandaloneKeyboardManager).
    private fun setupKeyboardHandler() {
        keyboardHandler = StandaloneKeyboardManager(
            keyBindingManager = keyBindingManager,
            getActivePlayer = { viewManager.getExoPlayer() },
            getCurrentMediaType = { viewModel.state.value.mediaFile?.type },
            onDelete = { fileOperations.deleteCurrentFile() },
            onExit = { finish() },
            onShowRename = { fileOperations.showStandaloneRenameDialog() },
            onShowInfo = { showFileInfo() },
            onToggleCommandPanel = {
                binding.topCommandPanel.isVisible = !binding.topCommandPanel.isVisible
                updateFullscreenExitButtonVisibility()
            },
            onToggleFullscreen = { fullscreenManager?.toggleFullscreen() },
            onToggleFavourite = { viewModel.toggleFavorite() },
            onNextFile = { viewModel.pageNext() },
            onPreviousFile = { viewModel.pagePrevious() },
            onToggleSlideshow = { viewModel.toggleSlideshow() },
            onShowContextMenu = {
                binding.topCommandPanel.isVisible = !binding.topCommandPanel.isVisible
                updateFullscreenExitButtonVisibility()
            },
            onToggleRotationSensor = { viewModel.toggleRotationSensor() },
            onShowHelp = {
                com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment.show(
                    supportFragmentManager,
                    com.sza.fastmediasorter.ui.common.input.UiSurface.PLAYER
                )
            },
        ).handler
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (::keyboardHandler.isInitialized &&
            keyboardHandler.handleKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (::keyboardHandler.isInitialized &&
            keyboardHandler.handlePointerEvent(window.decorView, event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    // Standalone player surface routes motion through its own keyboard/pointer handler; the shared
    // gamepad navigation layer must not also move focus here. S0508.
    override fun shouldHandleGamepadNavigation(): Boolean = false

    private fun showFileInfo() {
        val file = viewModel.state.value.mediaFile ?: return
        if (isFinishing || isDestroyed) return
        FileInfoDialog(
            this,
            file,
            smbClient.get(),
            sftpClient.get(),
            ftpClient.get(),
            credentialsRepository.get(),
            unifiedCache.get(),
            downloadNetworkFileUseCase = null,
            audioMetadataLoader = null,
            audioMetadataCacheRepository = null
        ).show()
    }

    /** Runs the one-shot [EXTRA_AUTO_ACTION] requested at launch (draw / translate), once, for images only. */
    private fun maybeRunAutoAction(type: MediaType) {
        if (autoActionConsumed || type != MediaType.IMAGE) return
        when (intent?.getStringExtra(EXTRA_AUTO_ACTION)) {
            AUTO_ACTION_DRAW -> {
                autoActionConsumed = true
                ensureDrawHelper().enterDrawMode()
            }
            AUTO_ACTION_SEND_TO -> {
                autoActionConsumed = true
                fileOperations.shareCurrentFile()
            }
            AUTO_ACTION_CROP_AND_SHARE -> {
                autoActionConsumed = true
                launchCropAndShareAutoAction()
            }
        }
    }

    private fun launchCropAndShareAutoAction() {
        lifecycleScope.launch {
            viewModel.ensureEditableImage()
            if (viewModel.editableImageFile.value == null) {
                pendingShareAfterCrop = false
                return@launch
            }
            pendingShareAfterCrop = true
            cropDelegate.enterCropMode(ImageCropManager.CropMode.CROP)
        }
    }

    private fun parseIncomingIntent() {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> intent?.data
        }
        if (uri == null) {
            Toast.makeText(this, R.string.error_opening_file_simple, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (DefaultPlayerProbe.isProbe(uri)) {
            Timber.d("PhotoVideoStandalone: ignoring default-player probe URI, finishing")
            finish()
            return
        }
        // S0837: remember the screenshot source so a default draw save overwrites it in place.
        if (intent?.getBooleanExtra(EXTRA_DRAW_OVERWRITE_SOURCE, false) == true) {
            drawOverwriteSourceUri = uri
        }
        val displayName = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            Timber.w(e, "PhotoVideoStandalone: failed to query display name")
            null
        } ?: uri.lastPathSegment
        // Folder paging enumerates only image/gif/video neighbours - the types this host renders.
        viewModel.setHostSupportedTypes(setOf(MediaType.IMAGE, MediaType.GIF, MediaType.VIDEO))
        viewModel.loadFromUri(uri, intent?.type, displayName)
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state ->
            binding.progressBar.isVisible = state.isLoading
            if (state.isLoading) return@collectOnLifecycle
            state.errorMessage?.let { error ->
                Toast.makeText(this@PhotoVideoStandaloneActivity, error, Toast.LENGTH_SHORT).show()
                finish()
                return@collectOnLifecycle
            }
            val file = state.mediaFile ?: return@collectOnLifecycle
            val type = state.mediaType ?: return@collectOnLifecycle
            if (type != MediaType.IMAGE && type != MediaType.GIF && type != MediaType.VIDEO) {
                // Audio + documents + binaries are other lanes - reject here, mirroring TextStandalone.
                Toast.makeText(
                    this@PhotoVideoStandaloneActivity,
                    R.string.unsupported_format_use_external_player,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@collectOnLifecycle
            }
            if (file.path != lastShownPath) {
                val onVideoReady: ((PlayerView) -> Unit)? =
                    if (type == MediaType.VIDEO) ({ pv -> setupVideoControls(pv) }) else null
                // S1041: defer the launch auto-action (OCR/translate) until the image drawable is
                // decoded - firing it synchronously after show() raced the async Glide load and read a
                // null bitmap, spuriously toasting ocr_extract_image_failed on the gesture-screenshot flow.
                val onImageReady: (() -> Unit)? =
                    if (type == MediaType.IMAGE) ({ maybeRunAutoAction(type) }) else null
                viewManager.show(file, type, onVideoReady, onImageReady)
                lastShownPath = file.path
                // S0610: build the Copy/Move destination grids for the shown file (runs in a coroutine
                // inside the manager, so it does not delay first render).
                destinationButtonsManager.populateDestinationButtons()
            }
            folderPagingEnabled = state.supportsFolderPaging
            pagingControls.applyState(state.supportsFolderPaging, state.isSlideshowActive)
            updateRenameButtonVisibility()
        }
        collectOnLifecycle(viewModel.isFavorite) { isFav ->
            binding.btnFavorite.setImageResource(
                if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            binding.btnFavorite.contentDescription = getString(
                if (isFav) R.string.cd_remove_from_favorites else R.string.cd_add_to_favorites
            )
        }
        collectOnLifecycle(viewModel.messageFlow) { message ->
            Toast.makeText(this@PhotoVideoStandaloneActivity, message, Toast.LENGTH_SHORT).show()
        }
        // S0390: gate Group A image actions on the editable-image state × the type-specific capability.
        collectOnLifecycle(viewModel.editableImageFile) { editFile ->
            // Crop overwrites the source in place, so it needs a writable local image. The rotation
            // toggle is decoupled (set once in setupFileOperationButtons) - it needs no file.
            binding.btnEditCrop.isVisible = editFile != null && supportsTypeSpecificActions
        }
        collectOnLifecycle(viewModel.rotationSensorEnabled) { enabled ->
            if (hasAccelerometer) {
                screenRotationManager.apply(
                    activity = this@PhotoVideoStandaloneActivity,
                    followSystem = false,
                    sensorEnabled = enabled,
                    hasAccelerometer = true,
                )
            }
            binding.btnEditRotate.setImageResource(
                if (enabled) R.drawable.ic_rotation_unlocked else R.drawable.ic_rotation_locked
            )
            binding.btnEditRotate.contentDescription = getString(
                if (enabled) R.string.rotation_toggle_sensor_on_desc
                else R.string.rotation_toggle_sensor_off_desc
            )
        }
        // S0763: keep the 3D/VR master-toggle snapshot current for the playback control dialog.
        collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            cached3dVrEnabled = !settings.disable3dVr
        }
    }

    private fun updateRenameButtonVisibility() = fileOperations.updateRenameButtonVisibility()

    // ── Video controls (mirrors StandalonePlayerActivity's VIDEO subset) ──────────

    // S0393 wave-C: full image edit (rotate/flip/filters/adjust) via the generic ImageEditDialog;
    // onEditComplete re-decodes the in-place-edited file through the existing seam reload path.
    private fun openImageEditDialog() {
        val file = viewModel.editableImageFile.value ?: return
        if (isFinishing || isDestroyed) return
        com.sza.fastmediasorter.ui.dialog.ImageEditDialog(
            context = this,
            imagePath = file.path,
            rotateImageUseCase = rotateImageUseCase,
            flipImageUseCase = flipImageUseCase,
            networkImageEditUseCase = networkImageEditUseCase,
            applyImageFilterUseCase = applyImageFilterUseCase,
            adjustImageUseCase = adjustImageUseCase,
            onEditComplete = { viewModel.state.value.mediaFile?.let { viewManager.reloadImage(it) } },
        ).show()
    }

    // S0393 U2: per-file playback-control dialog (speed / track / subtitles / hue / brightness),
    // ported from legacy StandalonePlayerActivity.showPlaybackControlDialog.
    private fun showPlaybackControlDialog() {
        if (isFinishing || isDestroyed) return
        val type = viewModel.state.value.mediaType
        if (type != MediaType.VIDEO && type != MediaType.AUDIO) return
        val fm = supportFragmentManager
        if (fm.isStateSaved) return
        if (fm.findFragmentByTag(PlaybackControlDialogFragment.TAG) != null) return
        PlaybackControlDialogFragment().show(fm, PlaybackControlDialogFragment.TAG)
    }

    // S0393 U1: wire Picture-in-Picture once a video PlayerView is ready (mirrors legacy host).
    private fun setupPictureInPicture(pv: PlayerView) {
        if (pipManager != null) return
        val manager = com.sza.fastmediasorter.ui.player.helpers.PictureInPictureManager(
            activity = this,
            playerView = pv,
            chromeToHide = listOf(binding.topCommandPanel),
            getPlayer = { viewManager.getExoPlayer() },
            onPlay = { viewManager.getExoPlayer()?.play() },
            onPause = { viewManager.getExoPlayer()?.pause() },
            isVideoPlaying = { viewManager.getExoPlayer()?.isPlaying == true },
        )
        pipManager = manager
        collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            manager.setupPipButton(settings.enablePictureInPicture, isAudio = false)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pipManager?.onUserLeaveHint(pipManager?.isEnabled ?: false)
    }

    // Single-arg override kept for legacy minSdk 23: on API 24-25 the framework calls only this
    // signature (two-arg added in API 26), so migrating away would drop PiP handling on those devices.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        pipManager?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    // S0393: reapply window insets after rotation (configChanges handles orientation here, so the
    // activity is not recreated) - ported from legacy host to keep the panel clear of system bars.
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.topCommandPanel.post { binding.topCommandPanel.requestApplyInsets() }
    }

    private fun setupVideoControls(pv: PlayerView) {
        val controlsManager = StandaloneVideoControlsManager(
            playerView = pv,
            callback = object : StandaloneVideoControlsManager.StandaloneVideoControlsCallback {
                // S0393 U2: ported from legacy StandalonePlayerActivity - the dialog reads this host
                // via PlayerHostCapabilities + videoPlayerHandle (both already implemented).
                override fun showPlaybackControlDialog() = this@PhotoVideoStandaloneActivity.showPlaybackControlDialog()
                // S1114: standalone VR entry from the transport row (host has no VR badge of its own).
                override fun onVrLaunchClicked() {
                    viewModel.state.value.mediaFile?.let { vrCinemaLaunchManager.launch(it) }
                }
                override fun isVrEntryAvailable(): Boolean =
                    vrCinemaLaunchManager.isAvailable &&
                        viewModel.state.value.mediaType == MediaType.VIDEO
            }
        )
        controlsManager.setupVideoControls()
        videoControlsManager = controlsManager
        setupPictureInPicture(pv)

        val fsManager = StandaloneFullscreenManager(this)
        fullscreenManager = fsManager
        // S1115: the overlay exit button restores the command panel (leaves fullscreen) - the same path
        // as the edge-swipe callback below, but visible and discoverable.
        btnFullscreenExit.setOnClickListener {
            UserActionLogger.logButtonClick("FullscreenExit", "PhotoVideoStandaloneActivity")
            fsManager.exitFullscreenWithPanel(binding.topCommandPanel) {
                updateFullscreenExitButtonVisibility()
            }
        }
        // S0920: honor "open video in fullscreen" instead of always hiding the system bars.
        // When on, enter true fullscreen - system bars AND the command panel hidden together, so the
        // panel stays the single source of truth for "in fullscreen"; when off, keep both visible
        // (commands mode). An edge-swipe restores the panel via setupTransientBarsExitCallback below.
        lifecycleScope.launch {
            val openFullscreen = settingsRepository.getSettings().first().openVideoInFullscreen
            if (openFullscreen) {
                fsManager.enterFullscreenWithPanel(binding.topCommandPanel) {
                    updateFullscreenExitButtonVisibility()
                }
            }
        }
        fsManager.setupTransientBarsExitCallback {
            if (!binding.topCommandPanel.isVisible) binding.topCommandPanel.isVisible = true
            updateFullscreenExitButtonVisibility()
        }

        val trackManager = VideoTrackSelectionManager(
            getPlayer = { viewManager.getExoPlayer() },
            getPlayerView = { pv }
        )
        trackSelectionManager = trackManager

        playerSettingsManager = StandalonePlayerSettingsManager(
            activity = this,
            playerView = pv,
            settingsRepository = settingsRepository,
            trackSelectionManager = trackManager,
            lifecycleScope = lifecycleScope
        )

        val tracksListener = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                controlsManager.updateTrackButtonsVisibility(
                    hasMultipleAudio = trackManager.hasMultipleAudioTracks(),
                    hasSubtitles = trackManager.hasSubtitleTracks()
                )
            }
        }
        tracksChangedListener = tracksListener
        viewManager.getExoPlayer()?.addListener(tracksListener)

        val touchDelegate = StandaloneVideoTouchDelegate(
            activity = this,
            playerView = pv,
            rootView = binding.root,
            getBrightnessProgress = { viewManager.getBrightnessProgress() },
            setBrightnessProgress = { progress -> viewManager.setBrightnessProgress(progress) },
            getBrightnessPercentOffset = { viewManager.getBrightnessPercentOffset() }
        )
        touchDelegate.attachIndicator(binding.tvVideoGestureIndicator)
        videoTouchDelegate = touchDelegate

        @SuppressLint("ClickableViewAccessibility")
        val touchListener = View.OnTouchListener { _, event ->
            val consumed = touchDelegate.handleTouchEvent(event)
            if (!consumed && event.actionMasked == MotionEvent.ACTION_UP) pv.performClick()
            consumed
        }
        pv.setOnTouchListener(touchListener)
        Timber.d("PhotoVideoStandalone: video controls setup complete")
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResumeWithViews() {
        viewManager.onResume()
    }

    override fun onPause() {
        // S0393 U1: keep playback running when the activity pauses to enter PiP.
        val isInPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!isInPip) viewManager.onPause()
        super.onPause()
    }

    // S0893: API24+ multi-window release edge - release the video codec while backgrounded.
    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) viewManager.onStopVideo()
    }

    // S0893: rebuild only the video path - audio/image/document types have nothing to recreate here.
    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && viewModel.state.value.mediaType == MediaType.VIDEO) {
            viewModel.state.value.mediaFile?.let { file ->
                viewManager.show(file, MediaType.VIDEO, onVideoReady = { pv -> setupVideoControls(pv) })
            }
        }
    }

    override fun onDestroy() {
        fullscreenManager?.exitFullscreen()
        fullscreenManager = null
        videoControlsManager = null
        trackSelectionManager = null
        videoTouchDelegate = null
        playerSettingsManager = null
        pipManager?.release()
        pipManager = null
        viewManager.getExoPlayer()?.let { player -> tracksChangedListener?.let(player::removeListener) }
        tracksChangedListener = null
        viewManager.release()
        super.onDestroy()
    }

    // ── PlayerActionHost (S0393) ──────────────────────────────────────────────
    // Single external file, no resource context. actionCurrentFile is the URI resolved to a writable
    // local image path (editableImageFile), which crop needs to overwrite in place / save a copy.

    override val hostActivity: AppCompatActivity get() = this
    override val hostScope: LifecycleCoroutineScope get() = lifecycleScope
    override val actionCurrentFile: MediaFile? get() = viewModel.editableImageFile.value
    override val actionCurrentResource: MediaResource? get() = null
    override val overlayMountTarget: ViewGroup get() = binding.mediaContentArea
    override val imagePinchTarget: View get() = binding.photoView
    // Null when photoView has no drawable; an empty rect makes the crop delegate fall back to the
    // legacy full-view mapping instead of dividing by a zero-size rect.
    override fun imageDisplayRect(): RectF = binding.photoView.displayRect ?: RectF()
    // S0410: the draw overlay merges its strokes onto this base bitmap.
    override val displayedBitmap: Bitmap? get() = binding.photoView.drawable?.toBitmap()

    override fun reloadCurrentImageInPlace() {
        viewModel.state.value.mediaFile?.let { viewManager.reloadImage(it) }
        if (pendingShareAfterCrop) {
            // Crop success path: share the cropped local copy, not the original (possibly content://) file.
            pendingShareAfterCrop = false
            viewModel.editableImageFile.value?.let { fileOperations.shareLocalCopy(it) }
        }
    }

    override fun onCropFlowCancelled() {
        pendingShareAfterCrop = false
    }

    override fun onFileSavedInFolder(savedPath: String) {
        val fileName = savedPath.substringAfterLast('/')
        Toast.makeText(this, getString(R.string.crop_file_created, fileName), Toast.LENGTH_LONG).show()
    }

    // ── PlayerHostCapabilities ──────────────────────────────────────────────────

    override val supportsListNavigation: Boolean = false
    // Slideshow auto-advance runs over the enumerated folder list, so it tracks folder paging.
    override val supportsSlideshow: Boolean get() = folderPagingEnabled
    override val supportsPersistentAudio: Boolean = false
    override val supportsCast: Boolean = false
    override val supportsCommandPanelFolding: Boolean = false
    override val supportsFolderPaging: Boolean get() = folderPagingEnabled

    override val currentMediaFile: StateFlow<MediaFile?> by lazy {
        viewModel.state.map { it.mediaFile }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, viewModel.state.value.mediaFile)
    }

    override val currentMediaType: StateFlow<MediaType?> by lazy {
        viewModel.state.map { it.mediaType }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, viewModel.state.value.mediaType)
    }

    override val stereoMode: StateFlow<StereoMode> get() = viewModel.stereoMode
    override val detectedStereoMode: StateFlow<StereoMode> get() = viewModel.detectedStereoMode

    // S0763: gate the dialog's "3D" section on the cached 3D/VR master-toggle state.
    override val is3dVrEnabled: Boolean get() = cached3dVrEnabled

    override fun setStereoMode(mode: StereoMode) = viewModel.setStereoMode(mode)
    override fun rememberStereoModeForCurrentFile(mode: StereoMode) =
        viewModel.rememberStereoModeForCurrentFile(mode)

    override val videoPlayerHandle: VideoPlayerHandle get() = photoVideoHandle

    private val photoVideoHandle: VideoPlayerHandle by lazy {
        PhotoVideoStandaloneVideoHandle(
            viewManager = viewManager,
            trackSelectionManager = { trackSelectionManager },
            currentMediaType = { viewModel.state.value.mediaType },
        )
    }

    override val isAudioServiceActive: Boolean = false

    override fun showMessage(message: String) = viewModel.showMessage(message)

    override fun requestFinishAfterDelete() = finish()

    companion object {
        /** Names a one-shot action to run after the image is shown (set by the screenshot gesture dispatcher). */
        const val EXTRA_AUTO_ACTION = "auto_action"
        const val AUTO_ACTION_DRAW = "draw"
        const val AUTO_ACTION_SEND_TO = "send_to"
        const val AUTO_ACTION_CROP_AND_SHARE = "crop_and_share"

        /**
         * S0837: set by the screenshot OPEN_IN_DRAW gesture. When true, a default save inside the draw
         * editor overwrites the launched source URI in place instead of writing a new file; an explicit
         * "Save as.." still creates a new file. Never set for photo-edit or the manual draw menu.
         */
        const val EXTRA_DRAW_OVERWRITE_SOURCE = "draw_overwrite_source"
    }
}
