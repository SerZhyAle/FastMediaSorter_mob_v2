package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.AudioMetadataLoader
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.ui.dialog.FileOperationDestinationDialog
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.entry.VrTaskTransition
import com.sza.fastmediasorter.ui.resourceeditor.ResourceEditorActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.utils.UserActionLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import timber.log.Timber
import java.io.File

class BrowseManagerInitializer(
    private val activity: BrowseActivity,
    private val binding: ActivityBrowseBinding,
    private val viewModel: BrowseViewModel,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val passwordManager: ResourcePasswordManager,
    private val fileOperationUseCase: FileOperationUseCase,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val settingsRepository: SettingsRepository,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val googleDriveClient: GoogleDriveRestClient,
    private val dropboxClient: DropboxClient,
    private val oneDriveClient: OneDriveRestClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val unifiedFileOperationHandler: UnifiedFileOperationHandler,
    private val audioMetadataLoader: AudioMetadataLoader,
    private val resourceOpsMenuManager: ResourceOpsMenuManager,
    private val launcherManager: BrowseLauncherManager,
    private val showVideoThumbnailsGetter: () -> Boolean,
    private val showPdfThumbnailsGetter: () -> Boolean,
    private val updateShowVideoThumbnails: (Boolean) -> Unit,
    private val updateShowPdfThumbnails: (Boolean) -> Unit,
    private val isSkipAvailabilityCheck: Boolean
) {
    lateinit var mediaFileAdapter: MediaFileAdapter
    lateinit var dialogHelper: BrowseDialogHelper
    lateinit var mediaStoreObserver: BrowseMediaStoreObserver
    lateinit var recyclerViewManager: BrowseRecyclerViewManager
    lateinit var keyboardNavigationManager: KeyboardNavigationManager
    lateinit var fileOperationsManager: BrowseFileOperationsManager
    lateinit var smallControlsManager: BrowseSmallControlsManager
    lateinit var cloudAuthManager: BrowseCloudAuthManager
    lateinit var utilityManager: BrowseUtilityManager
    lateinit var stateManager: BrowseStateManager
    lateinit var sortMenuManager: BrowseSortMenuManager
    lateinit var archiveDialogManager: BrowseArchiveDialogManager
    lateinit var binaryFileHandler: BrowseBinaryFileHandler
    lateinit var errorDisplayManager: BrowseErrorDisplayManager
    lateinit var scrollButtonManager: BrowseScrollButtonManager
    lateinit var eventHandler: BrowseEventHandler
    lateinit var stateUiUpdater: BrowseStateUiUpdater
    lateinit var buttonSetupHelper: BrowseButtonSetupHelper
    lateinit var lifecycleHelper: BrowseLifecycleHelper
    lateinit var folderPickerHandler: BrowseFolderPickerHandler
    lateinit var observerManager: BrowseObserverManager
    lateinit var listSubmitManager: BrowseListSubmitManager

    fun initialize() {
        dialogHelper = BrowseDialogHelper(activity, object : BrowseDialogHelper.DialogCallbacks {
            override fun onFilterApplied(filter: FileFilter?) {
                viewModel.setFilter(filter)
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
                    Toast.makeText(activity, R.string.toast_filter_active, Toast.LENGTH_SHORT).show()
                }
            }
            override fun onSortModeSelected(sortMode: SortMode) = viewModel.setSortMode(sortMode)
            override fun onRandomReshuffle() = viewModel.reshuffleRandom()
            override fun onRenameConfirmed(oldName: String, newName: String) {}
            override fun onRenameMultipleConfirmed(files: List<Pair<String, String>>) {}
            override fun onDirectoryRenameConfirmed(oldPath: String, newName: String) = viewModel.renameDirectory(oldPath, newName)
            override fun onCopyDestinationSelected(destinationPath: String) {}
            override fun onMoveDestinationSelected(destinationPath: String) {}
            override fun onDeleteConfirmed(fileCount: Int) = viewModel.deleteSelectedFiles()
            override fun onCloudSignInRequested(provider: CloudProvider) {
                when (provider) {
                    CloudProvider.GOOGLE_DRIVE -> cloudAuthManager.launchGoogleSignIn()
                    CloudProvider.DROPBOX -> cloudAuthManager.launchDropboxSignIn()
                    CloudProvider.ONEDRIVE -> cloudAuthManager.launchOneDriveSignIn()
                }
            }
            override fun saveUndoOperation(undoOp: UndoOperation) = viewModel.saveUndoOperation(undoOp)
            override fun reloadFiles() = viewModel.reloadFiles()
            override fun updateFile(oldPath: String, newFile: MediaFile) = viewModel.updateFile(oldPath, newFile)
            override fun setIgnoringFileChanges(ignoring: Boolean) = viewModel.setIgnoringFileChanges(ignoring)
            override fun createMediaFileFromFile(file: File): MediaFile = viewModel.createMediaFileFromFile(file)
            override fun getFileOperationUseCase(): FileOperationUseCase = fileOperationUseCase
            override fun getResourceName(): String? = viewModel.state.value.resource?.name
            override fun getLifecycleOwner(): androidx.lifecycle.LifecycleOwner = activity
        })

        mediaStoreObserver = BrowseMediaStoreObserver(activity, object : BrowseMediaStoreObserver.MediaStoreCallbacks {
            override fun onMediaStoreChanged() {
                if (!viewModel.isIgnoringFileChanges()) {
                    viewModel.reloadFiles()
                }
            }
        })

        mediaFileAdapter = MediaFileAdapter(
            onFileClick = { file ->
                UserActionLogger.logItemClick(file.name, context = "File click")
                viewModel.saveLastViewedFile(file.path)
                viewModel.openFile(file)
            },
            onFileLongClick = { file ->
                UserActionLogger.logItemLongClick(file.name, context = "Range selection")
                viewModel.selectFileRange(file.path)
            },
            onContextMenuRequest = { anchor, file ->
                UserActionLogger.logItemLongClick(file.name, context = "Mouse context menu")
                resourceOpsMenuManager.showMenu(
                    anchor = anchor,
                    viewModel = viewModel,
                )
            },
            onSelectionChanged = { file, selected ->
                UserActionLogger.logSelection(file.name, selected, context = "Checkbox click")
                viewModel.selectFile(file.path)
                if (selected) viewModel.saveLastViewedFile(file.path)
            },
            onSelectionRangeRequested = { file ->
                UserActionLogger.logItemLongClick(file.name, context = "Checkbox long click - range")
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
                UserActionLogger.logItemClick(file.name, context = "Binary file click")
                binaryFileHandler.showBinaryFileMenu(file)
            },
            getShowVideoThumbnails = showVideoThumbnailsGetter,
            getShowPdfThumbnails = showPdfThumbnailsGetter
        )

        val binaryThumbnailGenerator = com.sza.fastmediasorter.util.BinaryFileThumbnailGenerator(activity)
        mediaFileAdapter.setBinaryThumbnailGenerator(binaryThumbnailGenerator)
        mediaFileAdapter.setAudioMetadataLoader(audioMetadataLoader)

        stateUiUpdater = BrowseStateUiUpdater(
            activity = activity,
            binding = binding,
            adapter = mediaFileAdapter,
            viewModel = viewModel,
            passwordManager = passwordManager,
            smallControlsManager = BrowseSmallControlsManager(binding),
            onUpdateDisplayMode = { mode -> updateDisplayMode(mode) },
            onUpdateBreadcrumb = { state -> updateBreadcrumb(state) },
            onBuildResourceInfo = { state -> BrowseUtilityManager(activity).buildResourceInfo(state) },
            onLaunchEditResource = { id -> launchEditResource(id) },
            onUpdateToggleViewAvailability = { disable -> updateToggleViewAvailability(disable) }
        )

        recyclerViewManager = BrowseRecyclerViewManager(
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            resources = activity.resources,
            callbacks = object : BrowseRecyclerViewManager.RecyclerViewCallbacks {
                override fun onDisplayModeChanged(displayMode: DisplayMode) {
                    stateUiUpdater.currentDisplayMode = displayMode
                }
                override fun updateToggleButtonIcon(iconResId: Int) {
                    binding.btnToggleView.setIconResource(iconResId)
                }
            }
        )

        scrollButtonManager = BrowseScrollButtonManager(
            activity = activity,
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            fabScrollToTop = binding.fabScrollToTop,
            fabScrollToBottom = binding.fabScrollToBottom,
            fabPageUp = binding.fabPageUp,
            fabPageDown = binding.fabPageDown
        )

        binding.rvMediaFiles.addOnScrollListener(
            BrowseScrollThumbnailListener(mediaFileAdapter) {
                scrollButtonManager.updateScrollButtonsVisibility(it)
            }
        )

        stateManager = BrowseStateManager(
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            callbacks = object : BrowseStateManager.StateCallbacks {
                override fun saveLastViewedFile(filePath: String) { viewModel.saveLastViewedFile(filePath) }
                override fun saveScrollPosition(position: Int) { viewModel.saveScrollPosition(position) }
            }
        )

        keyboardNavigationManager = KeyboardNavigationManager(
            recyclerView = binding.rvMediaFiles,
            callbacks = object : KeyboardNavigationManager.KeyboardNavigationCallbacks {
                override fun getCurrentFocusPosition(): Int = stateManager.getCurrentFocusPosition()
                override fun getMediaFilesCount(): Int = viewModel.state.value.mediaFiles.size
                override fun getSelectedFilesCount(): Int = viewModel.state.value.selectedFiles.size
                override fun toggleCurrentItemSelection(position: Int) = this@BrowseManagerInitializer.toggleCurrentItemSelection(position)
                override fun playCurrentOrSelected(position: Int) = this@BrowseManagerInitializer.playCurrentOrSelected(position)
                @Suppress("DEPRECATION")
                override fun onBackPressed() = activity.onBackPressed()
                override fun showDeleteConfirmation() = this@BrowseManagerInitializer.showDeleteConfirmation()
                override fun showCopyDialog() = this@BrowseManagerInitializer.showCopyDialog()
                override fun showMoveDialog() = this@BrowseManagerInitializer.showMoveDialog()
                override fun selectAllFiles() { performSelectAllWithToast() }
                override fun showRenameDialog() {
                    val s = viewModel.state.value.selectedFiles
                    if (s.size == 1) {
                        val files = viewModel.state.value.mediaFiles.filter { it.path in s }
                        if (files.isNotEmpty()) dialogHelper.showRenameDialog(files)
                    }
                }
                override fun refreshFiles() = viewModel.reloadFiles()
                override fun clearSelection() = viewModel.clearSelection()
                override fun navigateUp() { viewModel.navigateUp() }
                override fun showCreateFolderDialog() {
                    resourceOpsMenuManager.showCreateFolderDialog(viewModel)
                }
                override fun showHelp() {
                    com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment.show(
                        activity.supportFragmentManager,
                        com.sza.fastmediasorter.ui.common.input.InputSurface.BROWSE,
                    )
                }
                override fun showContextMenu() {
                    // Context menu is pointer-anchored; keyboard shortcut opens it at top of list.
                    val anchor = binding.rvMediaFiles.findViewHolderForAdapterPosition(
                        stateManager.getCurrentFocusPosition()
                    )?.itemView ?: binding.rvMediaFiles
                    resourceOpsMenuManager.showMenu(
                        anchor = anchor,
                        viewModel = viewModel,
                    )
                }
                override fun extendSelectionUp() {
                    val pos = (stateManager.getCurrentFocusPosition() - 1).coerceAtLeast(0)
                    viewModel.state.value.mediaFiles.getOrNull(pos)?.let { viewModel.selectFileRange(it.path) }
                }
                override fun extendSelectionDown() {
                    val files = viewModel.state.value.mediaFiles
                    val pos = (stateManager.getCurrentFocusPosition() + 1).coerceAtMost(files.size - 1)
                    files.getOrNull(pos)?.let { viewModel.selectFileRange(it.path) }
                }
                override fun undoLastOperation() {
                    viewModel.undoLastOperation()
                }
                override fun playRandomFiles() = startRandomPlay()
            }
        )

        cloudAuthManager = BrowseCloudAuthManager(
            context = activity,
            coroutineScope = lifecycleScope,
            googleDriveClient = googleDriveClient,
            dropboxClient = dropboxClient,
            oneDriveClient = oneDriveClient,
            googleSignInLauncher = launcherManager.googleSignInLauncher,
            callbacks = object : BrowseCloudAuthManager.CloudAuthCallbacks {
                override fun onAuthenticationSuccess() = viewModel.reloadFiles()
                override fun onAuthenticationFailure() {}
            }
        )

        fileOperationsManager = BrowseFileOperationsManager(
            context = activity,
            coroutineScope = lifecycleScope,
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            smbClient = smbClient,
            sftpClient = sftpClient,
            ftpClient = ftpClient,
            credentialsRepository = credentialsRepository,
            dirOperationHandler = unifiedFileOperationHandler,
            callbacks = object : BrowseFileOperationsManager.FileOperationCallbacks {
                override fun onOperationCompleted() = viewModel.reloadFiles()
                override fun saveUndoOperation(undoOp: UndoOperation) = viewModel.saveUndoOperation(undoOp)
                override fun clearSelection() = viewModel.clearSelection()
                override fun getCacheDir(): File? = activity.cacheDir
                override fun getExternalCacheDir(): File? = activity.externalCacheDir
                override fun onAuthRequest(provider: String) {
                    when (provider) {
                        "dropbox" -> cloudAuthManager.launchDropboxSignIn()
                        "google_drive" -> cloudAuthManager.launchGoogleSignIn()
                        "onedrive" -> cloudAuthManager.launchOneDriveSignIn()
                    }
                }
                override fun onPermissionRequired(pendingIntent: android.app.PendingIntent) {
                    try {
                        val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent).build()
                        launcherManager.permissionRequestLauncher.launch(intentSenderRequest)
                    } catch (e: Exception) {
                        Toast.makeText(activity, R.string.failed_to_request_permission, Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onShowMessage(message: String) { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
                override fun onShowError(message: String, details: String?) {
                    val text = if (details != null) "$message\n$details" else message
                    Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
                }
                override fun onFolderPickerRequested(
                    operationType: com.sza.fastmediasorter.domain.model.FileOperationType,
                    sourceFiles: List<java.io.File>,
                    sourceCredentialsId: String?,
                    resourceType: com.sza.fastmediasorter.domain.model.ResourceType,
                    resource: com.sza.fastmediasorter.domain.model.MediaResource,
                    dirItems: List<com.sza.fastmediasorter.domain.model.MediaFile>
                ) {
                    folderPickerHandler.requestFolderPick(operationType, sourceFiles, sourceCredentialsId, resourceType, resource, dirItems)
                }
            }
        )

        folderPickerHandler = BrowseFolderPickerHandler(
            activity = activity,
            coroutineScope = lifecycleScope,
            settingsRepository = settingsRepository,
            fileOperationsManager = fileOperationsManager,
            unifiedFileOperationHandler = unifiedFileOperationHandler,
            onLaunchPicker = { initialUri -> launcherManager.folderPickerLauncher.launch(initialUri) }
        )

        utilityManager = BrowseUtilityManager(activity)
        smallControlsManager = BrowseSmallControlsManager(binding)

        sortMenuManager = BrowseSortMenuManager(
            context = activity,
            onSortModeSelected = { viewModel.setSortMode(it) },
            onRandomReshuffle = { viewModel.reshuffleRandom() },
            getCurrentSortMode = { viewModel.state.value.sortMode },
            getCurrentResource = { viewModel.state.value.resource }
        )

        archiveDialogManager = BrowseArchiveDialogManager(
            context = activity,
            onArchiveRequested = { name, dir -> viewModel.archiveSelectedFiles(name, dir) },
            onCancelArchive = { viewModel.cancelArchive() },
            onExtractArchive = { viewModel.extractArchive(it) },
            onCancelExtraction = { viewModel.cancelExtraction() },
            onNavigateToFolder = { viewModel.navigateToFolder(it) }
        )

        binaryFileHandler = BrowseBinaryFileHandler(
            activity = activity,
            onSelectFile = { viewModel.selectFile(it) },
            onPrepareExtraction = { viewModel.prepareExtraction(it) },
            onShowCopyDialog = { showCopyDialog() },
            onShowMoveDialog = { showMoveDialog() },
            onShowRenameDialog = { showRenameDialog() },
            onShowDeleteConfirmation = { showDeleteConfirmation() }
        )

        errorDisplayManager = BrowseErrorDisplayManager(
            activity = activity,
            rootView = binding.root,
            anchorView = binding.layoutOperations,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            onShowCloudAuthDialog = { provider ->
                dialogHelper.showCloudAuthenticationDialog(
                    provider,
                    viewModel.state.value.resource?.name ?: "",
                    // lastBrowseDate is already cleared in BrowseLoadingAuxManager,
                    // so closing the screen is sufficient to break the auto-reopen loop.
                    onRemoveResource = { activity.finish() }
                )
            },
            onUndoRequested = { viewModel.undoLastOperation() },
            getCurrentCloudProvider = { viewModel.state.value.resource?.cloudProvider }
        )

        listSubmitManager = BrowseListSubmitManager(
            activity = activity,
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            emptyStateView = binding.emptyStateView,
            scrollButtonManager = scrollButtonManager,
            isLoadingProvider = { viewModel.loading.value }
        )

        setupDragToReorder()

        lifecycleHelper = BrowseLifecycleHelper(
            activity = activity,
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            listSubmitManager = listSubmitManager
        )

        observerManager = BrowseObserverManager(
            lifecycleOwner = activity,
            binding = binding,
            viewModel = viewModel,
            adapter = mediaFileAdapter,
            settingsRepository = settingsRepository,
            onUpdateDisplayMode = { mode -> updateDisplayMode(mode) },
            onNotifyRangeChanged = { start, count, payload, source -> scrollButtonManager.notifyItemRangeChangedSafely(start, count, payload, source) },
            getShowPdfThumbnails = showPdfThumbnailsGetter,
            setShowVideoThumbnails = updateShowVideoThumbnails,
            setShowPdfThumbnails = updateShowPdfThumbnails
        )

        eventHandler = BrowseEventHandler(
            activity = activity,
            viewModel = viewModel,
            errorDisplayManager = errorDisplayManager,
            archiveDialogManager = archiveDialogManager,
            resourceOpsMenuManager = resourceOpsMenuManager,
            permissionRequestLauncher = launcherManager.permissionRequestLauncher,
            playerActivityLauncher = launcherManager.playerActivityLauncher,
            onShowCloudAuthDialog = { provider ->
                dialogHelper.showCloudAuthenticationDialog(
                    provider,
                    viewModel.state.value.resource?.name ?: "",
                    onRemoveResource = { activity.finish() }
                )
            },
            skipAvailabilityCheck = isSkipAvailabilityCheck,
            onScrollToFile = { fileName ->
                // Read index from VM state (already up-to-date) — adapter.currentList lags behind
                // because ListAdapter's AsyncListDiffer commits the diff asynchronously.
                val idx = viewModel.state.value.mediaFiles.indexOfFirst { it.name == fileName }
                if (idx >= 0) binding.rvMediaFiles.post { binding.rvMediaFiles.scrollToPosition(idx) }
            }
        )

        buttonSetupHelper = BrowseButtonSetupHelper(
            binding = binding,
            adapter = mediaFileAdapter,
            scrollButtonManager = scrollButtonManager
        )

        updateSortButton(viewModel.state.value.sortMode)
        binding.btnSort.setOnClickListener {
            UserActionLogger.logButtonClick("SortButton", "BrowseActivity")
            sortMenuManager.showSortPopupMenu(binding.btnSort)
        }

        buttonSetupHelper.setupAllButtons(object : BrowseButtonSetupHelper.ButtonCallbacks {
            override fun onFilterClicked() {
                val allowedTypes = viewModel.state.value.resource?.supportedMediaTypes
                dialogHelper.showFilterDialog(viewModel.state.value.filter, allowedTypes)
            }
            override fun onRefreshClicked() {
                NetworkFileDataFetcher.clearFailedVideoCache()
                mediaFileAdapter.incrementRefreshVersion()
                viewModel.reloadFiles()
            }
            override fun onToggleViewClicked() = viewModel.toggleDisplayMode()
            override fun onSelectAllClicked() = performSelectAllWithToast()
            override fun onDeselectAllClicked() = viewModel.clearSelection()
            override fun onCopyClicked() = showCopyDialog()
            override fun onMoveClicked() = showMoveDialog()
            override fun onRenameClicked() = showRenameDialog()
            override fun onDeleteClicked() = showDeleteConfirmation()
            override fun onUndoClicked() = viewModel.undoLastOperation()
            override fun onShareClicked() {
                val state = viewModel.state.value
                val resource = state.resource ?: return
                val sel = state.mediaFiles.filter { it.path in state.selectedFiles }
                fileOperationsManager.shareSelectedFiles(sel, resource)
            }
            override fun onArchiveClicked() {
                val state = viewModel.state.value
                archiveDialogManager.showArchiveConfigurationDialog(
                    currentDir = state.currentPath ?: state.resource?.path ?: "",
                    selectedFiles = state.selectedFiles,
                    mediaFiles = state.mediaFiles
                )
            }
            override fun onPlayClicked() = startSlideshow()
            override fun onPlayRandomClicked() = startRandomPlay()
            override fun onRetryClicked() {
                viewModel.clearError()
                viewModel.reloadFiles()
            }
            override fun onStopScanClicked() {
                viewModel.cancelScan(forceCancel = true)
                Toast.makeText(activity, activity.getString(R.string.scan_stopped, viewModel.state.value.mediaFiles.size), Toast.LENGTH_SHORT).show()
            }
            override fun isAudioOnlyResource() = viewModel.state.value.resource?.isAudioOnly() == true
            override fun onResourceOpsClicked(anchor: android.view.View) {
                val isScheduleEnabled = BuildConfig.ENABLE_SCHEDULED_OPERATIONS && viewModel.scheduledOperationsEnabled
                // Destinations-full check runs async — resolve it before opening the popup so the
                // menu item visibility is correct on first render (the popup itself is not suspendable).
                lifecycleScope.launch {
                    val isDestinationsFull = try {
                        getDestinationsUseCase.isDestinationsFull()
                    } catch (e: Exception) {
                        Timber.w(e, "onResourceOpsClicked: failed to resolve isDestinationsFull — defaulting to false")
                        false
                    }
                    val settings = settingsRepository.getSettings().first()
                    // Check state-based predicate first (toggle + media type + path), then
                    // confirm the system actually has a handler for the capture intent.
                    // hasCameraHandler() is the key fix for Quest 3 where handlers=0.
                    val isCameraVisibleByState = BrowseStateUiUpdater.isCameraCaptureVisible(viewModel.state.value, settings)
                    val isCameraVisible = isCameraVisibleByState &&
                        viewModel.state.value.resource?.let { res ->
                            BrowseCameraCaptureManager.hasCameraHandler(activity, res)
                        } ?: false
                    resourceOpsMenuManager.showMenu(
                        anchor = anchor,
                        viewModel = viewModel,
                        isScheduleEnabled = isScheduleEnabled,
                        onAutomateSource = if (isScheduleEnabled) {
                            {
                                val resourceId = viewModel.state.value.resource?.id ?: return@showMenu
                                activity.startActivity(Intent(activity, SettingsActivity::class.java).apply {
                                    putExtra(SettingsActivity.EXTRA_SOURCE_RESOURCE_ID, resourceId)
                                })
                            }
                        } else null,
                        onAddToDestinations = { viewModel.addCurrentResourceAsDestination() },
                        onArchive = { showArchiveDestinationPicker() },
                        isDestinationsFull = isDestinationsFull,
                        onCameraCapture = { activity.onCameraCaptureClicked() },
                        isCameraVisible = isCameraVisible
                    )
                }
            }
        })
        
        buttonSetupHelper.updateToolbarButtonLabels(activity.resources.configuration)
    }

    private fun setupDragToReorder() {
        val callback = com.sza.fastmediasorter.ui.browse.helpers.BrowseFileDragTouchCallback(
            adapter = mediaFileAdapter,
            onDragComplete = { orderedPaths -> viewModel.saveManualOrder(orderedPaths) }
        )
        val touchHelper = androidx.recyclerview.widget.ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.rvMediaFiles)

        mediaFileAdapter.setDragStartListener(object : com.sza.fastmediasorter.ui.browse.MediaFileAdapter.DragStartListener {
            override fun onStartDrag(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
                touchHelper.startDrag(viewHolder)
            }
        })

        // Show/hide handles whenever sort mode changes
        updateDragHandleVisibility(viewModel.state.value.sortMode)
    }

    fun updateSortButton(sortMode: SortMode) {
        val tintColor = binding.btnSort.currentTextColor
        val sortIconRes = BrowseSortMenuManager.getSortModeIconRes(sortMode) ?: 0

        binding.btnSort.text = sortMenuManager.getSortModeShortName(sortMode)
        // Keep btnSort as a compact TextView so both browse layouts retain their current sizing.
        binding.btnSort.setCompoundDrawablesRelativeWithIntrinsicBounds(
            sortIconRes,
            0,
            R.drawable.ic_arrow_drop_down,
            0
        )
        binding.btnSort.compoundDrawablePadding = if (sortIconRes != 0) {
            activity.resources.getDimensionPixelSize(R.dimen.layout_spacing_normal)
        } else {
            0
        }
        TextViewCompat.setCompoundDrawableTintList(binding.btnSort, ColorStateList.valueOf(tintColor))
    }

    fun updateDragHandleVisibility(sortMode: SortMode) {
        mediaFileAdapter.showDragHandles(sortMode == SortMode.MANUAL)
    }

    private fun performSelectAllWithToast() {
        val totalCount = viewModel.state.value.mediaFiles.size
        viewModel.selectAll()
        if (totalCount > 0) {
            val message = activity.resources.getQuantityString(R.plurals.selected_n_files, totalCount, totalCount)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog() {
        val state = viewModel.state.value
        if (state.resource?.isReadOnly == true) {
            Toast.makeText(activity, R.string.error_read_only, Toast.LENGTH_SHORT).show()
            return
        }
        // Read selection directly from selectionManager to avoid async state propagation lag
        val selectedPaths = viewModel.currentSelectedPaths()
        val sel = state.mediaFiles.filter { it.path in selectedPaths }
        dialogHelper.showRenameDialog(sel)
    }

    private fun showDeleteConfirmation() {
        val state = viewModel.state.value
        val resource = state.resource
        if (resource?.isReadOnly == true) {
            Toast.makeText(activity, R.string.error_read_only, Toast.LENGTH_SHORT).show()
            return
        }
        // Read selection directly from selectionManager to avoid async state propagation lag
        val selectedPaths = viewModel.currentSelectedPaths()
        val sel = state.mediaFiles.filter { it.path in selectedPaths }
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            dialogHelper.showDeleteConfirmation(sel, resource, settings)
        }
    }

    private fun showCopyDialog() {
        val state = viewModel.state.value
        val resource = state.resource ?: run {
            Toast.makeText(activity, R.string.toast_resource_not_loaded, Toast.LENGTH_SHORT).show()
            return
        }
        // Read selection directly from selectionManager to avoid async state propagation lag
        val selectedPaths = viewModel.currentSelectedPaths()
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            fileOperationsManager.showCopyDialog(selectedPaths.toList(), state.mediaFiles, resource, settings)
        }
    }

    private fun showMoveDialog() {
        val state = viewModel.state.value
        val resource = state.resource ?: run {
            Toast.makeText(activity, R.string.toast_resource_not_loaded, Toast.LENGTH_SHORT).show()
            return
        }
        if (resource.isReadOnly) {
            Toast.makeText(activity, R.string.error_read_only, Toast.LENGTH_SHORT).show()
            return
        }
        // Read selection directly from selectionManager to avoid async state propagation lag
        val selectedPaths = viewModel.currentSelectedPaths()
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            fileOperationsManager.showMoveDialog(selectedPaths.toList(), state.mediaFiles, resource, settings)
        }
    }

    /**
     * Archive menu entry: pick a destination folder via the Copy/Move picker in ARCHIVE mode,
     * then hand the chosen path to [BrowseArchiveDialogManager.showArchiveConfigurationDialog]
     * which prompts for the archive name and runs the operation.
     *
     * Destination picker excludes the current source resource (same semantics as Copy/Move);
     * the toolbar btnArchive already covers archiving into the current folder.
     */
    private fun showArchiveDestinationPicker() {
        val state = viewModel.state.value
        val resource = state.resource ?: run {
            Toast.makeText(activity, R.string.toast_resource_not_loaded, Toast.LENGTH_SHORT).show()
            return
        }
        val selectedPaths = state.selectedFiles.toList()
        if (selectedPaths.isEmpty()) {
            Toast.makeText(activity, R.string.no_files_selected, Toast.LENGTH_SHORT).show()
            return
        }

        val sourceFiles = selectedPaths.map { File(it) }
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            val dialog = FileOperationDestinationDialog(
                context = activity,
                operationType = FileOperationType.ARCHIVE,
                sourceFiles = sourceFiles,
                sourceFolderName = resource.name,
                currentResourceId = resource.id,
                currentBrowsePath = state.currentPath,
                sourceCredentialsId = resource.credentialsId,
                fileOperationUseCase = fileOperationUseCase,
                getDestinationsUseCase = getDestinationsUseCase,
                overwriteFiles = false,
                showDetailedErrors = settings.showDetailedErrors,
                onDestinationSelected = { destination ->
                    // ARCHIVE mode: dialog captures destination and dismisses; hand off to the
                    // existing archive-configuration dialog (name prompt + progress/result flow).
                    archiveDialogManager.showArchiveConfigurationDialog(
                        currentDir = destination.path,
                        selectedFiles = state.selectedFiles,
                        mediaFiles = state.mediaFiles
                    )
                },
                onComplete = { /* no-op: ARCHIVE mode does not run an operation here */ }
            )
            dialog.show()
        }
    }

    private fun startSlideshow() {
        val state = viewModel.state.value
        val resource = state.resource
        val startIndex = if (resource?.lastViewedFile != null) {
            state.mediaFiles.indexOfFirst { it.path == resource.lastViewedFile }
        } else if (state.selectedFiles.isNotEmpty()) {
            state.mediaFiles.indexOfFirst { it.path == state.selectedFiles.first() }
        } else {
            -1
        }
        val actualIndex = if (startIndex >= 0) startIndex else 0
        if (state.mediaFiles.isNotEmpty()) {
            val file = state.mediaFiles[actualIndex]
            val isDoc = file.type == MediaType.TEXT || file.type == MediaType.PDF || file.type == MediaType.EPUB
            val intent = PlayerActivity.createIntent(activity, resource?.id ?: 0L, actualIndex, isSkipAvailabilityCheck).apply {
                if (!isDoc) putExtra("slideshow_mode", true)
            }
            if (VrTaskTransition.shouldEnterImmersiveTask(intent)) {
                VrTaskTransition.enterImmersive(activity, intent)
            } else {
                activity.startActivity(intent)
            }
        } else {
            Toast.makeText(activity, R.string.toast_no_files_to_play, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Reshuffles the current file list (switches sort to RANDOM) and immediately
     * launches PlayerActivity starting from the first file in the reshuffled list.
     * This gives a true "play random" UX: list order is randomised AND playback starts
     * at a random position every time the button is pressed.
     */
    private fun startRandomPlay() {
        if (viewModel.state.value.mediaFiles.isEmpty()) {
            Toast.makeText(activity, R.string.toast_no_files_to_play, Toast.LENGTH_SHORT).show()
            return
        }
        // Reshuffle (persists RANDOM sort mode) — the list state updates asynchronously,
        // so we capture the reshuffled list synchronously via the ViewModel for the launch.
        viewModel.reshuffleRandom()
        val state = viewModel.state.value
        val resource = state.resource
        val files = state.mediaFiles
        if (files.isEmpty()) {
            Toast.makeText(activity, R.string.toast_no_files_to_play, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = PlayerActivity.createIntent(activity, resource?.id ?: 0L, 0, isSkipAvailabilityCheck)
        if (VrTaskTransition.shouldEnterImmersiveTask(intent)) {
            VrTaskTransition.enterImmersive(activity, intent)
        } else {
            activity.startActivity(intent)
        }
    }

    private fun toggleCurrentItemSelection(position: Int) {
        if (position in 0 until viewModel.state.value.mediaFiles.size) {
            viewModel.selectFile(viewModel.state.value.mediaFiles[position].path)
        }
    }

    private fun playCurrentOrSelected(position: Int) {
        val state = viewModel.state.value
        if (state.selectedFiles.isNotEmpty()) {
            state.mediaFiles.firstOrNull { it.path in state.selectedFiles }?.let { viewModel.openFile(it) }
        } else if (position in 0 until state.mediaFiles.size) {
            viewModel.openFile(state.mediaFiles[position])
        }
    }

    private fun updateDisplayMode(mode: DisplayMode) {
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            val currentResource = viewModel.state.value.resource
            val shouldForceList = currentResource?.isAudioOnly() == true
            val effectiveMode = if (shouldForceList) DisplayMode.LIST else mode
            val iconSize = if (shouldForceList) 48 else if (currentResource?.disableThumbnails == true) 32 else settings.defaultIconSize

            recyclerViewManager.updateDisplayMode(effectiveMode, iconSize, showVideoThumbnailsGetter(), settings.useCompactElements)
            stateUiUpdater.currentDisplayMode = effectiveMode
            updateToggleViewAvailability(shouldForceList)
            binding.rvMediaFiles.post { scrollButtonManager.updateScrollButtonsVisibility(mediaFileAdapter.itemCount) }
        }
    }

    private fun updateToggleViewAvailability(shouldDisable: Boolean) {
        binding.btnToggleView.apply {
            isEnabled = !shouldDisable
            alpha = if (shouldDisable) 0.4f else 1.0f
            visibility = if (shouldDisable) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    private fun updateBreadcrumb(state: com.sza.fastmediasorter.ui.browse.BrowseState) {
        if (state.isSubfolderMode && state.currentPath != null) {
            binding.breadcrumbView.visibility = android.view.View.VISIBLE
            binding.spaceAfterBack?.visibility = android.view.View.GONE
            val (resourceName, folders) = viewModel.getBreadcrumbParts()
            binding.breadcrumbView.setPath(resourceName, folders)
            binding.breadcrumbView.setOnSegmentClickListener { depth -> viewModel.navigateToDepth(depth) }
        } else {
            binding.breadcrumbView.visibility = android.view.View.GONE
            binding.breadcrumbView.clear()
            binding.spaceAfterBack?.visibility = android.view.View.VISIBLE
        }
    }

    private fun launchEditResource(resourceId: Long) {
        launcherManager.editResourceLauncher.launch(ResourceEditorActivity.createEditIntent(activity, resourceId))
    }

    fun forceUpdateDisplayMode(mode: DisplayMode) {
        updateDisplayMode(mode)
    }
}
