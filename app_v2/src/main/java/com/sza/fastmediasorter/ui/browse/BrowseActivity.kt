package com.sza.fastmediasorter.ui.browse

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import android.view.InputDevice
import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.resourceeditor.ResourceEditorActivity
import com.sza.fastmediasorter.ui.browse.managers.BrowseDialogHelper
import com.sza.fastmediasorter.ui.browse.managers.BrowseMediaStoreObserver
import com.sza.fastmediasorter.ui.browse.managers.BrowseRecyclerViewManager
import com.sza.fastmediasorter.ui.browse.managers.KeyboardNavigationManager
import com.sza.fastmediasorter.utils.UserActionLogger
import dagger.hilt.android.AndroidEntryPoint
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
@android.annotation.SuppressLint("SetTextI18n")
class BrowseActivity : BaseActivity<ActivityBrowseBinding>() {

    private val viewModel: BrowseViewModel by viewModels()
    private lateinit var mediaFileAdapter: MediaFileAdapter
    private lateinit var dialogHelper: BrowseDialogHelper
    private lateinit var mediaStoreObserver: BrowseMediaStoreObserver
    private lateinit var recyclerViewManager: BrowseRecyclerViewManager
    private lateinit var keyboardNavigationManager: KeyboardNavigationManager
    private lateinit var fileOperationsManager: com.sza.fastmediasorter.ui.browse.managers.BrowseFileOperationsManager
    private lateinit var smallControlsManager: com.sza.fastmediasorter.ui.browse.managers.BrowseSmallControlsManager
    private lateinit var cloudAuthManager: com.sza.fastmediasorter.ui.browse.managers.BrowseCloudAuthManager
    private lateinit var utilityManager: com.sza.fastmediasorter.ui.browse.managers.BrowseUtilityManager
    private lateinit var stateManager: com.sza.fastmediasorter.ui.browse.managers.BrowseStateManager
    private lateinit var sortMenuManager: com.sza.fastmediasorter.ui.browse.managers.BrowseSortMenuManager
    private lateinit var archiveDialogManager: com.sza.fastmediasorter.ui.browse.managers.BrowseArchiveDialogManager
    private lateinit var binaryFileHandler: com.sza.fastmediasorter.ui.browse.managers.BrowseBinaryFileHandler
    private lateinit var errorDisplayManager: com.sza.fastmediasorter.ui.browse.managers.BrowseErrorDisplayManager
    private lateinit var scrollButtonManager: com.sza.fastmediasorter.ui.browse.managers.BrowseScrollButtonManager
    private lateinit var eventHandler: com.sza.fastmediasorter.ui.browse.managers.BrowseEventHandler
    private lateinit var stateUiUpdater: com.sza.fastmediasorter.ui.browse.managers.BrowseStateUiUpdater
    private lateinit var buttonSetupHelper: com.sza.fastmediasorter.ui.browse.managers.BrowseButtonSetupHelper
    private lateinit var lifecycleHelper: com.sza.fastmediasorter.ui.browse.managers.BrowseLifecycleHelper
    private lateinit var passwordManager: ResourcePasswordManager
    
    @Inject
    lateinit var googleDriveClient: GoogleDriveRestClient
    
    @Inject
    lateinit var resourceOpsMenuManager: com.sza.fastmediasorter.ui.browse.managers.ResourceOpsMenuManager
    
    @Inject
    lateinit var dropboxClient: com.sza.fastmediasorter.data.cloud.DropboxClient

