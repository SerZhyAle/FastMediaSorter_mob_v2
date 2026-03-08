package com.sza.fastmediasorter.ui.browse

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.PopupMenu
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
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
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
import com.sza.fastmediasorter.utils.setBadgeText
import com.sza.fastmediasorter.utils.clearBadge
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
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
    private lateinit var passwordManager: ResourcePasswordManager
    
    @Inject
    lateinit var googleDriveClient: GoogleDriveRestClient
    
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
    
    // Flag to prevent duplicate file loading on first onResume after onCreate
    private var isFirstResume = true
    
    // Cache current display mode to avoid redundant updateDisplayMode() calls
    private var currentDisplayMode: DisplayMode? = null
    // Cache current audio-only mode to force layout refresh when resource media types change
    private var currentAudioOnlyMode: Boolean? = null
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

    private var showVideoThumbnails = true // Cached setting value
    private var showPdfThumbnails = false // Cached PDF thumbnail setting
    private var shouldScrollToLastViewed = false // Flag for scroll restoration after PlayerActivity return

    override fun onDestroy() {
        Timber.d("BrowseActivity.onDestroy: isFinishing=$isFinishing, isChangingConfigurations=$isChangingConfigurations")
        // Log Glide cache statistics before destroying activity
        com.sza.fastmediasorter.utils.GlideCacheStats.logStats()
        stopMediaStoreObserver()
        super.onDestroy()
        Timber.d("BrowseActivity.onDestroy: COMPLETE")
    }

    override fun getViewBinding(): ActivityBrowseBinding {
        return ActivityBrowseBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
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
            },
            onSelectionRangeRequested = { file ->
                UserActionLogger.logItemLongClick(file.name, context = "Checkbox long click - range")
                // Long click on checkbox: select range from last selected file
                viewModel.selectFileRange(file.path)
            },
            onPlayClick = { file ->
                UserActionLogger.logButtonClick("InlinePlay", "File: ${file.name}")
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
        
        recyclerViewManager = BrowseRecyclerViewManager(
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            resources = resources,
            callbacks = object : BrowseRecyclerViewManager.RecyclerViewCallbacks {
                override fun onDisplayModeChanged(displayMode: DisplayMode) {
                    currentDisplayMode = displayMode
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
                            }
                        }
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
        
        // Setup back press callback for subfolder navigation
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.d("BrowseActivity.handleOnBackPressed: canNavigateUp=${viewModel.canNavigateUp()}")
                if (viewModel.canNavigateUp() && viewModel.navigateUp()) {
                    // Navigated back to parent folder
                    Timber.d("Navigated back to parent folder")
                } else {
                    // No more parent folders, exit activity
                    Timber.d("BrowseActivity.handleOnBackPressed: Exiting activity")
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
                Timber.d("BrowseActivity.btnBack: finishing activity")
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

        binding.btnFilter.setOnClickListener {
            UserActionLogger.logButtonClick("Filter", "BrowseActivity")
            showFilterDialog()
        }

        binding.btnRefresh.setOnClickListener {
            UserActionLogger.logButtonClick("Refresh", "BrowseActivity")
            performRefresh()
        }
        
        // Pull-to-refresh handler
        binding.swipeRefreshLayout.setOnRefreshListener {
            UserActionLogger.logButtonClick("PullToRefresh", "BrowseActivity")
            performRefresh()
        }
        
        // Set SwipeRefreshLayout colors using theme colors
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.blue_500,
            R.color.teal_700
        )
        
        binding.btnStopScan.setOnClickListener {
            UserActionLogger.logButtonClick("StopScan", "BrowseActivity")
            Timber.d("Stop scan requested by user")
            viewModel.cancelScan(forceCancel = true)
            val fileCount = viewModel.state.value.mediaFiles.size
            Toast.makeText(this, getString(R.string.scan_stopped, fileCount), Toast.LENGTH_SHORT).show()
        }

        binding.btnToggleView.setOnClickListener {
            val resource = viewModel.state.value.resource
            if (resource?.isAudioOnly() == true) {
                return@setOnClickListener
            }
            UserActionLogger.logButtonClick("ToggleView", "BrowseActivity")
            viewModel.toggleDisplayMode()
        }
        
        binding.btnSelectAll.setOnClickListener {
            UserActionLogger.logButtonClick("SelectAll", "BrowseActivity")
            performSelectAllWithToast()
        }
        
        binding.btnDeselectAll.setOnClickListener {
            UserActionLogger.logButtonClick("DeselectAll", "BrowseActivity")
            viewModel.clearSelection()
        }

        binding.btnCopy.setOnClickListener {
            UserActionLogger.logButtonClick("Copy", "BrowseActivity - Toolbar")
            showCopyDialog()
        }

        binding.btnMove.setOnClickListener {
            UserActionLogger.logButtonClick("Move", "BrowseActivity - Toolbar")
            showMoveDialog()
        }

        binding.btnRename.setOnClickListener {
            UserActionLogger.logButtonClick("Rename", "BrowseActivity - Toolbar")
            showRenameDialog()
        }

        binding.btnDelete.setOnClickListener {
            UserActionLogger.logButtonClick("Delete", "BrowseActivity - Toolbar")
            showDeleteConfirmation()
        }
        
        binding.btnUndo.setOnClickListener {
            UserActionLogger.logButtonClick("Undo", "BrowseActivity")
            viewModel.undoLastOperation()
        }
        
        binding.btnShare.setOnClickListener {
            UserActionLogger.logButtonClick("Share", "BrowseActivity")
            shareSelectedFiles()
        }

        binding.btnPlay.setOnClickListener {
            UserActionLogger.logButtonClick("Play", "BrowseActivity - Toolbar")
            startSlideshow()
        }
        
        binding.btnRetry.setOnClickListener {
            UserActionLogger.logButtonClick("Retry", "BrowseActivity")
            viewModel.clearError()
            viewModel.reloadFiles()
        }
        
        // Scroll to top button
        binding.fabScrollToTop.setOnClickListener {
            UserActionLogger.logButtonClick("ScrollToTop", "BrowseActivity")
            val layoutManager = binding.rvMediaFiles.layoutManager
            when (layoutManager) {
                is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(0, 0)
                is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(0, 0)
                else -> binding.rvMediaFiles.scrollToPosition(0)
            }
            Timber.d("Scrolled to top (position 0)")
        }
        
        // Scroll to bottom button
        binding.fabScrollToBottom.setOnClickListener {
            UserActionLogger.logButtonClick("ScrollToBottom", "BrowseActivity")
            val itemCount = mediaFileAdapter.itemCount
            if (itemCount > 0) {
                val layoutManager = binding.rvMediaFiles.layoutManager
                when (layoutManager) {
                    is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(itemCount - 1, 0)
                    is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(itemCount - 1, 0)
                    else -> binding.rvMediaFiles.scrollToPosition(itemCount - 1)
                }
                Timber.d("Scrolled to bottom (position ${itemCount - 1})")
            }
        }
        
        // Set initial button labels based on current orientation
        updateToolbarButtonLabels(resources.configuration)
    }

    /**
     * Handle configuration changes (screen rotation).
     * Recalculates grid layout and updates toolbar button labels.
     */
    override fun onLayoutConfigurationChanged(newConfig: Configuration) {
        // Update toolbar button labels based on orientation
        updateToolbarButtonLabels(newConfig)
        
        // Force display mode recalculation with new screen dimensions
        lifecycleScope.launch {
            currentDisplayMode?.let { mode ->
                // Reset cached mode to force recalculation
                currentDisplayMode = null
                updateDisplayMode(mode)
                Timber.d("onLayoutConfigurationChanged: Recalculated display mode for screenWidthDp=${newConfig.screenWidthDp}")
            }
        }
    }
    
    /**
     * Show or hide text labels on toolbar buttons depending on orientation.
     * In landscape: show icon + text (TextButton style).
     * In portrait: show icon only (IconButton style).
     */
    private fun updateToolbarButtonLabels(config: Configuration) {
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        Timber.d("updateToolbarButtonLabels: isLandscape=$isLandscape")
        
        if (isLandscape) {
            binding.btnBack.text = getString(R.string.back)
            binding.btnFilter.text = getString(R.string.search)
            binding.btnRefresh.text = getString(R.string.refresh)
            binding.btnToggleView.text = getString(R.string.toggle_view_short)
            binding.btnSelectAll.text = getString(R.string.select_all_short)
            binding.btnPlay.text = getString(R.string.slideshow)
        } else {
            binding.btnBack.text = null
            binding.btnFilter.text = null
            binding.btnRefresh.text = null
            binding.btnToggleView.text = null
            binding.btnSelectAll.text = null
            binding.btnPlay.text = null
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
                            // CRITICAL: Check if Activity is still alive before accessing binding
                            // submitList callback can be called AFTER onDestroy (race condition)
                            if (isDestroyed || isFinishing) {
                                Timber.w("submitList callback: Activity destroyed/finishing, skipping")
                                return@submitList
                            }
                            
                            Timber.d("=== submitList CALLBACK START ===")
                            Timber.d("Adapter list submitted successfully, current itemCount=${mediaFileAdapter.itemCount}")
                            Timber.d("skipInitialThumbnailLoad flag BEFORE check: ${mediaFileAdapter.getSkipInitialThumbnailLoad()}")
                            
                            // Trigger thumbnail loading after layout is complete (ONLY if we have items)
                            if (mediaFileAdapter.itemCount > 0) {
                                // Check if layout is already complete
                                Timber.d("RecyclerView.isLaidOut = ${binding.rvMediaFiles.isLaidOut}")
                                if (binding.rvMediaFiles.isLaidOut && binding.rvMediaFiles.childCount > 0) {
                                    // Layout already done AND children are bound - trigger immediately
                                    Timber.d("=== RecyclerView ALREADY LAID OUT WITH CHILDREN - triggering thumbnails immediately ===")
                                    val layoutManager = binding.rvMediaFiles.layoutManager
                                    Timber.d("LayoutManager type: ${layoutManager?.javaClass?.simpleName}")
                                    
                                    val firstVisible = when (layoutManager) {
                                        is LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                                        is GridLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                                        else -> 0
                                    }
                                    val lastVisible = when (layoutManager) {
                                        is LinearLayoutManager -> layoutManager.findLastVisibleItemPosition()
                                        is GridLayoutManager -> layoutManager.findLastVisibleItemPosition()
                                        else -> minOf(20, mediaFileAdapter.itemCount - 1)
                                    }
                                    
                                    Timber.d("Visible range: first=$firstVisible, last=$lastVisible, itemCount=${mediaFileAdapter.itemCount}")
                                    
                                    if (firstVisible >= 0 && lastVisible >= firstVisible) {
                                        val visibleCount = lastVisible - firstVisible + 1
                                        Timber.d(">>> Calling notifyItemRangeChanged($firstVisible, $visibleCount, LOAD_THUMBNAILS)")
                                        mediaFileAdapter.notifyItemRangeChanged(firstVisible, visibleCount, "LOAD_THUMBNAILS")
                                        Timber.d("<<< notifyItemRangeChanged completed")
                                    } else {
                                        Timber.w("NOT calling notifyItemRangeChanged - invalid range: $firstVisible to $lastVisible")
                                        Timber.w("RV isAttachedToWindow=${binding.rvMediaFiles.isAttachedToWindow}, childCount=${binding.rvMediaFiles.childCount}")
                                    }
                                    
                                    // Reset flag
                                    Timber.d("Resetting skipInitialThumbnailLoad flag to false")
                                    mediaFileAdapter.setSkipInitialThumbnailLoad(false)
                                    Timber.d("skipInitialThumbnailLoad flag AFTER reset: ${mediaFileAdapter.getSkipInitialThumbnailLoad()}")
                                } else {
                                    // Layout ready but no children yet OR not laid out - use post {} to trigger after children are bound
                                    Timber.d("=== RecyclerView laid out but childCount=${binding.rvMediaFiles.childCount} - using post {} ===")
                                    binding.rvMediaFiles.post {
                                        // Check if Activity still alive in post {} callback
                                        if (isDestroyed || isFinishing) {
                                            Timber.w("post callback: Activity destroyed/finishing, skipping")
                                            return@post
                                        }
                                        
                                        Timber.d("=== POST EXECUTED - checking for visible items ===")
                                        val layoutManager = binding.rvMediaFiles.layoutManager
                                        Timber.d("LayoutManager type: ${layoutManager?.javaClass?.simpleName}, childCount=${binding.rvMediaFiles.childCount}")
                                        
                                        val firstVisible = when (layoutManager) {
                                            is LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                                            is GridLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                                            else -> 0
                                        }
                                        val lastVisible = when (layoutManager) {
                                            is LinearLayoutManager -> layoutManager.findLastVisibleItemPosition()
                                            is GridLayoutManager -> layoutManager.findLastVisibleItemPosition()
                                            else -> minOf(20, mediaFileAdapter.itemCount - 1)
                                        }
                                        
                                        Timber.d("Visible range: first=$firstVisible, last=$lastVisible, itemCount=${mediaFileAdapter.itemCount}")
                                        
                                        if (firstVisible >= 0 && lastVisible >= firstVisible) {
                                            val visibleCount = lastVisible - firstVisible + 1
                                            Timber.d(">>> Calling notifyItemRangeChanged($firstVisible, $visibleCount, LOAD_THUMBNAILS)")
                                            mediaFileAdapter.notifyItemRangeChanged(firstVisible, visibleCount, "LOAD_THUMBNAILS")
                                            Timber.d("<<< notifyItemRangeChanged completed")
                                        } else {
                                            Timber.w("NOT calling notifyItemRangeChanged in post - invalid range: $firstVisible to $lastVisible")
                                            Timber.w("RV isAttachedToWindow=${binding.rvMediaFiles.isAttachedToWindow}, childCount=${binding.rvMediaFiles.childCount}")
                                        }
                                        
                                        // Reset flag after post execution
                                        Timber.d("Resetting skipInitialThumbnailLoad flag to false (in post)")
                                        mediaFileAdapter.setSkipInitialThumbnailLoad(false)
                                        Timber.d("skipInitialThumbnailLoad flag AFTER reset: ${mediaFileAdapter.getSkipInitialThumbnailLoad()}")
                                    }
                                }
                            } else {
                                // Empty list - just reset flag
                                Timber.d("Empty list (itemCount=0), resetting skipInitialThumbnailLoad flag")
                                mediaFileAdapter.setSkipInitialThumbnailLoad(false)
                            }
                            
                            // Update empty state AFTER adapter updates itemCount
                            val itemCount = mediaFileAdapter.itemCount
                            
                            if (itemCount > 0) {
                                // Files loaded - hide empty state
                                binding.emptyStateView.isVisible = false
                                Timber.d("Empty state: hidden (itemCount=$itemCount)")
                            } else {
                                // No items yet - check loading state
                                val isLoading = viewModel.loading.value
                                
                                if (isLoading) {
                                    // Loading in progress - hide empty state
                                    binding.emptyStateView.isVisible = false
                                    Timber.d("Empty state: hidden during loading (layoutProgress visible)")
                                } else {
                                    // Loading complete, no files - show empty state
                                    binding.emptyStateView.isVisible = true
                                    Timber.d("Empty state: shown (isLoading=false, itemCount=0)")
                                }
                            }
                            Timber.d("UI visibility: rvMediaFiles.isVisible=${binding.rvMediaFiles.isVisible}")
                            
                            // Restore scroll position after adapter updates
                            if (itemCount > 0) {
                                // Priority 1: Restore to lastViewedFile (return from PlayerActivity)
                                if (shouldScrollToLastViewed) {
                                    state.resource?.lastViewedFile?.let { lastViewedPath ->
                                        Timber.d("submitList callback: Restoring scroll to lastViewedFile: $lastViewedPath")
                                        
                                        val position = state.mediaFiles.indexOfFirst { it.path == lastViewedPath }
                                        Timber.d("submitList callback: Found position=$position for file: ${lastViewedPath.substringAfterLast('/')}")
                                        
                                        if (position >= 0) {
                                            binding.rvMediaFiles.post {
                                                // Check if Activity still alive in post {} callback
                                                if (isDestroyed || isFinishing) {
                                                    Timber.w("post callback (scroll restore): Activity destroyed/finishing, skipping")
                                                    return@post
                                                }
                                                
                                                val layoutManager = binding.rvMediaFiles.layoutManager
                                                when (layoutManager) {
                                                    is LinearLayoutManager -> {
                                                        layoutManager.scrollToPositionWithOffset(position, 0)
                                                        Timber.i("submitList callback: ✓ Scrolled to '${state.mediaFiles[position].name}' at position $position (LinearLayoutManager)")
                                                    }
                                                    is GridLayoutManager -> {
                                                        layoutManager.scrollToPositionWithOffset(position, 0)
                                                        Timber.i("submitList callback: ✓ Scrolled to '${state.mediaFiles[position].name}' at position $position (GridLayoutManager)")
                                                    }
                                                    else -> {
                                                        binding.rvMediaFiles.scrollToPosition(position)
                                                        Timber.w("submitList callback: Scrolled to position $position (fallback)")
                                                    }
                                                }
                                            }
                                        } else {
                                            Timber.w("submitList callback: File not found in list: $lastViewedPath")
                                        }
                                    } ?: Timber.w("submitList callback: lastViewedFile is null")
                                    shouldScrollToLastViewed = false
                                } 
                                // Priority 2: Restore to lastScrollPosition (first open or reopen after back button)
                                else if (isFirstResume && state.resource?.lastScrollPosition != null && state.resource.lastScrollPosition > 0) {
                                    val position = state.resource.lastScrollPosition
                                    // Validate position is within bounds
                                    if (position < itemCount) {
                                        binding.rvMediaFiles.post {
                                            // Check if Activity still alive in post {} callback
                                            if (isDestroyed || isFinishing) {
                                                Timber.w("post callback (restore scroll position): Activity destroyed/finishing, skipping")
                                                return@post
                                            }
                                            
                                            val layoutManager = binding.rvMediaFiles.layoutManager
                                            when (layoutManager) {
                                                is LinearLayoutManager -> {
                                                    layoutManager.scrollToPositionWithOffset(position, 0)
                                                    Timber.i("submitList callback: ✓ Restored scroll to saved position $position (LinearLayoutManager)")
                                                }
                                                is GridLayoutManager -> {
                                                    layoutManager.scrollToPositionWithOffset(position, 0)
                                                    Timber.i("submitList callback: ✓ Restored scroll to saved position $position (GridLayoutManager)")
                                                }
                                                else -> {
                                                    binding.rvMediaFiles.scrollToPosition(position)
                                                    Timber.i("submitList callback: ✓ Restored scroll to saved position $position (fallback)")
                                                }
                                            }
                                        }
                                    } else {
                                        Timber.w("submitList callback: Saved position $position is out of bounds (itemCount=$itemCount)")
                                    }
                                }
                            }
                            
                            // Update scroll buttons visibility based on file count
                            updateScrollButtonsVisibility(itemCount)

                            // Heap protection: if heap >85% used after loading, trim Glide memory cache.
                            // Prevents silent OOM kills on devices with limited heap (e.g., 512MB).
                            val maxMem = Runtime.getRuntime().maxMemory()
                            val usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                            val heapPct = (usedMem * 100L / maxMem).toInt()
                            if (heapPct > 85) {
                                Timber.w("HEAP_MONITOR: ${heapPct}% used (${usedMem/1024/1024}MB/${maxMem/1024/1024}MB) — clearing Glide memory cache")
                                com.bumptech.glide.Glide.get(this@BrowseActivity).clearMemory()
                            }
                        }
                    }
                    // No log for skipped submitList - reduces log spam during large folder loading
                    
                    mediaFileAdapter.setSelectedPaths(state.selectedFiles)
                    state.resource?.let { resource ->
                        mediaFileAdapter.setAudioOnlyMode(resource.isAudioOnly())
                        mediaFileAdapter.setCredentialsId(resource.credentialsId)
                        mediaFileAdapter.setDisableThumbnails(resource.disableThumbnails)
                        Timber.d("THUMBNAIL_DEBUG: Resource '${resource.name}' disableThumbnails=${resource.disableThumbnails}")
                        
                        // Update item operation buttons visibility based on resource permissions
                        lifecycleScope.launch {
                            val hasDestinations = getDestinationsUseCase.getDestinationsExcluding(resource.id).isNotEmpty()
                            mediaFileAdapter.setResourcePermissions(
                                hasDestinations = hasDestinations,
                                isWritable = resource.isWritable && !resource.isReadOnly
                            )
                        }
                    }

                    // Show filter warning ONLY for user-defined filters (not resource type restrictions)
                    // Filter is considered user-defined if it has ANY criteria beyond mediaTypes
                    // OR if mediaTypes differ from resource.supportedMediaTypes
                    val filter = state.filter
                    val resource = state.resource
                    
                    Timber.v("Filter badge check: filter=$filter")
                    Timber.v("Filter badge check: resource.supportedMediaTypes=${resource?.supportedMediaTypes}")
                    
                    val isUserFilter = filter != null && !filter.isEmpty() && (
                        !filter.nameContains.isNullOrBlank() ||
                        filter.minDate != null ||
                        filter.maxDate != null ||
                        filter.minSizeMb != null ||
                        filter.maxSizeMb != null ||
                        (filter.mediaTypes != null && filter.mediaTypes != resource?.supportedMediaTypes)
                    )
                    
                    Timber.v("Filter badge check: isUserFilter=$isUserFilter (nameContains='${filter?.nameContains}', minDate=${filter?.minDate}, maxDate=${filter?.maxDate}, minSize=${filter?.minSizeMb}, maxSize=${filter?.maxSizeMb}, mediaTypes=${filter?.mediaTypes})")
                    
                    if (isUserFilter) {
                        // Show short toast instead of permanent warning line
                        // Toast already shown when filter is applied, no need to repeat
                        binding.tvFilterWarning.isVisible = false
                    } else {
                        binding.tvFilterWarning.isVisible = false
                    }
                    
                    // Update filter badge (show red circle ONLY for user-defined filters)
                    if (isUserFilter) {
                        val filterCount = state.filter?.activeFilterCount() ?: 0
                        Timber.v("Filter badge: SHOWING badge with count=$filterCount")
                        binding.btnFilter.setBadgeText(filterCount.toString())
                    } else {
                        Timber.v("Filter badge: CLEARING badge (isUserFilter=false)")
                        binding.btnFilter.clearBadge()
                    }

                    val hasSelection = state.selectedFiles.isNotEmpty()
                    // resource is already defined above
                    val isWritable = (resource?.isWritable ?: false) && (resource?.isReadOnly != true)
                    
                    // Show operations panel only when there are selected files or undo available
                    binding.layoutOperations.isVisible = hasSelection || state.lastOperation != null
                    
                    binding.btnCopy.isVisible = hasSelection
                    binding.btnMove.isVisible = hasSelection && isWritable
                    binding.btnRename.isVisible = hasSelection && isWritable
                    binding.btnDelete.isVisible = hasSelection && isWritable
                    binding.btnUndo.isVisible = state.lastOperation != null
                    binding.btnShare.isVisible = hasSelection

                    val shouldDisableToggle = resource?.isAudioOnly() == true
                    updateToggleViewAvailability(shouldDisableToggle)

                    // Update display mode when either mode OR audio-only state changed.
                    // This is required to restore normal thumbnail sizing after leaving audio-only resources.
                    if (state.displayMode != currentDisplayMode || shouldDisableToggle != currentAudioOnlyMode) {
                        currentAudioOnlyMode = shouldDisableToggle
                        currentDisplayMode = state.displayMode
                        updateDisplayMode(state.displayMode)
                    }
                    
                    // Apply or restore small controls based on setting
                    if (state.showSmallControls) {
                        smallControlsManager.applySmallControlsIfNeeded()
                    } else {
                        smallControlsManager.restoreCommandButtonHeightsIfNeeded()
                    }
                    
                    // Update breadcrumb visibility and text for subfolder navigation
                    updateBreadcrumb(state)
                    
                    // Update resource action button (edit/folder icon)
                    val stateResource = state.resource
                    Timber.d("BrowseActivity: btnResourceAction update - resource=${stateResource?.name}, isSubfolderMode=${state.isSubfolderMode}, currentPath=${state.currentPath}")
                    if (stateResource != null) {
                        binding.tvResourceInfo.text = buildResourceInfo(state)
                        
                        // currentPath == null means root of resource (not a subfolder)
                        val isSubfolder = state.isSubfolderMode && state.currentPath != null && state.currentPath != stateResource.path
                        Timber.d("BrowseActivity: btnResourceAction isSubfolder=$isSubfolder, setting VISIBLE")
                        if (isSubfolder) {
                            // Subfolder: show folder icon, no click action
                            binding.btnResourceAction.setImageResource(R.drawable.ic_folder_24)
                            binding.btnResourceAction.isClickable = false
                            binding.btnResourceAction.isFocusable = false
                            binding.btnResourceAction.visibility = android.view.View.VISIBLE
                        } else {
                            // Root resource: show edit icon, clickable
                            binding.btnResourceAction.setImageResource(R.drawable.ic_edit_20)
                            binding.btnResourceAction.isClickable = true
                            binding.btnResourceAction.isFocusable = true
                            binding.btnResourceAction.visibility = android.view.View.VISIBLE
                            binding.btnResourceAction.setOnClickListener {
                                if (!stateResource.accessPin.isNullOrBlank()) {
                                    passwordManager.checkResourcePin(stateResource) {
                                        launchEditResource(stateResource.id)
                                    }
                                } else {
                                    launchEditResource(stateResource.id)
                                }
                            }
                        }
                    } else {
                        Timber.d("BrowseActivity: btnResourceAction - resource is NULL, button stays hidden")
                    }
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
                    Timber.d("Progress observer: isLoading=$isLoading, layoutProgress update")
                    binding.layoutProgress.isVisible = isLoading
                    binding.btnStopScan.isVisible = state.isScanCancellable && isLoading
                    
                    // Hide SwipeRefreshLayout indicator when loading completes
                    if (!isLoading) {
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                    
                    // Debug logging for STOP button visibility
                    Timber.d("Progress UI update: isLoading=$isLoading, isScanCancellable=${state.isScanCancellable}, btnStopScan.visible=${state.isScanCancellable && isLoading}, progress=${state.loadingProgress}")
                    
                    // Update progress message
                    if (state.loadingProgress > 0) {
                        binding.tvProgressMessage.text = getString(R.string.loading) + " (${state.loadingProgress})"
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
                        mediaFileAdapter.notifyItemRangeChanged(0, mediaFileAdapter.itemCount, "LOAD_THUMBNAILS")
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
                    when (event) {
                        is BrowseEvent.ShowError -> {
                            showError(event.message, event.details, event.exception)
                        }
                        is BrowseEvent.ShowMessage -> {
                            Toast.makeText(this@BrowseActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is BrowseEvent.ShowUndoToast -> {
                            // Replaced Toast with Snackbar for better UX
                            val operation = viewModel.state.value.lastOperation
                            if (operation != null) {
                                showUndoSnackbar(operation)
                            }
                        }
                        is BrowseEvent.NavigateToPlayer -> {
                            viewModel.inlineStop()
                            val resourceId = viewModel.state.value.resource?.id ?: 0L
                            // Pass skipAvailabilityCheck to prevent redundant checks
                            val skipCheck = intent.getBooleanExtra(EXTRA_SKIP_AVAILABILITY_CHECK, false)
                            val playerIntent = PlayerActivity.createIntent(
                                this@BrowseActivity,
                                resourceId,
                                event.fileIndex,
                                skipCheck,
                                event.filePath // Pass file path for pagination mode
                            )
                            playerActivityLauncher.launch(playerIntent)
                            @Suppress("DEPRECATION")
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        }
                        is BrowseEvent.ShowCloudAuthenticationRequired -> {
                            showCloudAuthenticationDialog(event.provider)
                        }
                        is BrowseEvent.CloudAuthRequired -> {
                            // This event includes a custom message - show it directly
                            Toast.makeText(this@BrowseActivity, event.message, Toast.LENGTH_LONG).show()
                        }
                        is BrowseEvent.NoFilesFound -> {
                            val msg = if (event.messageResId != null) {
                                getString(event.messageResId)
                            } else {
                                event.message ?: ""
                            }
                            Toast.makeText(this@BrowseActivity, msg, Toast.LENGTH_LONG).show()
                            finish()
                        }
                        is BrowseEvent.PermissionRequired -> {
                            Timber.i("BrowseActivity: ========================================")
                            Timber.i("BrowseActivity: PERMISSION REQUIRED EVENT RECEIVED")
                            Timber.i("BrowseActivity: PendingIntent: ${event.pendingIntent}")
                            try {
                                Timber.i("BrowseActivity: Building IntentSenderRequest...")
                                val intentSenderRequest = IntentSenderRequest.Builder(event.pendingIntent).build()
                                Timber.i("BrowseActivity: Launching permission request...")
                                permissionRequestLauncher.launch(intentSenderRequest)
                                Timber.i("BrowseActivity: Permission request launched successfully")
                            } catch (e: Exception) {
                                Timber.e(e, "BrowseActivity: FAILED to launch permission request")
                                Toast.makeText(this@BrowseActivity, "Failed to request permission", Toast.LENGTH_SHORT).show()
                            }
                            Timber.i("BrowseActivity: ========================================")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Show error message respecting showDetailedErrors setting
     * If showDetailedErrors=true: shows ErrorDialog with copyable text and detailed info
     * If showDetailedErrors=false: shows Toast (short notification)
     */
    private fun showError(message: String, details: String?, exception: Throwable? = null) {
        // Check if this is a Google Drive authentication error
        if (message.contains("Google Drive authentication required", ignoreCase = true) ||
            message.contains("Not authenticated", ignoreCase = true)) {
            // Extract provider from message or use current resource's provider
            val provider = viewModel.state.value.resource?.cloudProvider 
                ?: com.sza.fastmediasorter.data.cloud.CloudProvider.GOOGLE_DRIVE
            showCloudAuthenticationDialog(provider)
            return
        }

        if (isNonCriticalNetworkImageError(message, details, exception)) {
            Timber.d("BrowseActivity: Suppressed non-critical network image error: $message")
            return
        }
        
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            Timber.d("showError: showDetailedErrors=${settings.showDetailedErrors}, message=$message, hasDetails=${details != null}, hasException=${exception != null}")
            
            if (settings.showDetailedErrors) {
                // Use ErrorDialog with full details
                if (exception != null) {
                    // Show exception with stack trace
                    com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                        context = this@BrowseActivity,
                        title = getString(R.string.error),
                        throwable = exception
                    )
                } else if (details != null) {
                    // Show message with details
                    com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                        context = this@BrowseActivity,
                        title = getString(R.string.error),
                        message = message,
                        details = details
                    )
                } else {
                    // Show only message
                    com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                        context = this@BrowseActivity,
                        title = getString(R.string.error),
                        message = message
                    )
                }
            } else {
                // Simple toast for users who don't want details
                Toast.makeText(this@BrowseActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isNonCriticalNetworkImageError(
        message: String,
        details: String?,
        exception: Throwable?
    ): Boolean {
        val messages = mutableListOf<String>()
        messages.add(message)
        if (!details.isNullOrBlank()) {
            messages.add(details)
        }

        var current = exception
        var depth = 0
        while (current != null && depth < 12) {
            messages.add(current.message ?: "")
            current = current.cause
            depth++
        }

        val hasNetworkContext = messages.any { msg ->
            msg.contains("NetworkFileDataFetcher", ignoreCase = true) ||
            msg.contains("Failed to load network file:", ignoreCase = true) ||
            msg.contains("smb://", ignoreCase = true) ||
            msg.contains("sftp://", ignoreCase = true) ||
            msg.contains("ftp://", ignoreCase = true)
        }

        if (!hasNetworkContext) return false

        return messages.any { msg ->
            msg.contains("Invalid/corrupted image data", ignoreCase = true) ||
            msg.contains("Corrupted image data:", ignoreCase = true) ||
            msg.contains("Request cancelled", ignoreCase = true) ||
            msg.contains("Failed to decode", ignoreCase = true) ||
            msg.contains("DecodeException", ignoreCase = true) ||
            msg.contains("ImageDecoder", ignoreCase = true) ||
            msg.contains("HEIC", ignoreCase = true) ||
            msg.contains("HEIF", ignoreCase = true)
        }
    }
    
    /**
     * Show Snackbar with operation description and Undo button
     */
    private fun showUndoSnackbar(operation: UndoOperation) {
        if (isFinishing || isDestroyed) {
            return
        }
        
        val count = operation.sourceFiles.size
        val description = when (operation.type) {
            com.sza.fastmediasorter.domain.model.FileOperationType.DELETE -> {
                getString(R.string.deleted_n_files, count)
            }
            com.sza.fastmediasorter.domain.model.FileOperationType.COPY -> {
                val destination = operation.destinationFolder?.substringAfterLast('/') ?: "destination"
                getString(R.string.msg_copy_success_count, count, destination)
            }
            com.sza.fastmediasorter.domain.model.FileOperationType.MOVE -> {
                val destination = operation.destinationFolder?.substringAfterLast('/') ?: "destination"
                getString(R.string.msg_move_success_count, count, destination)
            }
            com.sza.fastmediasorter.domain.model.FileOperationType.RENAME -> {
                getString(R.string.renamed_n_files, count)
            }
        }

        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            description,
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        )
            .setAction(getString(R.string.undo).uppercase()) {
                viewModel.undoLastOperation()
            }
            .setAnchorView(binding.layoutOperations)
            .show()
    }
    
    @Deprecated("Use showError() instead - respects showDetailedErrors setting")
    private fun showErrorDialog(message: String, details: String?) {
        dialogHelper.showErrorDialog(message, details)
    }

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
        currentDisplayMode = effectiveMode
        updateToggleViewAvailability(shouldForceList)
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
        // Update button text with current sort mode
        updateSortButtonText()
        
        // Setup click listener to show popup menu
        binding.btnSort.setOnClickListener {
            UserActionLogger.logButtonClick("SortButton", "BrowseActivity")
            showSortPopupMenu()
        }
    }
    
    private fun updateSortButtonText() {
        val currentMode = viewModel.state.value.sortMode
        binding.btnSort.text = getSortModeShortName(currentMode)
    }
    
    private fun showSortPopupMenu() {
        val popup = PopupMenu(this, binding.btnSort)
        val resource = viewModel.state.value.resource
        val sortModes = getRelevantSortModes(resource)

        sortModes.forEachIndexed { index, mode ->
            popup.menu.add(0, index, index, getSortModeName(mode))
        }

        popup.setOnMenuItemClickListener { menuItem ->
            val selectedMode = sortModes[menuItem.itemId]
            if (selectedMode != viewModel.state.value.sortMode) {
                UserActionLogger.logButtonClick("SortMenu_${selectedMode.name}", "BrowseActivity")
                viewModel.setSortMode(selectedMode)
                updateSortButtonText()
            }
            true
        }

        popup.show()
    }

    /**
     * Returns sort modes relevant for the given resource's media type configuration.
     * Metadata-specific modes (artist, title, duration, dateTaken) are filtered
     * to only appear when the resource actually supports the related media types.
     */
    private fun getRelevantSortModes(resource: MediaResource?): Array<SortMode> {
        val types = resource?.supportedMediaTypes ?: emptySet()
        val showAll = resource?.allFiles ?: true
        return SortMode.values().filter { mode ->
            when (mode) {
                SortMode.ARTIST_ASC, SortMode.ARTIST_DESC,
                SortMode.TITLE_ASC, SortMode.TITLE_DESC ->
                    showAll || MediaType.AUDIO in types
                SortMode.DURATION_ASC, SortMode.DURATION_DESC ->
                    showAll || MediaType.AUDIO in types || MediaType.VIDEO in types
                SortMode.DATE_TAKEN_ASC, SortMode.DATE_TAKEN_DESC ->
                    showAll || MediaType.IMAGE in types || MediaType.GIF in types
                else -> true
            }
        }.toTypedArray()
    }
    
    private fun getSortModeShortName(mode: SortMode): String {
        return when (mode) {
            SortMode.MANUAL -> "Manual"
            SortMode.NAME_ASC -> "A-Z"
            SortMode.NAME_DESC -> "Z-A"
            SortMode.DATE_ASC -> "Old ↑"
            SortMode.DATE_DESC -> "New ↓"
            SortMode.SIZE_ASC -> "Size ↑"
            SortMode.SIZE_DESC -> "Size ↓"
            SortMode.TYPE_ASC -> "Type ↑"
            SortMode.TYPE_DESC -> "Type ↓"
            SortMode.ARTIST_ASC -> "Artist ↑"
            SortMode.ARTIST_DESC -> "Artist ↓"
            SortMode.TITLE_ASC -> "Title ↑"
            SortMode.TITLE_DESC -> "Title ↓"
            SortMode.DURATION_ASC -> "Duration ↑"
            SortMode.DURATION_DESC -> "Duration ↓"
            SortMode.DATE_TAKEN_ASC -> "DateTaken ↑"
            SortMode.DATE_TAKEN_DESC -> "DateTaken ↓"
            SortMode.RANDOM -> "Random"
        }
    }
    
    private fun getSortModeName(mode: SortMode): String {
        return when (mode) {
            SortMode.MANUAL -> getString(R.string.sort_mode_manual)
            SortMode.NAME_ASC -> getString(R.string.sort_mode_name_asc)
            SortMode.NAME_DESC -> getString(R.string.sort_mode_name_desc)
            SortMode.DATE_ASC -> getString(R.string.sort_mode_date_asc)
            SortMode.DATE_DESC -> getString(R.string.sort_mode_date_desc)
            SortMode.SIZE_ASC -> getString(R.string.sort_mode_size_asc)
            SortMode.SIZE_DESC -> getString(R.string.sort_mode_size_desc)
            SortMode.TYPE_ASC -> getString(R.string.sort_mode_type_asc)
            SortMode.TYPE_DESC -> getString(R.string.sort_mode_type_desc)
            SortMode.ARTIST_ASC -> getString(R.string.sort_mode_artist_asc)
            SortMode.ARTIST_DESC -> getString(R.string.sort_mode_artist_desc)
            SortMode.TITLE_ASC -> getString(R.string.sort_mode_title_asc)
            SortMode.TITLE_DESC -> getString(R.string.sort_mode_title_desc)
            SortMode.DURATION_ASC -> getString(R.string.sort_mode_duration_asc)
            SortMode.DURATION_DESC -> getString(R.string.sort_mode_duration_desc)
            SortMode.DATE_TAKEN_ASC -> getString(R.string.sort_mode_date_taken_asc)
            SortMode.DATE_TAKEN_DESC -> getString(R.string.sort_mode_date_taken_desc)
            SortMode.RANDOM -> getString(R.string.sort_mode_random)
        }
    }

    private fun showDeleteConfirmation() {
        val state = viewModel.state.value
        val resource = state.resource
        
        if (resource?.isReadOnly == true) {
            Toast.makeText(this, R.string.error_read_only, Toast.LENGTH_SHORT).show()
            return
        }
        
        val count = state.selectedFiles.size
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            dialogHelper.showDeleteConfirmation(count, settings)
        }
    }
    
    // Task 6: Show bottom sheet menu for binary files
    private fun showBinaryFileMenu(mediaFile: com.sza.fastmediasorter.domain.model.MediaFile) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_binary_file, root, false)
        
        view.findViewById<android.widget.TextView>(R.id.tvFileName)?.text = mediaFile.name
        
        view.findViewById<android.view.View>(R.id.btnShare)?.setOnClickListener {
            shareFile(mediaFile)
            bottomSheet.dismiss()
        }
        
        view.findViewById<android.view.View>(R.id.btnOpenWith)?.setOnClickListener {
            openWithDefaultApp(mediaFile)
            bottomSheet.dismiss()
        }
        
        view.findViewById<android.view.View>(R.id.btnCopy)?.setOnClickListener {
            viewModel.selectFile(mediaFile.path)
            showCopyDialog()
            bottomSheet.dismiss()
        }
        
        view.findViewById<android.view.View>(R.id.btnMove)?.setOnClickListener {
            viewModel.selectFile(mediaFile.path)
            showMoveDialog()
            bottomSheet.dismiss()
        }
        
        view.findViewById<android.view.View>(R.id.btnRename)?.setOnClickListener {
            viewModel.selectFile(mediaFile.path)
            showRenameDialog()
            bottomSheet.dismiss()
        }
        
        view.findViewById<android.view.View>(R.id.btnDelete)?.setOnClickListener {
            viewModel.selectFile(mediaFile.path)
            showDeleteConfirmation()
            bottomSheet.dismiss()
        }
        
        bottomSheet.setContentView(view)
        bottomSheet.show()
    }
    
    // Task 6: Open binary file with default app
    private fun openWithDefaultApp(mediaFile: com.sza.fastmediasorter.domain.model.MediaFile) {
        try {
            val uri = android.net.Uri.parse(mediaFile.path)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeTypeForFile(mediaFile))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(
                    this,
                    R.string.no_app_to_open,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to open file with default app")
            Toast.makeText(this, R.string.error_opening_file_simple, Toast.LENGTH_SHORT).show()
        }
    }
    
    // Task 6: Share binary file
    private fun shareFile(mediaFile: com.sza.fastmediasorter.domain.model.MediaFile) {
        try {
            val uri = android.net.Uri.parse(mediaFile.path)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = getMimeTypeForFile(mediaFile)
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(android.content.Intent.createChooser(intent, getString(R.string.share)))
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to share file")
            Toast.makeText(this, R.string.error_sharing_file, Toast.LENGTH_SHORT).show()
        }
    }
    
    // Task 6: Get MIME type for binary file
    private fun getMimeTypeForFile(mediaFile: com.sza.fastmediasorter.domain.model.MediaFile): String {
        val extension = mediaFile.name.substringAfterLast('.', "").lowercase()
        return when (mediaFile.type) {
            com.sza.fastmediasorter.domain.model.MediaType.BINARY_ARCHIVE -> "application/$extension"
            com.sza.fastmediasorter.domain.model.MediaType.BINARY_EXECUTABLE -> when (extension) {
                "apk" -> "application/vnd.android.package-archive"
                "exe", "dll" -> "application/x-msdownload"
                else -> "application/octet-stream"
            }
            com.sza.fastmediasorter.domain.model.MediaType.BINARY_DISK -> "application/$extension"
            else -> "application/octet-stream"
        }
    }
    
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
        Timber.d("BrowseActivity.onResume: isFirstResume=$isFirstResume, shouldScrollToLastViewed=$shouldScrollToLastViewed, resourceId=${viewModel.state.value.resource?.id}")
        
        // Handle any pending cloud authentication results
        if (::cloudAuthManager.isInitialized) {
            cloudAuthManager.onResume()
        }
        
        // Adapter is no longer cleared in onPause - no need to restore
        // Memory cache (1GB) persists across PlayerActivity navigation
        
        // Skip reload on first onResume - files already loaded in ViewModel.init{}
        if (isFirstResume) {
            isFirstResume = false
            Timber.d("BrowseActivity.onResume: First resume, skipping reload (already loaded in init)")
        } else {
            Timber.d("BrowseActivity.onResume: Returned to BrowseActivity, checking for changes")
            // Check if resource settings changed (supportedMediaTypes, scanSubfolders)
            // If changed, reloads files automatically. If not, syncs with PlayerActivity cache.
            viewModel.checkAndReloadIfResourceChanged()
        }
        
        // Clear expired undo operations (older than 5 minutes)
        viewModel.clearExpiredUndoOperation()
        
        // Scroll restoration moved to submitList callback in state observer
        // This ensures RecyclerView adapter has updated before scrolling
        Timber.d("BrowseActivity.onResume: shouldScrollToLastViewed=$shouldScrollToLastViewed (will restore in submitList callback)")
        
        // Start MediaStore observer for local resources
        startMediaStoreObserver()
    }
    
    override fun onPause() {
        Timber.d("BrowseActivity.onPause: isFinishing=$isFinishing, itemCount=${binding.rvMediaFiles.adapter?.itemCount}")
        super.onPause()
        // Stop MediaStore observer to avoid unnecessary updates
        stopMediaStoreObserver()
        
        // Save scroll position when leaving Browse (back button, home, etc.)
        stateManager.saveScrollPosition()
        
        // Set flag to restore scroll position on next resume (return from PlayerActivity)
        shouldScrollToLastViewed = true
        
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
    
    private fun saveLastViewedFile() {
        stateManager.saveLastViewedFile()
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
    
    /**
     * Update scroll buttons visibility based on file count
     * Buttons are visible only when there are more than 20 files
     */
    private fun updateScrollButtonsVisibility(fileCount: Int) {
        val shouldShow = fileCount > 20
        binding.fabScrollToTop.isVisible = shouldShow
        binding.fabScrollToBottom.isVisible = shouldShow
        Timber.d("Scroll buttons visibility: $shouldShow (fileCount=$fileCount)")
    }
    
    private fun getThemeColor(attrId: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }

    companion object {
        const val EXTRA_RESOURCE_ID = "resourceId"
        const val EXTRA_SKIP_AVAILABILITY_CHECK = "skipAvailabilityCheck"

        fun createIntent(context: Context, resourceId: Long, skipAvailabilityCheck: Boolean = false): Intent {
            return Intent(context, BrowseActivity::class.java).apply {
                putExtra(EXTRA_RESOURCE_ID, resourceId)
                putExtra(EXTRA_SKIP_AVAILABILITY_CHECK, skipAvailabilityCheck)
            }
        }
    }
}
