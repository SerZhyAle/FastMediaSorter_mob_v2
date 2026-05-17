package com.sza.fastmediasorter.ui.player

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.AdjustImageUseCase
import com.sza.fastmediasorter.domain.usecase.ApplyImageFilterUseCase
import com.sza.fastmediasorter.domain.usecase.ChangeGifSpeedUseCase
import com.sza.fastmediasorter.domain.usecase.ExtractGifFramesUseCase
import com.sza.fastmediasorter.domain.usecase.FlipImageUseCase
import com.sza.fastmediasorter.domain.usecase.NetworkImageEditUseCase
import com.sza.fastmediasorter.domain.usecase.RotateImageUseCase
import com.sza.fastmediasorter.domain.usecase.SaveGifFirstFrameUseCase
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.ui.dialog.FileOperationDestinationDialog
import com.sza.fastmediasorter.ui.dialog.RenameDialog
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Helper class for managing dialog displays in PlayerActivity.
 * Handles copy/move/rename dialogs, file info, image editing, and settings.
 * 
 * Responsibilities:
 * - Copy/Move/Rename dialogs with destination selection
 * - File info display dialog
 * - Image editing dialog
 * - Player settings dialog
 * - Error dialogs (cloud auth, network issues)
 */
class PlayerDialogHelper(
    private val activity: AppCompatActivity,
    private val viewModel: PlayerViewModel,
    private val settingsRepository: SettingsRepository,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val unifiedCache: UnifiedFileCache,
    private val rotateImageUseCase: RotateImageUseCase,
    private val flipImageUseCase: FlipImageUseCase,
    private val networkImageEditUseCase: NetworkImageEditUseCase,
    private val applyImageFilterUseCase: ApplyImageFilterUseCase,
    private val adjustImageUseCase: AdjustImageUseCase,
    private val extractGifFramesUseCase: ExtractGifFramesUseCase,
    private val saveGifFirstFrameUseCase: SaveGifFirstFrameUseCase,
    private val changeGifSpeedUseCase: ChangeGifSpeedUseCase,
    private val downloadNetworkFileUseCase: com.sza.fastmediasorter.domain.usecase.DownloadNetworkFileUseCase,
    private val dialogCallback: DialogCallback,
    private val videoPlayerManagerProvider: (() -> VideoPlayerManager)? = null,
    private val textViewerManagerProvider: (() -> com.sza.fastmediasorter.ui.player.helpers.TextViewerManager)? = null,
    private val sleepTimerManagerProvider: (() -> com.sza.fastmediasorter.ui.player.helpers.SleepTimerManager?)? = null
) {
    
    private var onAuthRequestCallback: ((String) -> Unit)? = null
    private val activeDialogs = mutableListOf<Dialog>()

    fun setAuthCallback(callback: (String) -> Unit) {
        onAuthRequestCallback = callback
    }

    /**
     * Safely show a dialog with activity lifecycle check.
     * Tracks the dialog for cleanup in [dismissAll].
     */
    private fun safeShow(dialog: Dialog) {
        if (activity.isFinishing || activity.isDestroyed) {
            Timber.w("PlayerDialogHelper: cannot show dialog — activity is finishing/destroyed")
            return
        }
        try {
            dialog.setOnDismissListener { activeDialogs.remove(dialog) }
            activeDialogs.add(dialog)
            dialog.show()
            com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper.applyInitialFocus(dialog)
        } catch (e: WindowManager.BadTokenException) {
            Timber.e(e, "PlayerDialogHelper: dialog show failed — bad window token")
            activeDialogs.remove(dialog)
        }
    }

    /**
     * Dismiss all tracked dialogs. Call from Activity.onDestroy().
     */
    fun dismissAll() {
        activeDialogs.toList().forEach { dialog ->
            try {
                if (dialog.isShowing) dialog.dismiss()
            } catch (e: Exception) {
                Timber.w(e, "PlayerDialogHelper: error dismissing dialog")
            }
        }
        activeDialogs.clear()
    }

    fun showPlaybackControlDialog() {
        if (activity.isFinishing || activity.isDestroyed) {
            Timber.w("PlayerDialogHelper: cannot show PlaybackControlDialogFragment — activity is finishing/destroyed")
            return
        }

        val currentType = viewModel.state.value.currentFile?.type
        if (currentType != MediaType.VIDEO && currentType != MediaType.AUDIO) {
            Timber.d("PlayerDialogHelper: skip PlaybackControlDialogFragment for non-playback media")
            return
        }

        val fragmentManager = activity.supportFragmentManager
        if (fragmentManager.isStateSaved) {
            Timber.w("PlayerDialogHelper: cannot show PlaybackControlDialogFragment — fragment state already saved")
            return
        }

        if (fragmentManager.findFragmentByTag(PlaybackControlDialogFragment.TAG) != null) {
            return
        }

        activity.lifecycleScope.launch {
            if (activity.isFinishing || activity.isDestroyed || fragmentManager.isStateSaved) return@launch
            if (fragmentManager.findFragmentByTag(PlaybackControlDialogFragment.TAG) != null) return@launch
            PlaybackControlDialogFragment.newInstance()
                .show(fragmentManager, PlaybackControlDialogFragment.TAG)
        }
    }
    
    /**
     * Callback interface for dialog actions
     */
    interface DialogCallback {
        fun onImageEditComplete()
        fun onGifEditComplete()
        fun onBeforeRenameDialog(oldPath: String)
        fun onRenameRequested(oldPath: String, newName: String)
        fun onRenameComplete(oldPath: String, newPath: String)
    }
    
    /**
     * Show copy dialog with destination selection
     */
    fun showCopyDialog(currentFile: MediaFile, resourceId: Long) {
        // For network paths (SMB/S/FTP), create File with URI-compatible scheme
        val sourceFile = if (currentFile.path.startsWith("smb://") || 
                             currentFile.path.startsWith("sftp://") || 
                             currentFile.path.startsWith("ftp://") ||
                             currentFile.path.startsWith("cloud://")) {
            // Use custom File with network path that preserves the scheme
            object : File(currentFile.path) {
                override fun getAbsolutePath(): String = currentFile.path
                override fun getPath(): String = currentFile.path
                override fun getName(): String = currentFile.name
                override fun length(): Long = currentFile.size
            }
        } else {
            File(currentFile.path)
        }
        
        activity.lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            val resource = viewModel.state.value.resource
            
            // Extract current browse path from file (parent directory)
            val currentBrowsePath = currentFile.path.let { path ->
                val lastSlashIndex = path.lastIndexOf('/')
                if (lastSlashIndex > 0) {
                    path.substring(0, lastSlashIndex + 1)
                } else {
                    null
                }
            }
            
            FileOperationDestinationDialog(
                context = activity,
                operationType = FileOperationType.COPY,
                sourceFiles = listOf(sourceFile),
                sourceFolderName = resource?.name ?: "Current folder",
                currentResourceId = resourceId,
                currentBrowsePath = currentBrowsePath,
                sourceCredentialsId = resource?.credentialsId,
                fileOperationUseCase = viewModel.fileOperationUseCase,
                getDestinationsUseCase = viewModel.getDestinationsUseCase,
                overwriteFiles = settings.overwriteOnCopy,
                showDetailedErrors = settings.showDetailedErrors,
                onComplete = { undoOperation ->
                    // Save undo operation if enabled
                    if (settings.enableUndo && undoOperation != null) {
                        viewModel.saveUndoOperation(undoOperation)
                    }
                    // Go to next file if setting enabled
                    if (settings.goToNextAfterCopy) {
                        viewModel.nextFile()
                    }
                },
                onAuthRequest = { provider ->
                    // Delegate to activity via helper method or callback
                    // Since we don't have direct access to activity methods, we can cast or use a callback
                    // But wait, showCloudAuthError is in this class.
                    // We can call showCloudAuthError(provider) but that just shows the dialog.
                    // We need to trigger the actual auth.
                    // I should add onAuthRequest to PlayerDialogHelper constructor/setter.
                    // I already added it to showCloudAuthError, but not to the class itself.
                    
                    // I'll add a property to PlayerDialogHelper to hold the auth callback.
                    onAuthRequestCallback?.invoke(provider)
                }
            ).also { safeShow(it) }
        }
    }
    
    /**
     * Show move dialog with destination selection
     */
    fun showMoveDialog(currentFile: MediaFile, resourceId: Long) {
        activity.lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            
            // Check Safe Mode for move confirmation
            val shouldConfirmMove = settings.enableSafeMode && settings.confirmMove
            
            if (shouldConfirmMove) {
                // Show confirmation dialog first
                val resource = viewModel.state.value.resource
                safeShow(AlertDialog.Builder(activity)
                    .setTitle(R.string.confirm_move_title)
                    .setMessage(activity.getString(R.string.confirm_move_message, 1, resource?.name ?: "destination"))
                    .setPositiveButton(R.string.move) { _, _ ->
                        // Proceed with move dialog
                        showMoveDialogInternal(currentFile, resourceId, settings)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .create())
            } else {
                // Skip confirmation - show move dialog directly
                showMoveDialogInternal(currentFile, resourceId, settings)
            }
        }
    }
    
    private fun showMoveDialogInternal(currentFile: MediaFile, resourceId: Long, settings: AppSettings) {
        // For network paths (SMB/S/FTP), create File with URI-compatible scheme
        val sourceFile = if (currentFile.path.startsWith("smb://") || 
                             currentFile.path.startsWith("sftp://") || 
                             currentFile.path.startsWith("ftp://") ||
                             currentFile.path.startsWith("cloud://")) {
            object : File(currentFile.path) {
                override fun getAbsolutePath(): String = currentFile.path
                override fun getPath(): String = currentFile.path
                override fun getName(): String = currentFile.name
                override fun length(): Long = currentFile.size
            }
        } else {
            File(currentFile.path)
        }
        
        val resource = viewModel.state.value.resource
        
        // Extract current browse path from file (parent directory)
        val currentBrowsePath = currentFile.path.let { path ->
            val lastSlashIndex = path.lastIndexOf('/')
            if (lastSlashIndex > 0) {
                path.substring(0, lastSlashIndex + 1)
            } else {
                null
            }
        }
        
        FileOperationDestinationDialog(
            context = activity,
            operationType = FileOperationType.MOVE,
            sourceFiles = listOf(sourceFile),
            sourceFolderName = resource?.name ?: "Current folder",
            currentResourceId = resourceId,
            currentBrowsePath = currentBrowsePath,
            sourceCredentialsId = resource?.credentialsId,
            fileOperationUseCase = viewModel.fileOperationUseCase,
            getDestinationsUseCase = viewModel.getDestinationsUseCase,
            overwriteFiles = settings.overwriteOnMove,
            showDetailedErrors = settings.showDetailedErrors,
            onComplete = { undoOperation ->
                // Save undo operation if enabled
                if (settings.enableUndo && undoOperation != null) {
                    viewModel.saveUndoOperation(undoOperation)
                }
                // Remove moved file from list and go to next
                viewModel.onFileMoved(currentFile.path)
            },
            onAuthRequest = { provider ->
                onAuthRequestCallback?.invoke(provider)
            }
        ).also { safeShow(it) }
    }
    
    /**
     * Show rename dialog
     */
    fun showRenameDialog(currentFile: MediaFile) {
        val resource = viewModel.state.value.resource
        
        // Create File object - for network/cloud paths, preserve the scheme
        val file = if (currentFile.path.startsWith("smb://") || 
                       currentFile.path.startsWith("sftp://") || 
                       currentFile.path.startsWith("ftp://") ||
                       currentFile.path.startsWith("cloud://")) {
            object : File(currentFile.path) {
                override fun getAbsolutePath(): String = currentFile.path
                override fun getPath(): String = currentFile.path
                override fun getName(): String = currentFile.name
                override fun length(): Long = currentFile.size
            }
        } else {
            File(currentFile.path)
        }
        
        RenameDialog(
            context = activity,
            lifecycleOwner = activity,
            files = listOf(file),
            sourceFolderName = resource?.name ?: "Current folder",
            fileOperationUseCase = viewModel.fileOperationUseCase,
            onNameChosen = { oldPath, newName -> dialogCallback.onRenameRequested(oldPath, newName) },
            onComplete = { oldPath, newFile -> dialogCallback.onRenameComplete(oldPath, newFile.path) },
            onBeforeRename = { oldPath -> dialogCallback.onBeforeRenameDialog(oldPath) },
        ).also { safeShow(it) }
    }
    
    /**
     * Show file information dialog
     */
    fun showFileInfo(file: MediaFile) {
        val dialog = com.sza.fastmediasorter.ui.dialog.FileInfoDialog(
            activity,
            file,
            smbClient,
            sftpClient,
            ftpClient,
            credentialsRepository,
            unifiedCache,
            downloadNetworkFileUseCase,
            audioMetadataLoader = null,
            audioMetadataCacheRepository = null
        )
        safeShow(dialog)
    }
    
    /**
     * Show image editing dialog (rotate, flip, filters)
     */
    fun showImageEditDialog(currentFile: MediaFile) {
        if (currentFile.type != MediaType.IMAGE) {
            Toast.makeText(activity, R.string.toast_edit_images_only, Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialog = com.sza.fastmediasorter.ui.dialog.ImageEditDialog(
            context = activity,
            imagePath = currentFile.path,
            rotateImageUseCase = rotateImageUseCase,
            flipImageUseCase = flipImageUseCase,
            networkImageEditUseCase = networkImageEditUseCase,
            applyImageFilterUseCase = applyImageFilterUseCase,
            adjustImageUseCase = adjustImageUseCase,
            onEditComplete = {
                dialogCallback.onImageEditComplete()
            }
        )
        safeShow(dialog)
    }
    
    /**
     * Show GIF editing dialog (extract frames, change speed, save first frame)
     */
    fun showGifEditDialog(currentFile: MediaFile) {
        if (!isAnimatedImagePath(currentFile.path)) {
            Toast.makeText(activity, R.string.gif_editing_only_for_gif_files, Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialog = com.sza.fastmediasorter.ui.dialog.GifEditorDialog(
            context = activity,
            gifPath = currentFile.path,
            extractFramesUseCase = extractGifFramesUseCase,
            saveFirstFrameUseCase = saveGifFirstFrameUseCase,
            changeSpeedUseCase = changeGifSpeedUseCase,
            downloadNetworkFileUseCase = downloadNetworkFileUseCase,
            onEditComplete = {
                dialogCallback.onGifEditComplete()
            }
        )
        safeShow(dialog)
    }

    private fun isAnimatedImagePath(path: String): Boolean {
        val lowerPath = path.lowercase()
        return lowerPath.endsWith(".gif") || lowerPath.endsWith(".webp") || lowerPath.endsWith(".apng")
    }
    
    /**
     * Show player settings dialog for video/audio files
     */
    fun showPlayerSettingsDialog(
        currentSettings: com.sza.fastmediasorter.ui.dialog.PlayerSettingsDialog.PlayerSettings,
        onSettingsApplied: (com.sza.fastmediasorter.ui.dialog.PlayerSettingsDialog.PlayerSettings) -> Unit
    ) {
        val dialog = com.sza.fastmediasorter.ui.dialog.PlayerSettingsDialog(
            context = activity,
            currentSettings = currentSettings,
            onSettingsApplied = onSettingsApplied
        )
        safeShow(dialog)
    }
    
    /**
     * Show cloud authentication error dialog
     * @param providerName Optional provider name (e.g., "Dropbox", "Google Drive")
     * @param onAuthRequest Optional callback to trigger authentication
     */
    fun showCloudAuthError(providerName: String? = null, onAuthRequest: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) {
            Timber.w("showCloudAuthenticationError: Activity is finishing/destroyed, skipping dialog")
            return
        }
        
        val message = if (providerName != null) {
            activity.getString(R.string.cloud_auth_required, providerName)
        } else {
            activity.getString(R.string.cloud_auth_copy_error)
        }
        
        val builder = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.authentication_required))
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)

        if (onAuthRequest != null) {
             builder.setPositiveButton(activity.getString(R.string.sign_in)) { _, _ ->
                 onAuthRequest.invoke()
             }
             builder.setNeutralButton(activity.getString(R.string.go_to_resources)) { _, _ ->
                 activity.finish()
             }
        } else {
             builder.setPositiveButton(activity.getString(R.string.go_to_resources)) { _, _ ->
                 activity.finish()
             }
        }
        safeShow(builder.create())
    }
    /**
     * Show PDF editing dialog with available export actions.
     */
    fun showPdfEditDialog(currentFile: MediaFile) {
        if (currentFile.type != MediaType.PDF) {
            Toast.makeText(activity, activity.getString(R.string.msg_no_file_to_edit), Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf(activity.getString(R.string.pdf_export_to_jpg))

        safeShow(AlertDialog.Builder(activity)
            .setTitle(R.string.pdf_edit_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportPdfToJpg(currentFile)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create())
    }

    fun showEncodingDialog() {
        val manager = textViewerManagerProvider?.invoke() ?: return
        val charsets = manager.getSupportedCharsets()
        val currentCharset = manager.getCurrentCharsetName()
        val labels = charsets.map { (name, charset) ->
            if (charset.name() == currentCharset) "✓ $name" else name
        }.toTypedArray()
        safeShow(com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.select_encoding)
            .setItems(labels) { _, which ->
                manager.reopenWithEncoding(charsets[which].second)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create())
    }

    fun showReaderSettingsDialog() {
        val manager = textViewerManagerProvider?.invoke() ?: return
        val themes = com.sza.fastmediasorter.ui.player.helpers.TextReaderTheme.entries
        val themeLabels = arrayOf(
            activity.getString(R.string.reader_theme_light),
            activity.getString(R.string.reader_theme_dark),
            activity.getString(R.string.reader_theme_sepia)
        )
        val currentIndex = themes.indexOf(manager.getCurrentTheme()).coerceAtLeast(0)
        safeShow(com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.reader_settings)
            .setSingleChoiceItems(themeLabels, currentIndex) { dialog, which ->
                manager.applyReaderTheme(themes[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create())
    }

    fun showSleepTimerDialog() {
        val manager = sleepTimerManagerProvider?.invoke() ?: return
        val options = com.sza.fastmediasorter.ui.player.helpers.SleepTimerManager.SLEEP_TIMER_OPTIONS
        val labels = options.map { minutes ->
            if (minutes >= 60) {
                activity.getString(R.string.sleep_timer_hours, minutes / 60, minutes % 60)
            } else {
                activity.getString(R.string.sleep_timer_minutes, minutes)
            }
        }.toTypedArray()
        val items = if (manager.isSleepTimerActive) {
            arrayOf(activity.getString(R.string.sleep_timer_off)) + labels
        } else {
            labels
        }
        val indexOffset = if (manager.isSleepTimerActive) 1 else 0
        safeShow(com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.sleep_timer_title)
            .setItems(items) { _, which ->
                if (manager.isSleepTimerActive && which == 0) {
                    manager.cancelSleepTimer()
                    Toast.makeText(activity, R.string.sleep_timer_cancelled, Toast.LENGTH_SHORT).show()
                    Timber.d("PlayerDialogHelper: sleep timer cancelled by user")
                } else {
                    val selectedMinutes = options[which - indexOffset]
                    manager.startSleepTimer(selectedMinutes)
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.sleep_timer_set, items[which]),
                        Toast.LENGTH_SHORT
                    ).show()
                    Timber.d("PlayerDialogHelper: sleep timer set for $selectedMinutes min")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create())
    }

    fun showAudioTrackDialog() {
        val videoManager = videoPlayerManagerProvider?.invoke() ?: return
        val tracks = videoManager.getAvailableAudioTracks()
        if (tracks.isEmpty()) return
        val labels = tracks.map { it.label }.toTypedArray()
        val selectedIndex = tracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        safeShow(com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.select_audio_track)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val track = tracks[which]
                videoManager.selectAudioTrack(track.groupIndex, track.trackIndex)
                Timber.d("PlayerDialogHelper: selected audio track: ${track.label}")
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create())
    }

    fun showSubtitleTrackDialog() {
        val videoManager = videoPlayerManagerProvider?.invoke() ?: return
        val tracks = videoManager.getAvailableSubtitleTracks()
        val labels = mutableListOf(activity.getString(R.string.subtitle_off))
        labels.addAll(tracks.map { it.label })
        val selectedIndex = if (tracks.any { it.isSelected }) {
            tracks.indexOfFirst { it.isSelected } + 1
        } else {
            0
        }
        safeShow(com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.select_subtitle_track)
            .setSingleChoiceItems(labels.toTypedArray(), selectedIndex) { dialog, which ->
                if (which == 0) {
                    val groupIndex = tracks.firstOrNull()?.groupIndex ?: 0
                    videoManager.selectSubtitleTrack(groupIndex, -1)
                    Timber.d("PlayerDialogHelper: subtitles turned off")
                } else {
                    val track = tracks[which - 1]
                    videoManager.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                    Timber.d("PlayerDialogHelper: selected subtitle track: ${track.label}")
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create())
    }

    private fun exportPdfToJpg(currentFile: MediaFile) {
        activity.lifecycleScope.launch {
            Toast.makeText(activity, R.string.pdf_exporting_started, Toast.LENGTH_SHORT).show()

            try {
                if (currentFile.path.contains("://")) {
                    Toast.makeText(activity, R.string.unsupported_format_network_hint, Toast.LENGTH_LONG).show()
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    com.sza.fastmediasorter.utils.PdfExportHelper.exportPdfPagesToJpg(
                        activity,
                        File(currentFile.path)
                    )
                }

                result.onSuccess { count ->
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.pdf_export_success, count),
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { e ->
                    Timber.e(e, "PDF export failed")
                    Toast.makeText(
                        activity,
                        activity.getString(
                            R.string.pdf_export_failed,
                            activity.getString(R.string.friendly_copy_error_generic)
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "PDF export failed")
                Toast.makeText(
                    activity,
                    activity.getString(
                        R.string.pdf_export_failed,
                        activity.getString(R.string.friendly_copy_error_generic)
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