    @Inject
    lateinit var oneDriveClient: com.sza.fastmediasorter.data.cloud.OneDriveRestClient
    
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleGoogleSignInResult(result.data)
    }
    
    private val playerActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringArrayListExtra(PlayerActivity.EXTRA_MODIFIED_FILES)?.let { modifiedPaths ->
                // Remove deleted/moved files from adapter
                viewModel.removeFilesFromList(modifiedPaths)
            }
        }
    }

    private val editResourceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Timber.i("Resource updated, reloading files")
            viewModel.reloadFiles(clearList = true)
        }
    }
    
    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Timber.i("Permission granted by user")
            
            // For both Move and Delete operations:
            // - Delete: createDeleteRequest auto-deleted files after grant
            // - Move: files were already uploaded to server BEFORE permission dialog,
            //         createDeleteRequest auto-deleted source files after grant
            // In both cases, just update UI to reflect deletions.
            Timber.i("Delete permission granted, updating UI state")
            viewModel.onDeletePermissionGranted()
            
            // Clear any pending move state (no retry needed - files already uploaded and deleted)
            fileOperationsManager.clearPendingMoveOperation()
        } else {
            Timber.w("Permission denied by user")
            fileOperationsManager.clearPendingMoveOperation()
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    /** System folder tree picker for local storage (API 21+). */
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            Timber.i("folderPickerLauncher: selected uri=$uri")
            // Persist read+write permission for the selected tree so subsequent
            // access within the same process works without re-prompting.
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Timber.w(e, "takePersistableUriPermission failed (non-fatal)")
            }
            // Resolve the real filesystem path (needed by FileOperationUseCase)
            val path = com.sza.fastmediasorter.core.util.UriPathResolver.getPath(this, uri) ?: uri.path
            if (path != null) {
                // Validate writability — on restricted storage/SD cards the resolved
                // path may not be accessible via java.io.File even though SAF granted it.
                val destDir = java.io.File(path)
                if (!destDir.exists() || !destDir.canWrite()) {
                    Timber.w("folderPickerLauncher: path=$path is not writable (exists=${destDir.exists()}, canWrite=${destDir.canWrite()})")
                    Toast.makeText(this, getString(R.string.error_folder_not_writable), Toast.LENGTH_LONG).show()
                    pendingFolderPickerOp = null
                    return@registerForActivityResult
                }
                val op = pendingFolderPickerOp
                if (op != null) {
                    pendingFolderPickerOp = null
                    // Persist last selected local folder (store the content:// URI, not the filesystem path,
                    // so OpenDocumentTree can re-use it as initialUri on the next launch)
                    lifecycleScope.launch {
                        try {
                            val current = settingsRepository.getSettings().first()
                            settingsRepository.updateSettings(current.copy(lastSelectedLocalFolder = uri.toString()))
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to save last local folder")
                        }
                    }
                    fileOperationsManager.executeOperationToPath(
                        operationType = op.operationType,
                        sourceFiles = op.sourceFiles,
                        destinationPath = path,
                        sourceCredentialsId = op.sourceCredentialsId,
                        overwriteFiles = op.overwriteFiles
                    )
                    // Dispatch directory items via directory-aware handler
                    if (op.dirItems.isNotEmpty()) {
                        lifecycleScope.launch {
                            var dirSucceeded = 0
                            var dirFailed = 0
                            for (dir in op.dirItems) {
                                val dirResult = when (op.operationType) {
                                    com.sza.fastmediasorter.domain.model.FileOperationType.COPY ->
                                        unifiedFileOperationHandler.executeCopyDirectory(dir.path, path)
                                    com.sza.fastmediasorter.domain.model.FileOperationType.MOVE ->
                                        unifiedFileOperationHandler.executeMoveDirectory(dir.path, path)
                                    else -> Result.failure(IllegalArgumentException("Unsupported dir op: ${op.operationType}"))
                                }
                                dirResult
                                    .onSuccess { dirSucceeded++ }
                                    .onFailure { e ->
                                        Timber.e(e, "folderPickerLauncher: dir op failed for ${dir.path}")
                                        dirFailed++
                                    }
                            }
                            if (dirFailed > 0) {
                                Toast.makeText(
                                    this@BrowseActivity,
                                    getString(R.string.error_some_operations_failed, dirFailed, dirSucceeded + dirFailed),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            } else {
                Timber.w("folderPickerLauncher: could not resolve path from uri=$uri")
                Toast.makeText(this, getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
            }
        } else {
            Timber.d("folderPickerLauncher: user cancelled folder picker")
        }
    }
    
    // Flag to prevent duplicate file loading on first onResume after onCreate
    private var isFirstResume = true
    
    // Track last submitted sort mode to force adapter refresh when the user changes sorting
    private var lastSubmittedSortMode: SortMode? = null
    
    // Shared RecycledViewPool for optimizing ViewHolder reuse
    private val sharedViewPool = RecyclerView.RecycledViewPool().apply {
        // Set max recycled views for each view type
        // ViewType 0 = List item, ViewType 1 = Grid item
        setMaxRecycledViews(0, 30) // List view holders
        setMaxRecycledViews(1, 40) // Grid view holders (more needed for grid)
    }
    
    @Inject
    lateinit var fileOperationUseCase: FileOperationUseCase
    
    @Inject
    lateinit var getDestinationsUseCase: GetDestinationsUseCase
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var smbClient: SmbClient
    
    @Inject
    lateinit var sftpClient: SftpClient
    
    @Inject
    lateinit var ftpClient: FtpClient
    
    @Inject
    lateinit var credentialsRepository: com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository

    @Inject
    lateinit var unifiedFileOperationHandler: com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler

    @Inject
    lateinit var audioMetadataLoader: com.sza.fastmediasorter.core.util.AudioMetadataLoader

    private var showVideoThumbnails = true // Cached setting value
    private var showPdfThumbnails = false // Cached PDF thumbnail setting
    private lateinit var listSubmitManager: com.sza.fastmediasorter.ui.browse.managers.BrowseListSubmitManager

    /** Pending folder picker operation: stored while the system/network picker is open. */
    private data class PendingFolderPickerOp(
        val operationType: com.sza.fastmediasorter.domain.model.FileOperationType,
        val sourceFiles: List<java.io.File>,
        val sourceCredentialsId: String?,
        val overwriteFiles: Boolean,
        val resource: com.sza.fastmediasorter.domain.model.MediaResource,
        val dirItems: List<com.sza.fastmediasorter.domain.model.MediaFile> = emptyList()
    )
    private var pendingFolderPickerOp: PendingFolderPickerOp? = null

    override fun onDestroy() {
        Timber.d("BrowseActivity.onDestroy: isFinishing=$isFinishing, isChangingConfigurations=$isChangingConfigurations")
        // Log Glide cache statistics before destroying activity
        com.sza.fastmediasorter.utils.GlideCacheStats.logStats()
        stopMediaStoreObserver()
        // Clear scroll listeners BEFORE super.onDestroy() nulls _binding.
        // RecyclerView.onDetachedFromWindow() fires AFTER onDestroy via WindowManager.removeViewImmediate(),
        // triggering setScrollState(IDLE) → onScrollStateChanged → updateScrollButtonsVisibility → binding crash.
        binding.rvMediaFiles.clearOnScrollListeners()
        super.onDestroy()
        Timber.d("BrowseActivity.onDestroy: COMPLETE")
    }

    override fun getViewBinding(): ActivityBrowseBinding {
        return ActivityBrowseBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // Apply edge-to-edge insets: push top bar below status bar, bottom bar above nav bar
        com.sza.fastmediasorter.ui.browse.managers.BrowseEdgeToEdgeHelper.apply(binding)

        // Reset Glide cache stats for this browsing session
        com.sza.fastmediasorter.utils.GlideCacheStats.reset()

        passwordManager = ResourcePasswordManager(context = this, layoutInflater = layoutInflater)
        Timber.d("showVideoThumbnails initialized: $showVideoThumbnails")
        
        // Initialize managers
        dialogHelper = BrowseDialogHelper(this, object : BrowseDialogHelper.DialogCallbacks {
            override fun onFilterApplied(filter: FileFilter?) {
                viewModel.setFilter(filter)
                
                // Show toast only for user-defined filters (not resource type restrictions)
                val resource = viewModel.state.value.resource
                val isUserFilter = filter != null && !filter.isEmpty() && (
                    !filter.nameContains.isNullOrBlank() ||
                    filter.minDate != null ||
                    filter.maxDate != null ||
                    filter.minSizeMb != null ||
                    filter.maxSizeMb != null ||
                    (filter.mediaTypes != null && filter.mediaTypes != resource?.supportedMediaTypes)
                )
                
                if (isUserFilter) {
                    Toast.makeText(this@BrowseActivity, R.string.toast_filter_active, Toast.LENGTH_SHORT).show()
                }
            }
            override fun onSortModeSelected(sortMode: SortMode) {
                viewModel.setSortMode(sortMode)
            }
            override fun onRenameConfirmed(oldName: String, newName: String) {
                // Not used - handled by RenameDialog directly
            }
            override fun onRenameMultipleConfirmed(files: List<Pair<String, String>>) {
                // Not used - handled internally in showRenameMultipleDialog
            }
            override fun onDirectoryRenameConfirmed(oldPath: String, newName: String) {
                viewModel.renameDirectory(oldPath, newName)
            }
            override fun onCopyDestinationSelected(destinationPath: String) {
                // Not used - handled by CopyToDialog
            }
            override fun onMoveDestinationSelected(destinationPath: String) {
                // Not used - handled by MoveToDialog
            }
            override fun onDeleteConfirmed(fileCount: Int) {
                viewModel.deleteSelectedFiles()
            }
            override fun onCloudSignInRequested(provider: com.sza.fastmediasorter.data.cloud.CloudProvider) {
                when (provider) {
                    com.sza.fastmediasorter.data.cloud.CloudProvider.GOOGLE_DRIVE -> launchGoogleSignIn()
                    com.sza.fastmediasorter.data.cloud.CloudProvider.DROPBOX -> cloudAuthManager.launchDropboxSignIn()
                    com.sza.fastmediasorter.data.cloud.CloudProvider.ONEDRIVE -> cloudAuthManager.launchOneDriveSignIn()
                }
            }
            override fun saveUndoOperation(undoOp: UndoOperation) {
                viewModel.saveUndoOperation(undoOp)
            }
            override fun reloadFiles() {
                viewModel.reloadFiles()
            }
            override fun updateFile(oldPath: String, newFile: MediaFile) {
                viewModel.updateFile(oldPath, newFile)
            }
            override fun setIgnoringFileChanges(ignoring: Boolean) {
                viewModel.setIgnoringFileChanges(ignoring)
            }
            override fun createMediaFileFromFile(file: java.io.File): MediaFile {
                return viewModel.createMediaFileFromFile(file)
            }
            override fun getFileOperationUseCase(): com.sza.fastmediasorter.domain.usecase.FileOperationUseCase {
                return viewModel.fileOperationUseCase
            }
            override fun getResourceName(): String? {
                return viewModel.state.value.resource?.name
            }
            override fun getLifecycleOwner(): androidx.lifecycle.LifecycleOwner {
                return this@BrowseActivity
            }
        })
        
        mediaStoreObserver = BrowseMediaStoreObserver(this, object : BrowseMediaStoreObserver.MediaStoreCallbacks {
            override fun onMediaStoreChanged() {
                // Skip reload if ViewModel is currently ignoring file changes (e.g. during MediaStore sync)
                if (!viewModel.isIgnoringFileChanges()) {
                    viewModel.reloadFiles()
                } else {
                    Timber.d("MediaStore changed but ignoring (programmatic operation in progress)")
                }
            }
        })
        
        // Setup standard adapter (always used - no pagination) - MUST be initialized before recyclerViewManager
        mediaFileAdapter = MediaFileAdapter(
            onFileClick = { file ->
                UserActionLogger.logItemClick(file.name, context = "File click")
                viewModel.saveLastViewedFile(file.path)
                viewModel.openFile(file)
            },
            onFileLongClick = { file ->
                UserActionLogger.logItemLongClick(file.name, context = "Range selection")
                // According to specification: long press selects range
                viewModel.selectFileRange(file.path)
            },
            onSelectionChanged = { file, selected ->
                UserActionLogger.logSelection(file.name, selected, context = "Checkbox click")
                viewModel.selectFile(file.path)
                if (selected) viewModel.saveLastViewedFile(file.path)
            },
            onSelectionRangeRequested = { file ->
                UserActionLogger.logItemLongClick(file.name, context = "Checkbox long click - range")
                // Long click on checkbox: select range from last selected file
                viewModel.selectFileRange(file.path)
            },
            onPlayClick = { file ->
                UserActionLogger.logButtonClick("InlinePlay", "File: ${file.name}")
                viewModel.saveLastViewedFile(file.path)
                viewModel.inlinePlayToggle(file)
            },
            onFavoriteClick = { file ->
                UserActionLogger.logButtonClick("Favorite", "File: ${file.name}")
                viewModel.toggleFavorite(file)
            },
            onCopyClick = { file ->
                UserActionLogger.logButtonClick("Copy", "File: ${file.name}")
                viewModel.selectFile(file.path)
                showCopyDialog()
            },
            onMoveClick = { file ->
                UserActionLogger.logButtonClick("Move", "File: ${file.name}")
                viewModel.selectFile(file.path)
                showMoveDialog()
            },
            onRenameClick = { file ->
                UserActionLogger.logButtonClick("Rename", "File: ${file.name}")
                viewModel.selectFile(file.path)
                showRenameDialog()
            },
            onDeleteClick = { file ->
                UserActionLogger.logButtonClick("Delete", "File: ${file.name}")
                viewModel.selectFile(file.path)
                showDeleteConfirmation()
            },
            onFolderClick = { folder ->
                UserActionLogger.logItemClick(folder.name, context = "Folder click")
                viewModel.navigateToFolder(folder)
            },
            onBinaryFileClick = { file ->
                // Task 6: Show bottom sheet menu for binary files
                UserActionLogger.logItemClick(file.name, context = "Binary file click")
                showBinaryFileMenu(file)
            },
            getShowVideoThumbnails = { showVideoThumbnails },
            getShowPdfThumbnails = { showPdfThumbnails }
        )
        
        // Task 6: Initialize binary file thumbnail generator
        val binaryThumbnailGenerator = com.sza.fastmediasorter.util.BinaryFileThumbnailGenerator(this)
        mediaFileAdapter.setBinaryThumbnailGenerator(binaryThumbnailGenerator)
        mediaFileAdapter.setAudioMetadataLoader(audioMetadataLoader)
        
        recyclerViewManager = BrowseRecyclerViewManager(
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            resources = resources,
            callbacks = object : BrowseRecyclerViewManager.RecyclerViewCallbacks {
                override fun onDisplayModeChanged(displayMode: DisplayMode) {
                    stateUiUpdater.currentDisplayMode = displayMode
                }
                override fun updateToggleButtonIcon(iconResId: Int) {
                    binding.btnToggleView.setIconResource(iconResId)
                }
            }
        )
        
        // Task: Add scroll listener to optimize thumbnail loading during fast scroll
        binding.rvMediaFiles.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                Timber.d("BrowseActivity: onScrollStateChanged newState=$newState")
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        // Scroll ended - resume thumbnail loading for visible items
                        mediaFileAdapter.setScrolling(false)
                        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                        layoutManager?.let {
                            val firstVisible = it.findFirstVisibleItemPosition()
                            val lastVisible = it.findLastVisibleItemPosition()
                            if (firstVisible >= 0 && lastVisible >= 0) {
                                Timber.d("Scroll ended: loading visible thumbnails [$firstVisible..$lastVisible]")
                                mediaFileAdapter.loadVisibleThumbnails(firstVisible, lastVisible)
                                mediaFileAdapter.loadVisibleAudioMetadata(firstVisible, lastVisible)
                            }
                        }
                        // Refresh button visibility after scroll settles (positions are final here)
                        updateScrollButtonsVisibility(mediaFileAdapter.itemCount)
                    }
                    RecyclerView.SCROLL_STATE_DRAGGING,
                    RecyclerView.SCROLL_STATE_SETTLING -> {
                        // Scrolling started/settling - pause thumbnail loading
                        mediaFileAdapter.setScrolling(true)
                    }
                }
            }
            
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // This is called continuously during scroll - use it to detect scrollbar drag
                // Set scrolling flag immediately when scroll happens
                if (dy != 0 || dx != 0) {
                    mediaFileAdapter.setScrolling(true)
                }
                // Update position-based button visibility on every scroll event
                updateScrollButtonsVisibility(mediaFileAdapter.itemCount)
            }
        })
        
        keyboardNavigationManager = KeyboardNavigationManager(
            recyclerView = binding.rvMediaFiles,
            callbacks = object : KeyboardNavigationManager.KeyboardNavigationCallbacks {
                override fun getCurrentFocusPosition(): Int = this@BrowseActivity.getCurrentFocusPosition()
                override fun getMediaFilesCount(): Int = viewModel.state.value.mediaFiles.size
                override fun getSelectedFilesCount(): Int = viewModel.state.value.selectedFiles.size
                override fun toggleCurrentItemSelection(position: Int) = this@BrowseActivity.toggleCurrentItemSelection(position)
                override fun playCurrentOrSelected(position: Int) = this@BrowseActivity.playCurrentOrSelected(position)
                @Suppress("DEPRECATION")
                override fun onBackPressed() = this@BrowseActivity.onBackPressed()
                override fun showDeleteConfirmation() = this@BrowseActivity.showDeleteConfirmation()
                override fun showCopyDialog() = this@BrowseActivity.showCopyDialog()
                override fun showMoveDialog() = this@BrowseActivity.showMoveDialog()
                override fun performButtonClick(buttonId: Int) { /* Handled via direct button clicks */ }
                
                // Task 8: Additional keyboard shortcuts
                override fun selectAllFiles() {
                    Timber.d("Keyboard: Ctrl+A - Select All")
                    performSelectAllWithToast()
                }
                
                override fun showRenameDialog() {
                    val selectedFiles = viewModel.state.value.selectedFiles
                    if (selectedFiles.size == 1) {
                        val files = viewModel.state.value.mediaFiles.filter { it.path in selectedFiles }
                        if (files.isNotEmpty()) {
                            Timber.d("Keyboard: F2 - Rename: ${files.first().name}")
                            dialogHelper.showRenameDialog(files)
                        }
                    }
                }
                
                override fun refreshFiles() {
                    Timber.d("Keyboard: F5 - Refresh")
                    viewModel.reloadFiles()
                }
                
                override fun clearSelection() {
                    Timber.d("Keyboard: Escape - Clear selection")
                    viewModel.clearSelection()
                }
                
                override fun navigateUp() {
                    Timber.d("Keyboard: Backspace - Navigate up")
                    viewModel.navigateUp()
                }
            }
        )
        
        fileOperationsManager = com.sza.fastmediasorter.ui.browse.managers.BrowseFileOperationsManager(
            context = this,
            coroutineScope = lifecycleScope,
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            smbClient = smbClient,
            sftpClient = sftpClient,
            ftpClient = ftpClient,
            credentialsRepository = credentialsRepository,
            dirOperationHandler = unifiedFileOperationHandler,
            callbacks = object : com.sza.fastmediasorter.ui.browse.managers.BrowseFileOperationsManager.FileOperationCallbacks {
                override fun onOperationCompleted() {
                    viewModel.reloadFiles()
                }
                override fun saveUndoOperation(undoOp: UndoOperation) {
                    viewModel.saveUndoOperation(undoOp)
                }
                override fun clearSelection() {
                    viewModel.clearSelection()
                }
                override fun getCacheDir(): File? = cacheDir
                override fun getExternalCacheDir(): File? = externalCacheDir
                override fun onAuthRequest(provider: String) {
                    when (provider) {
                        "dropbox" -> cloudAuthManager.launchDropboxSignIn()
                        "google_drive" -> cloudAuthManager.launchGoogleSignIn()
                        "onedrive" -> cloudAuthManager.launchOneDriveSignIn()
                        // Add other providers as needed
                    }
                }
                override fun onPermissionRequired(pendingIntent: android.app.PendingIntent) {
                    try {
                        Timber.i("Launching permission request from FileOperationsManager callback")
                        val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent).build()
                        permissionRequestLauncher.launch(intentSenderRequest)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to launch permission request from callback")
                        Toast.makeText(this@BrowseActivity, "Failed to request permission", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onShowMessage(message: String) {
                    Toast.makeText(this@BrowseActivity, message, Toast.LENGTH_SHORT).show()
                }
                override fun onShowError(message: String, details: String?) {
                    val text = if (details != null) "$message\n$details" else message
                    Toast.makeText(this@BrowseActivity, text, Toast.LENGTH_LONG).show()
                }
                override fun onFolderPickerRequested(
                    operationType: com.sza.fastmediasorter.domain.model.FileOperationType,
                    sourceFiles: List<java.io.File>,
                    sourceCredentialsId: String?,
                    resourceType: com.sza.fastmediasorter.domain.model.ResourceType,
                    resource: com.sza.fastmediasorter.domain.model.MediaResource,
                    dirItems: List<com.sza.fastmediasorter.domain.model.MediaFile>
                ) {
                    // Store pending operation; fetch overwrite flag asynchronously
                    lifecycleScope.launch {
                        val settings = try {
                            settingsRepository.getSettings().first()
                        } catch (e: Exception) {
                            com.sza.fastmediasorter.domain.model.AppSettings()
                        }
                        val overwrite = if (operationType == com.sza.fastmediasorter.domain.model.FileOperationType.COPY)
                            settings.overwriteOnCopy else settings.overwriteOnMove
                        pendingFolderPickerOp = PendingFolderPickerOp(
                            operationType = operationType,
                            sourceFiles = sourceFiles,
                            sourceCredentialsId = sourceCredentialsId,
                            overwriteFiles = overwrite,
                            resource = resource,
                            dirItems = dirItems
                        )
                        when (resourceType) {
                            com.sza.fastmediasorter.domain.model.ResourceType.LOCAL -> {
                                val initialUri = settings.lastSelectedLocalFolder?.let {
                                    try { android.net.Uri.parse(it) } catch (e: Exception) { null }
                                }
                                try {
                                    folderPickerLauncher.launch(initialUri)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to launch folder picker")
                                    Toast.makeText(this@BrowseActivity, getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
                                    pendingFolderPickerOp = null
                                }
                            }
                            com.sza.fastmediasorter.domain.model.ResourceType.SMB,
                            com.sza.fastmediasorter.domain.model.ResourceType.SFTP,
                            com.sza.fastmediasorter.domain.model.ResourceType.FTP,
                            com.sza.fastmediasorter.domain.model.ResourceType.CLOUD -> {
                                // Network/Cloud: launch the local folder picker so the user can
                                // copy/move network files to local storage.
                                Toast.makeText(
                                    this@BrowseActivity,
                                    getString(R.string.select_folder_network_hint),
                                    Toast.LENGTH_SHORT
                                ).show()
                                val initialUri = settings.lastSelectedLocalFolder?.let {
                                    try { android.net.Uri.parse(it) } catch (e: Exception) { null }
                                }
                                try {
                                    folderPickerLauncher.launch(initialUri)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to launch folder picker for network resource")
                                    Toast.makeText(this@BrowseActivity, getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
                                    pendingFolderPickerOp = null
                                }
                            }
                        }
                    }
                }
            }
        )
        
        smallControlsManager = com.sza.fastmediasorter.ui.browse.managers.BrowseSmallControlsManager(binding)
        
        cloudAuthManager = com.sza.fastmediasorter.ui.browse.managers.BrowseCloudAuthManager(
            context = this,
            coroutineScope = lifecycleScope,
            googleDriveClient = googleDriveClient,
            dropboxClient = dropboxClient,
            oneDriveClient = oneDriveClient,
            googleSignInLauncher = googleSignInLauncher,
            callbacks = object : com.sza.fastmediasorter.ui.browse.managers.BrowseCloudAuthManager.CloudAuthCallbacks {
                override fun onAuthenticationSuccess() {
                    viewModel.reloadFiles()
                }
                override fun onAuthenticationFailure() {
                    // Error already shown in manager
                }
            }
        )
        
        utilityManager = com.sza.fastmediasorter.ui.browse.managers.BrowseUtilityManager(this)
        
        stateManager = com.sza.fastmediasorter.ui.browse.managers.BrowseStateManager(
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            callbacks = object : com.sza.fastmediasorter.ui.browse.managers.BrowseStateManager.StateCallbacks {
                override fun saveLastViewedFile(filePath: String) {
                    viewModel.saveLastViewedFile(filePath)
                }
                override fun saveScrollPosition(position: Int) {
                    viewModel.saveScrollPosition(position)
                }
            }
        )
        
        sortMenuManager = com.sza.fastmediasorter.ui.browse.managers.BrowseSortMenuManager(
            context = this,
            onSortModeSelected = { viewModel.setSortMode(it) },
            getCurrentSortMode = { viewModel.state.value.sortMode },
            getCurrentResource = { viewModel.state.value.resource }
        )

        archiveDialogManager = com.sza.fastmediasorter.ui.browse.managers.BrowseArchiveDialogManager(
            context = this,
            onArchiveRequested = { name, dir -> viewModel.archiveSelectedFiles(name, dir) },
            onCancelArchive = { viewModel.cancelArchive() },
            onExtractArchive = { viewModel.extractArchive(it) },
            onCancelExtraction = { viewModel.cancelExtraction() },
            onNavigateToFolder = { viewModel.navigateToFolder(it) }
        )

        binaryFileHandler = com.sza.fastmediasorter.ui.browse.managers.BrowseBinaryFileHandler(
            activity = this,
            onSelectFile = { viewModel.selectFile(it) },
            onPrepareExtraction = { viewModel.prepareExtraction(it) },
            onShowCopyDialog = { showCopyDialog() },
            onShowMoveDialog = { showMoveDialog() },
            onShowRenameDialog = { showRenameDialog() },
            onShowDeleteConfirmation = { showDeleteConfirmation() }
        )

        errorDisplayManager = com.sza.fastmediasorter.ui.browse.managers.BrowseErrorDisplayManager(
            activity = this,
            rootView = binding.root,
            anchorView = binding.layoutOperations,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            onShowCloudAuthDialog = { showCloudAuthenticationDialog(it) },
            onUndoRequested = { viewModel.undoLastOperation() },
            getCurrentCloudProvider = { viewModel.state.value.resource?.cloudProvider }
        )

        scrollButtonManager = com.sza.fastmediasorter.ui.browse.managers.BrowseScrollButtonManager(
            activity = this,
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            fabScrollToTop = binding.fabScrollToTop,
            fabScrollToBottom = binding.fabScrollToBottom,
            fabPageUp = binding.fabPageUp,
            fabPageDown = binding.fabPageDown
        )

        listSubmitManager = com.sza.fastmediasorter.ui.browse.managers.BrowseListSubmitManager(
            activity = this,
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            emptyStateView = binding.emptyStateView,
            scrollButtonManager = scrollButtonManager,
            isLoadingProvider = { viewModel.loading.value }
        )

        lifecycleHelper = com.sza.fastmediasorter.ui.browse.managers.BrowseLifecycleHelper(
            activity = this,
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            listSubmitManager = listSubmitManager
        )

        stateUiUpdater = com.sza.fastmediasorter.ui.browse.managers.BrowseStateUiUpdater(
            activity = this,
            binding = binding,
            adapter = mediaFileAdapter,
            viewModel = viewModel,
            passwordManager = passwordManager,
            smallControlsManager = smallControlsManager,
            onUpdateDisplayMode = { mode -> updateDisplayMode(mode) },
            onUpdateBreadcrumb = { state -> updateBreadcrumb(state) },
            onBuildResourceInfo = { state -> buildResourceInfo(state) },
            onLaunchEditResource = { id -> launchEditResource(id) },
            onUpdateToggleViewAvailability = { disable -> updateToggleViewAvailability(disable) }
        )

        eventHandler = com.sza.fastmediasorter.ui.browse.managers.BrowseEventHandler(
            activity = this,
            viewModel = viewModel,
            errorDisplayManager = errorDisplayManager,
            archiveDialogManager = archiveDialogManager,
            resourceOpsMenuManager = resourceOpsMenuManager,
            permissionRequestLauncher = permissionRequestLauncher,
            playerActivityLauncher = playerActivityLauncher,
            onShowCloudAuthDialog = { provider -> showCloudAuthenticationDialog(provider) },
            skipAvailabilityCheck = intent.getBooleanExtra(EXTRA_SKIP_AVAILABILITY_CHECK, false)
        )

        buttonSetupHelper = com.sza.fastmediasorter.ui.browse.managers.BrowseButtonSetupHelper(
            binding = binding,
            adapter = mediaFileAdapter,
            scrollButtonManager = scrollButtonManager
        )

        // Setup back press callback for subfolder navigation
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.d("BrowseActivity.handleOnBackPressed: canNavigateUp=${viewModel.canNavigateUp()}")
                if (viewModel.canNavigateUp() && viewModel.navigateUp()) {
                    // Navigated back to parent folder
                    Timber.d("Navigated back to parent folder")
                } else {
                    // No more parent folders, exit activity
                    Timber.d("BrowseActivity.handleOnBackPressed: Exiting activity — clearing resume state")
                    viewModel.clearResumeState()
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        
        binding.btnBack.setOnClickListener {
            UserActionLogger.logButtonClick("Back", "BrowseActivity")
            Timber.d("BrowseActivity.btnBack: clicked, canNavigateUp=${viewModel.canNavigateUp()}")
            // Try subfolder navigation first, then exit activity
            if (!viewModel.canNavigateUp() || !viewModel.navigateUp()) {
                Timber.d("BrowseActivity.btnBack: finishing activity — clearing resume state")
                viewModel.clearResumeState()
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }

        binding.rvMediaFiles.apply {
            // Always use standard adapter (pagination removed)
            adapter = mediaFileAdapter
            
            // Calculate optimal cache size based on screen size
            val displayMetrics = resources.displayMetrics
            val screenHeightDp = displayMetrics.heightPixels / displayMetrics.density
            // For list view: ~80dp per item, for grid: ~150dp per item
            // Cache 2 screens worth of items for smooth scrolling
            val optimalCacheSize = ((screenHeightDp / 80) * 2.0).toInt().coerceIn(20, 50)
            setItemViewCacheSize(optimalCacheSize)
            
            // Use shared RecycledViewPool for efficient ViewHolder reuse
            setRecycledViewPool(sharedViewPool)
            
            // Set fixed size for better performance (item size doesn't change)
            setHasFixedSize(true)
            
            // Enable prefetching for smooth scrolling, but limit to 1-2 rows ahead
            // Too aggressive prefetch causes network congestion with 8 parallel connections
            layoutManager?.isItemPrefetchEnabled = true
            (layoutManager as? LinearLayoutManager)?.initialPrefetchItemCount = 4 // ~1 row ahead
            (layoutManager as? GridLayoutManager)?.initialPrefetchItemCount = 6  // ~2 rows ahead (3 columns × 2 rows)
            
            // Add scroll listener for user action logging
            addOnScrollListener(UserActionLogger.createScrollListener("BrowseActivity"))
            
            Timber.d("RecyclerView optimizations: cacheSize=$optimalCacheSize, screenHeightDp=$screenHeightDp")
        }
        
        // Setup FastScroller for interactive scrollbar (can drag with finger/mouse)
        FastScrollerBuilder(binding.rvMediaFiles).useMd2Style().build()

        setupSortButton()

        buttonSetupHelper.setupAllButtons(object : com.sza.fastmediasorter.ui.browse.managers.BrowseButtonSetupHelper.ButtonCallbacks {
            override fun onFilterClicked() = showFilterDialog()
            override fun onRefreshClicked() = performRefresh()
            override fun onToggleViewClicked() = viewModel.toggleDisplayMode()
            override fun onSelectAllClicked() = performSelectAllWithToast()
            override fun onDeselectAllClicked() = viewModel.clearSelection()
            override fun onCopyClicked() = showCopyDialog()
            override fun onMoveClicked() = showMoveDialog()
            override fun onRenameClicked() = showRenameDialog()
            override fun onDeleteClicked() = showDeleteConfirmation()
            override fun onUndoClicked() = viewModel.undoLastOperation()
            override fun onShareClicked() = shareSelectedFiles()
            override fun onArchiveClicked() = showArchiveConfigurationDialog()
            override fun onPlayClicked() = startSlideshow()
            override fun onRetryClicked() {
                viewModel.clearError()
                viewModel.reloadFiles()
            }
            override fun onStopScanClicked() {
                Timber.d("Stop scan requested by user")
                viewModel.cancelScan(forceCancel = true)
                val fileCount = viewModel.state.value.mediaFiles.size
                Toast.makeText(this@BrowseActivity, getString(R.string.scan_stopped, fileCount), Toast.LENGTH_SHORT).show()
            }
            override fun isAudioOnlyResource() = viewModel.state.value.resource?.isAudioOnly() == true
            override fun onResourceOpsClicked(anchor: android.view.View) {
                val isScheduleEnabled = BuildConfig.ENABLE_SCHEDULED_OPERATIONS && viewModel.scheduledOperationsEnabled
                resourceOpsMenuManager.showMenu(
                    anchor = anchor,
                    viewModel = viewModel,
                    isScheduleEnabled = isScheduleEnabled,
                    onAutomateSource = if (isScheduleEnabled) {
                        {
                            val resourceId = viewModel.state.value.resource?.id ?: return@showMenu
                            startActivity(Intent(this@BrowseActivity, SettingsActivity::class.java).apply {
                                putExtra(SettingsActivity.EXTRA_SOURCE_RESOURCE_ID, resourceId)
                            })
                        }
                    } else null,
                    onAddToDestinations = { viewModel.addCurrentResourceAsDestination() }
                )
            }
        })

        buttonSetupHelper.updateToolbarButtonLabels(resources.configuration)
    }

    /**
     * Handle configuration changes (screen rotation).
     * Recalculates grid layout and updates toolbar button labels.
     */
    override fun onLayoutConfigurationChanged(newConfig: Configuration) {
        buttonSetupHelper.updateToolbarButtonLabels(newConfig)

        // Force display mode recalculation with new screen dimensions
        lifecycleScope.launch {
            stateUiUpdater.currentDisplayMode?.let { mode ->
                stateUiUpdater.currentDisplayMode = null
                updateDisplayMode(mode)
                Timber.d("onLayoutConfigurationChanged: Recalculated display mode for screenWidthDp=${newConfig.screenWidthDp}")
            }
        }
    }

    private fun performSelectAllWithToast() {
        val totalCount = viewModel.state.value.mediaFiles.size
        viewModel.selectAll()
        if (totalCount > 0) {
            val message = resources.getQuantityString(
                R.plurals.selected_n_files,
                totalCount,
                totalCount
            )
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun notifyItemRangeChangedSafely(start: Int, count: Int, payload: Any, source: String) =
        scrollButtonManager.notifyItemRangeChangedSafely(start, count, payload, source)

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Don't log every collect - only when actually submitting list (reduces log spam)
                    
                    // Always use standard mode (no pagination)
                    // Only submit list if content actually changed
                    // Compare with last emitted list from ViewModel (survives Activity recreation)
                    val previousMediaFiles = viewModel.lastEmittedMediaFiles
                    val previousSize = previousMediaFiles?.size ?: -1
                    
                    val sortChanged = state.sortMode != lastSubmittedSortMode
                    val shouldSubmit = if (previousMediaFiles == null) {
                        true
                    } else if (sortChanged) {
                        true
                    } else if (state.mediaFiles === previousMediaFiles) {
                        false
                    } else {
                        // Lists are different instances - let DiffUtil determine changes
                        // This handles size changes, content changes, and property changes (like isFavorite)
                        true
                    }
                    
                    // Update Sort button text if sortMode changed
                    if (sortChanged) {
                        updateSortButtonText()
                    }
                    
                    if (shouldSubmit) {
                        viewModel.markListAsSubmitted(state.mediaFiles)
                        lastSubmittedSortMode = state.sortMode
                        
                        // Standard mode - submit full list to MediaFileAdapter
                        val reason = when {
                            previousMediaFiles == null -> "First load"
                            state.mediaFiles.size != previousSize -> "Size changed ($previousSize → ${state.mediaFiles.size})"
                            else -> "Content changed"
                        }
                        Timber.d("Submitting list: $reason, resource=${state.resource?.name}")
                        
                        // Enable deferred thumbnail loading
                        mediaFileAdapter.setSkipInitialThumbnailLoad(true)
                        
                        mediaFileAdapter.submitList(state.mediaFiles) {
                            listSubmitManager.onListSubmitted(state)
                        }
                    }
                    // No log for skipped submitList - reduces log spam during large folder loading
                    
                    mediaFileAdapter.setSelectedPaths(state.selectedFiles)
                    state.resource?.let { resource ->
                        mediaFileAdapter.setAudioOnlyMode(resource.isAudioOnly())
                        mediaFileAdapter.setCredentialsId(resource.credentialsId)
                        mediaFileAdapter.setDisableThumbnails(resource.disableThumbnails)
                        
                        // Update item operation buttons visibility based on resource permissions
                        lifecycleScope.launch {
                            val hasDestinations = getDestinationsUseCase.getDestinationsExcluding(resource.id).isNotEmpty()
                            mediaFileAdapter.setResourcePermissions(
                                hasDestinations = hasDestinations,
                                isWritable = resource.isWritable && !resource.isReadOnly
                            )
                        }
                    }

                    stateUiUpdater.onStateChanged(state)
                }
            }
        }

        // Observe inline audio player state — update adapter + auto-scroll to playing track
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.inlinePlayerState.collect { state ->
                    mediaFileAdapter.updateInlinePlayerState(state)
                    state.playingPath?.let { path ->
                        val position = viewModel.state.value.mediaFiles.indexOfFirst { it.path == path }
                        if (position >= 0) {
                            val layoutManager = binding.rvMediaFiles.layoutManager as? LinearLayoutManager
                            layoutManager?.let { lm ->
                                val rvHeight = binding.rvMediaFiles.height
                                val itemHeight = lm.findViewByPosition(position)?.height ?: 80
                                val offset = ((rvHeight - itemHeight) / 2).coerceAtLeast(0)
                                lm.scrollToPositionWithOffset(position, offset)
                                Timber.d("InlinePlayer: auto-scrolled to position=$position offset=$offset")
                            }
                        }
                    }
                }
            }
        }

        // Observe settings for favorite button visibility
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    settingsRepository.getSettings(),
                    viewModel.state
                ) { settings, state ->
                    // Show favorite button if:
                    // 1. enableFavorites setting is on, OR
                    // 2. Currently viewing Favorites resource (id = -100)
                    settings.enableFavorites || state.resource?.id == -100L
                }.collect { shouldShow ->
                    mediaFileAdapter.setShowFavoriteButton(shouldShow)
                }
            }
        }

        // Observe hideGridActionButtons setting
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.getSettings().collect { settings ->
                    mediaFileAdapter.setHideGridActionButtons(settings.hideGridActionButtons)
                }
            }
        }

        // Observe loading state and STOP button visibility together
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.loading, viewModel.state) { isLoading, state ->
                    Pair(isLoading, state)
                }.collect { (isLoading, state) ->
                    binding.layoutProgress.isVisible = isLoading
                    binding.btnStopScan.isVisible = state.isScanCancellable && isLoading
                    
                    // Hide SwipeRefreshLayout indicator when loading completes
                    if (!isLoading) {
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                    
                    // Update progress message
                    if (state.loadingProgress > 0) {
                        binding.tvProgressMessage.text = getString(R.string.loading_with_progress, getString(R.string.loading), state.loadingProgress)
                    } else {
                        binding.tvProgressMessage.text = getString(R.string.loading)
                    }
                    
                    // Update Empty state visibility when loading completes
                    // CRITICAL: This ensures Empty state shows after scan finishes with 0 files
                    if (!isLoading) {
                        val hasError = viewModel.error.value != null
                        val actualItemCount = mediaFileAdapter.itemCount
                        val actualIsEmpty = actualItemCount == 0
                        val isFavoritesResource = state.resource?.id == -100L

                        // Guard: adapter may not have committed items yet while ViewModel already
                        // holds the loaded list — avoid flashing empty state in this race window.
                        val filesStillPending = actualIsEmpty && state.mediaFiles.isNotEmpty()

                        val shouldShowEmptyState = when {
                            filesStillPending -> false
                            isFavoritesResource -> actualIsEmpty && !hasError && state.mediaFiles.isEmpty()
                            else -> actualIsEmpty && !hasError
                        }

                        binding.emptyStateView.isVisible = shouldShowEmptyState
                        Timber.d("Progress observer: Empty state visibility updated after loading: $shouldShowEmptyState (itemCount=$actualItemCount, hasError=$hasError, filesStillPending=$filesStillPending)")
                    }
                }
            }
        }

        // Observe settings changes
        var lastIconSize = 96 // Track last known icon size
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.getSettings().collect { settings ->
                    Timber.d("PDF_THUMB_DEBUG: Settings loaded - showPdfThumbnails=${settings.showPdfThumbnails}, current=$showPdfThumbnails, itemCount=${mediaFileAdapter.itemCount}")
                    val pdfThumbnailsChanged = showPdfThumbnails != settings.showPdfThumbnails
                    showVideoThumbnails = settings.showVideoThumbnails
                    showPdfThumbnails = settings.showPdfThumbnails
                    Timber.d("PDF_THUMB_DEBUG: After update - showPdfThumbnails=$showPdfThumbnails, changed=$pdfThumbnailsChanged")
                    
                    // If PDF thumbnail setting changed, refresh visible items to load/hide thumbnails
                    if (pdfThumbnailsChanged && mediaFileAdapter.itemCount > 0) {
                        Timber.d("PDF_THUMB_DEBUG: Triggering notifyItemRangeChanged for ${mediaFileAdapter.itemCount} items")
                        notifyItemRangeChangedSafely(
                            start = 0,
                            count = mediaFileAdapter.itemCount,
                            payload = "LOAD_THUMBNAILS",
                            source = "settings pdf toggle"
                        )
                    }
                    
                    // Update grid cell size when thumbnail size changes in settings
                    val currentResource = viewModel.state.value.resource
                    if (currentResource != null && 
                        currentResource.displayMode == DisplayMode.GRID && 
                        !currentResource.isAudioOnly() &&
                        settings.defaultIconSize != lastIconSize) {
                        
                        lastIconSize = settings.defaultIconSize
                        Timber.d("Thumbnail size changed to ${settings.defaultIconSize}, updating grid layout")
                        updateDisplayMode(DisplayMode.GRID)
                    } else if (currentResource != null) {
                        lastIconSize = settings.defaultIconSize
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.error.collect { errorMessage ->
                    Timber.d("Error observer triggered: errorMessage=$errorMessage")
                    // Show error state if error occurred and no files loaded
                    val hasError = errorMessage != null
                    val isEmpty = mediaFileAdapter.itemCount == 0
                    val currentLoading = viewModel.loading.value
                    val currentState = viewModel.state.value
                    
                    Timber.d("Error observer: hasError=$hasError, isEmpty=$isEmpty, currentLoading=$currentLoading")
                    binding.errorStateView.isVisible = hasError && isEmpty
                    
                    // CRITICAL: Always check CURRENT adapter itemCount to avoid race conditions
                    // This observer can run before/after submitList callback
                    val actualItemCount = mediaFileAdapter.itemCount
                    val actualIsEmpty = actualItemCount == 0
                    
                    // For Favorites (resourceId -100), check both adapter and state to avoid race condition
                    val isFavoritesResource = currentState.resource?.id == -100L
                    val shouldShowEmptyState = if (isFavoritesResource) {
                        actualIsEmpty && !hasError && !currentLoading && currentState.mediaFiles.isEmpty()
                    } else {
                        actualIsEmpty && !hasError && !currentLoading
                    }
                    
                    binding.emptyStateView.isVisible = shouldShowEmptyState
                    Timber.d("Empty state visibility: $shouldShowEmptyState (actualItemCount=$actualItemCount, hasError=$hasError, loading=$currentLoading)")
                    if (binding.emptyStateView.isVisible) {
                        if (isFavoritesResource) {
                            // Show favorites-specific empty message
                            binding.tvEmptyStateMessage.text = getString(R.string.favorites_empty_title)
                            binding.tvEmptyStateHint.isVisible = true
                            binding.tvEmptyStateHint.text = getString(R.string.favorites_empty_hint)
                        } else {
                            // Show default empty message (existing logic)
                            binding.tvEmptyStateMessage.text = getString(R.string.no_files_found)
                            
                            // Check if scanSubdirectories is disabled - show hint
                            val resource = currentState.resource
                            if (resource != null && !resource.scanSubdirectories) {
                                binding.tvEmptyStateHint.isVisible = true
                                // Show different message based on whether we know subfolder count
                                if (resource.subfolderCount > 0) {
                                    // We know there are subfolders - show specific count
                                    binding.tvEmptyStateHint.text = getString(R.string.empty_folder_with_subfolders, resource.subfolderCount)
                                } else {
                                    // Generic message when subfolder count unknown
                                    binding.tvEmptyStateHint.text = getString(R.string.empty_folder_hint_subdirectories)
                                }
                            } else {
                                binding.tvEmptyStateHint.isVisible = false
                            }
                        }
                    }
                    
                    if (hasError && isEmpty) {
                        binding.tvErrorMessage.text = errorMessage
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    eventHandler.handleEvent(event)
                }
            }
        }
    }

    private fun showError(message: String, details: String?, exception: Throwable? = null) =
        errorDisplayManager.showError(message, details, exception)
    

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return keyboardNavigationManager.handleKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Forward mouse wheel scroll events to RecyclerView.
        // SwipeRefreshLayout does not propagate ACTION_SCROLL to children on all API levels.
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)
        ) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll != 0f) {
                val rv = binding.rvMediaFiles
                val scrollFactor = rv.context.resources.displayMetrics.density * 64f
                rv.scrollBy(0, (-vScroll * scrollFactor).toInt())
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
    
    private fun getCurrentFocusPosition(): Int {
        return stateManager.getCurrentFocusPosition()
    }
    
    private fun toggleCurrentItemSelection(position: Int) {
        if (position in 0 until viewModel.state.value.mediaFiles.size) {
            val file = viewModel.state.value.mediaFiles[position]
            viewModel.selectFile(file.path)
            Timber.d("Toggled selection for position $position: ${file.name}")
        }
    }
    
    private fun playCurrentOrSelected(position: Int) {
        val state = viewModel.state.value
        if (state.selectedFiles.isNotEmpty()) {
            // Play first selected file
            val firstSelected = state.mediaFiles.firstOrNull { it.path in state.selectedFiles }
            if (firstSelected != null) {
                viewModel.openFile(firstSelected)
            }
        } else if (position in 0 until state.mediaFiles.size) {
            // Play file at current position
            val file = state.mediaFiles[position]
            viewModel.openFile(file)
        }
    }

    private fun buildResourceInfo(state: BrowseState): String {
        return utilityManager.buildResourceInfo(state)
    }

    private fun launchEditResource(resourceId: Long) {
        val intent = ResourceEditorActivity.createEditIntent(this, resourceId)
        editResourceLauncher.launch(intent)
    }

    /**
     * Updates breadcrumb visibility and text based on current subfolder navigation state.
     * Shows interactive "Resource > Folder1 > Folder2" path when navigating subfolders.
     * Breadcrumb segments are clickable for quick navigation.
     */
    private fun updateBreadcrumb(state: BrowseState) {
        if (state.isSubfolderMode && state.currentPath != null) {
            // Show interactive breadcrumb in subfolder mode
            binding.breadcrumbView.isVisible = true
            binding.spaceAfterBack?.isVisible = false
            
            // Get breadcrumb parts and set in view
            val (resourceName, folders) = viewModel.getBreadcrumbParts()
            binding.breadcrumbView.setPath(resourceName, folders)
            
            // Set click listener for breadcrumb navigation
            binding.breadcrumbView.setOnSegmentClickListener { depth ->
                Timber.d("Breadcrumb clicked: depth=$depth")
                viewModel.navigateToDepth(depth)
            }
        } else {
            // Hide breadcrumb at root level
            binding.breadcrumbView.isVisible = false
            binding.breadcrumbView.clear()
            binding.spaceAfterBack?.isVisible = true
        }
    }

    private fun buildFilterDescription(filter: FileFilter): String {
        return utilityManager.buildFilterDescription(filter)
    }

    private suspend fun updateDisplayMode(mode: DisplayMode) {
        val settings = settingsRepository.getSettings().first()
        val currentResource = viewModel.state.value.resource
        val shouldForceList = currentResource?.isAudioOnly() == true
        val effectiveMode = if (shouldForceList) DisplayMode.LIST else mode
        val iconSize = if (shouldForceList) {
            48
        } else if (currentResource?.disableThumbnails == true) {
            32
        } else {
            settings.defaultIconSize
        }
        
        recyclerViewManager.updateDisplayMode(effectiveMode, iconSize, showVideoThumbnails)
        stateUiUpdater.currentDisplayMode = effectiveMode
        updateToggleViewAvailability(shouldForceList)
        // After layout manager / span count changes, visible item range may differ — re-check
        binding.rvMediaFiles.post { updateScrollButtonsVisibility(mediaFileAdapter.itemCount) }
    }

    private fun updateToggleViewAvailability(shouldDisable: Boolean) {
        binding.btnToggleView.apply {
            isEnabled = !shouldDisable
            alpha = if (shouldDisable) 0.4f else 1.0f
            // Hide the button completely for single-type resources
            visibility = if (shouldDisable) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    /**
     * Performs a full refresh of the file list.
     * Called from both refresh button and pull-to-refresh.
     */
    private fun performRefresh() {
        Timber.d("Manual refresh requested")
        // Clear failed video thumbnail cache to retry previously failed videos
        NetworkFileDataFetcher.clearFailedVideoCache()
        mediaFileAdapter.incrementRefreshVersion() // Force thumbnail reload
        viewModel.reloadFiles()
    }

    private fun showFilterDialog() {
        val allowedTypes = viewModel.state.value.resource?.supportedMediaTypes
        dialogHelper.showFilterDialog(viewModel.state.value.filter, allowedTypes)
    }

    private fun setupSortButton() {
        updateSortButtonText()
        binding.btnSort.setOnClickListener {
            UserActionLogger.logButtonClick("SortButton", "BrowseActivity")
            sortMenuManager.showSortPopupMenu(binding.btnSort)
        }
    }

    private fun updateSortButtonText() {
        binding.btnSort.text = sortMenuManager.getSortModeShortName(viewModel.state.value.sortMode)
    }

    private fun showArchiveConfigurationDialog() {
        val state = viewModel.state.value
        archiveDialogManager.showArchiveConfigurationDialog(
            currentDir = state.currentPath ?: state.resource?.path ?: "",
            selectedFiles = state.selectedFiles,
            mediaFiles = state.mediaFiles
        )
    }

    private fun showDeleteConfirmation() {
        val state = viewModel.state.value
        val resource = state.resource

        if (resource?.isReadOnly == true) {
            Toast.makeText(this, R.string.error_read_only, Toast.LENGTH_SHORT).show()
            return
        }

        val count = state.selectedFiles.size
        // We need to fetch the actual MediaFile objects
        val selectedFiles = state.mediaFiles.filter { it.path in state.selectedFiles }

        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            dialogHelper.showDeleteConfirmation(selectedFiles, resource, settings)
        }
    }
    
    private fun showBinaryFileMenu(mediaFile: com.sza.fastmediasorter.domain.model.MediaFile) =
        binaryFileHandler.showBinaryFileMenu(mediaFile)

    private fun showUnarchiveConfirmDialog(mediaFile: MediaFile, targetDirName: String) =
        archiveDialogManager.showUnarchiveConfirmDialog(mediaFile, targetDirName)
    
    
    private fun showRenameDialog() {
        val state = viewModel.state.value
        if (state.resource?.isReadOnly == true) {
            Toast.makeText(this, R.string.error_read_only, Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedFiles = state.mediaFiles.filter { 
            it.path in viewModel.state.value.selectedFiles 
        }
        dialogHelper.showRenameDialog(selectedFiles)
    }
    
    private fun startSlideshow() {
        val state = viewModel.state.value
        val resource = state.resource
        
        // Try to find lastViewedFile or selected file
        val startIndex = if (resource?.lastViewedFile != null) {
            state.mediaFiles.indexOfFirst { it.path == resource.lastViewedFile }
        } else if (state.selectedFiles.isNotEmpty()) {
            state.mediaFiles.indexOfFirst { it.path == state.selectedFiles.first() }
        } else {
            -1
        }
        
        // If file not found or no starting point specified, use first file (index 0)
        val actualIndex = if (startIndex >= 0) startIndex else 0
        
        if (state.mediaFiles.isNotEmpty()) {
            val file = state.mediaFiles[actualIndex]
            val isDocument = file.type == MediaType.TEXT || 
                            file.type == MediaType.PDF || 
                            file.type == MediaType.EPUB
            
            val resourceId = resource?.id ?: 0L
            val skipCheck = intent.getBooleanExtra(EXTRA_SKIP_AVAILABILITY_CHECK, false)
            val intent = PlayerActivity.createIntent(this, resourceId, actualIndex, skipCheck).apply {
                // Start slideshow only for media files (not documents)
                if (!isDocument) {
                    putExtra("slideshow_mode", true)
                }
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.toast_no_files_to_play, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showCopyDialog() {
        val state = viewModel.state.value
        val resource = state.resource ?: run {
            Toast.makeText(this, R.string.toast_resource_not_loaded, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            fileOperationsManager.showCopyDialog(
                state.selectedFiles.toList(),
                state.mediaFiles,
                resource,
                settings
            )
        }
    }
    
    private fun showMoveDialog() {
        val state = viewModel.state.value
        val resource = state.resource ?: run {
            Toast.makeText(this, R.string.toast_resource_not_loaded, Toast.LENGTH_SHORT).show()
            return
        }
        
        if (resource.isReadOnly) {
            Toast.makeText(this, R.string.error_read_only, Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            fileOperationsManager.showMoveDialog(
                state.selectedFiles.toList(),
                state.mediaFiles,
                resource,
                settings
            )
        }
    }
    
    override fun onResume() {
        super.onResume()
        Timber.d("BrowseActivity.onResume: isFirstResume=$isFirstResume, listSubmitManager.shouldScrollToLastViewed=$listSubmitManager.shouldScrollToLastViewed, resourceId=${viewModel.state.value.resource?.id}")
        
        // Handle any pending cloud authentication results
        if (::cloudAuthManager.isInitialized) {
            cloudAuthManager.onResume()
        }
        
        // Check storage permission for local resources
        lifecycleHelper.checkAndRequestStoragePermission(
            resource = viewModel.state.value.resource,
            onReloadFiles = { viewModel.reloadFiles(clearList = true) }
        )

        // Adapter is no longer cleared in onPause - no need to restore
        // Memory cache (1GB) persists across PlayerActivity navigation

        // Skip reload on first onResume - files already loaded in ViewModel.init{}
        if (isFirstResume) {
            isFirstResume = false
            Timber.d("BrowseActivity.onResume: First resume, skipping reload (already loaded in init)")
        } else {
            Timber.d("BrowseActivity.onResume: Returned to BrowseActivity, checking for changes")
            // Check if resource settings changed (supportedMediaTypes, scanSubfolders)
            viewModel.checkAndReloadIfResourceChanged()

            // Secondary scroll restore path: fires when list unchanged (submitList callback won't fire)
            lifecycleHelper.restoreScrollOnResume(viewModel.state.value)
        }
        
        // Clear expired undo operations (older than 5 minutes)
        viewModel.clearExpiredUndoOperation()
        
        // Scroll restoration: primary path is submitList callback (handles first load + list changes).
        // Secondary path is the onResume block above (handles return from player with unchanged list).
        Timber.d("BrowseActivity.onResume: listSubmitManager.shouldScrollToLastViewed=$listSubmitManager.shouldScrollToLastViewed (will restore in submitList callback if list changed)")
        
        // Start MediaStore observer for local resources
        startMediaStoreObserver()
    }
    
    override fun onPause() {
        Timber.d("BrowseActivity.onPause: isFinishing=$isFinishing, itemCount=${binding.rvMediaFiles.adapter?.itemCount}")
        super.onPause()
        // Stop MediaStore observer to avoid unnecessary updates
        stopMediaStoreObserver()
        
        // Save scroll position (index) when leaving Browse
        stateManager.saveScrollPosition()
        // Save last viewed file (path) when leaving Browse
        stateManager.saveLastViewedFile()
        
        // Set flag to restore scroll position on next resume (return from PlayerActivity)
        listSubmitManager.shouldScrollToLastViewed = true
        
        // Cancel background thumbnail loading to free bandwidth for PlayerActivity
        Timber.d("BrowseActivity.onPause: Cancelling background thumbnail operations")
        viewModel.cancelBackgroundThumbnailLoading()
        
        // Note: Adapter is NO LONGER cleared in onPause to preserve memory cache
        // Thumbnails persist across PlayerActivity navigation for instant reloading
        // Memory cache (up to 1GB) survives until BrowseActivity exits (onDestroy)
    }

    
    override fun onStop() {
        Timber.d("BrowseActivity.onStop: isFinishing=$isFinishing, isChangingConfigurations=$isChangingConfigurations")
        super.onStop()

        if (!isChangingConfigurations) {
            Timber.d("BrowseActivity.onStop: stopping inline audio playback")
            viewModel.inlineStop()
        }
        
        // Cancel scan and all loading tasks when Activity goes into background.
        // For network resources the job is killed immediately (no point keeping
        // SMB/FTP connections alive in background). For local resources the scan
        // is stopped gracefully via shouldStop flag.
        //
        // Scenarios covered:
        // 1. User opens another BrowseActivity (different resource)
        // 2. User minimizes app (Home button)
        // 3. PlayerActivity opens (scan resumes on return if needed)
        // 4. User exits app (onDestroy/onCleared will also fire)
        if (!isFinishing) {
            Timber.d("BrowseActivity.onStop: Activity going to background, cancelling scan")
            viewModel.cancelScan(forceCancel = true)
            viewModel.cancelBackgroundThumbnailLoading()
        } else {
            Timber.d("BrowseActivity.onStop: Activity finishing, scan will be cancelled in onDestroy")
        }
    }
    
    private fun startMediaStoreObserver() {
        if (!::mediaStoreObserver.isInitialized) {
            Timber.d("MediaStoreObserver not initialized, skipping start")
            return
        }
        val resource = viewModel.state.value.resource
        mediaStoreObserver.start(resource?.type)
    }
    
    private fun stopMediaStoreObserver() {
        if (!::mediaStoreObserver.isInitialized) {
            return
        }
        mediaStoreObserver.stop()
    }
    
    private fun shareSelectedFiles() {
        val state = viewModel.state.value
        val resource = state.resource ?: return
        val selectedFiles = state.mediaFiles.filter { it.path in state.selectedFiles }
        
        fileOperationsManager.shareSelectedFiles(selectedFiles, resource)
    }
    
    private fun showCloudAuthenticationDialog(provider: com.sza.fastmediasorter.data.cloud.CloudProvider) {
        val resourceName = viewModel.state.value.resource?.name ?: "Cloud resource"
        dialogHelper.showCloudAuthenticationDialog(provider, resourceName)
    }
    
    private fun launchGoogleSignIn() {
        cloudAuthManager.launchGoogleSignIn()
    }
    
    private fun handleGoogleSignInResult(data: Intent?) {
        cloudAuthManager.handleGoogleSignInResult(data)
    }
    
    private fun updateScrollButtonsVisibility(fileCount: Int) =
        scrollButtonManager.updateScrollButtonsVisibility(fileCount)
    
    private fun getThemeColor(attrId: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }

    companion object {
        const val EXTRA_RESOURCE_ID = "resourceId"
        const val EXTRA_SKIP_AVAILABILITY_CHECK = "skipAvailabilityCheck"
        const val EXTRA_INITIAL_FOLDER_PATH = "initialFolderPath"
        const val EXTRA_INITIAL_FILE_PATH = "initialFilePath"
        const val EXTRA_RESUME_IS_PLAYING = "resumeIsPlaying"

        fun createIntent(
            context: Context,
            resourceId: Long,
            skipAvailabilityCheck: Boolean = false,
            initialFolderPath: String? = null,
            initialFilePath: String? = null,
            isPlaying: Boolean? = null
        ): Intent {
            return Intent(context, BrowseActivity::class.java).apply {
                putExtra(EXTRA_RESOURCE_ID, resourceId)
                putExtra(EXTRA_SKIP_AVAILABILITY_CHECK, skipAvailabilityCheck)
                initialFolderPath?.let { putExtra(EXTRA_INITIAL_FOLDER_PATH, it) }
                initialFilePath?.let { putExtra(EXTRA_INITIAL_FILE_PATH, it) }
                isPlaying?.let { putExtra(EXTRA_RESUME_IS_PLAYING, it) }
            }
        }
    }
}
