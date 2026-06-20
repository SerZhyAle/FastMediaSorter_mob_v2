package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.app.AlertDialog
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
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.compat.MultiWindowCapabilityDetector
import com.sza.fastmediasorter.core.storage.RestrictedTreeTargetPolicy
import com.sza.fastmediasorter.core.util.AudioMetadataLoader
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.transfer.CloudFileHandle
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.PlaybackOrderMode
import com.sza.fastmediasorter.domain.model.ResourceType
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
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.ui.player.PlaybackControlPreferences
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.resourceeditor.ResourceEditorActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.utils.UserActionLogger
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.ui.browse.helpers.BrowseFileDragTouchCallback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.lang.ref.WeakReference
import com.sza.fastmediasorter.ui.player.helpers.BlackScreenOverlayManager
import com.sza.fastmediasorter.ui.player.helpers.SystemBarsManager
import dagger.Lazy

class BrowseManagerInitializer(
    private val activity: BrowseActivity,
    private val binding: ActivityBrowseBinding,
    private val viewModel: BrowseViewModel,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val passwordManager: ResourcePasswordManager,
    private val fileOperationUseCase: FileOperationUseCase,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val settingsRepository: SettingsRepository,
    private val smbClient: Lazy<SmbClient>,
    private val sftpClient: Lazy<SftpClient>,
    private val ftpClient: Lazy<FtpClient>,
    private val googleDriveClient: Lazy<GoogleDriveRestClient>,
    private val dropboxClient: Lazy<DropboxClient>,
    private val oneDriveClient: Lazy<OneDriveRestClient>,
    private val credentialsRepository: Lazy<NetworkCredentialsRepository>,
    private val unifiedFileOperationHandler: UnifiedFileOperationHandler,
    private val audioMetadataLoader: AudioMetadataLoader,
    private val unifiedFileCache: com.sza.fastmediasorter.core.cache.UnifiedFileCache,
    private val resourceOpsMenuManager: ResourceOpsMenuManager,
    private val browseFileOverflowMenuManager: com.sza.fastmediasorter.ui.browse.helpers.BrowseFileOverflowMenuManager,
    private val launcherManager: BrowseLauncherManager,
    private val showVideoThumbnailsGetter: () -> Boolean,
    private val showPdfThumbnailsGetter: () -> Boolean,
    private val updateShowVideoThumbnails: (Boolean) -> Unit,
    private val updateShowPdfThumbnails: (Boolean) -> Unit,
    private val isSkipAvailabilityCheck: Boolean,
    private val passthroughProvider: BrowsePassthroughCaptureProvider? = null,
    // Flavor-specific bottom-sheet actions are injected as a set so market builds stay feature-agnostic.
    private val binaryFileMenuActions: Set<@JvmSuppressWildcards BrowseBinaryFileMenuAction> = emptySet(),
    private val browseApkTileBadgeBinder: BrowseApkTileBadgeBinder,
    // S0135 - Google Play In-App Review request after successful Move/Copy.
    private val reviewRequestManager: com.sza.fastmediasorter.ui.browse.helpers.ReviewRequestManager,
    private val restrictedTreeTargetPolicy: RestrictedTreeTargetPolicy,
    private val mediaCapabilities: MediaCapabilities,
    private val sendToMenuManager: com.sza.fastmediasorter.ui.share.SendToMenuManager,
    private val openInShareTargetHandler: com.sza.fastmediasorter.core.share.handlers.OpenInShareTargetHandler,
) {
    // Cached settings and destinations - populated via collectors in initialize().
    // Used synchronously in onOverflowMenuClick to avoid coroutine overhead on tap.
    private var latestSettings: AppSettings? = null
    private var latestHasDestinations: Boolean = false

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
    // S0512: mouse band / touch drag multi-select over the media list.
    lateinit var dragSelectManager: BrowseDragSelectManager
    // S0096: black screen overlay for audio libraries
    internal lateinit var blackScreenManager: BlackScreenOverlayManager
    // S0374: adaptive priority+overflow controller for the top command bar.
    lateinit var commandOverflowManager: BrowseCommandOverflowManager
    private lateinit var buttonCallbacks: BrowseButtonSetupHelper.ButtonCallbacks

    fun initialize() {
        dialogHelper = BrowseDialogHelper(
            activity = activity,
            callbacks = BrowseDialogCallbacksImpl(
                viewModel = viewModel,
                context = activity,
                lifecycleOwner = activity,
                onLaunchGoogleSignIn = { cloudAuthManager.launchGoogleSignIn() },
                getCloudAuthManager = { cloudAuthManager }
            ),
            mediaCapabilities = mediaCapabilities
        )

        mediaStoreObserver = BrowseMediaStoreObserver(activity, object : BrowseMediaStoreObserver.MediaStoreCallbacks {
            override fun onMediaStoreChanged() { if (!viewModel.isIgnoringFileChanges()) viewModel.reloadFiles(syncMediaStore = false) }
        })

        mediaFileAdapter = MediaFileAdapter(
            onFileClick = { file ->
                UserActionLogger.logItemClick(file.name, context = "File click")
                viewModel.saveLastViewedFile(file.path); viewModel.openFile(file)
            },
            onFileLongClick = { file -> UserActionLogger.logItemLongClick(file.name, context = "Range selection"); viewModel.selectFileRange(file.path) },
            onContextMenuRequest = { anchor, file ->
                UserActionLogger.logItemLongClick(file.name, context = "Mouse context menu")
                showPerFileOverflowMenu(anchor, file)
            },
            onSelectionChanged = { file, selected ->
                UserActionLogger.logSelection(file.name, selected, context = "Checkbox click")
                viewModel.selectFile(file.path)
                if (selected) viewModel.saveLastViewedFile(file.path)
            },
            onSelectionRangeRequested = { file -> UserActionLogger.logItemLongClick(file.name, context = "Checkbox long click - range"); viewModel.selectFileRange(file.path) },
            onPlayClick = { file ->
                UserActionLogger.logButtonClick("InlinePlay", "File: ${file.name}")
                viewModel.saveLastViewedFile(file.path); viewModel.inlinePlayToggle(file)
            },
            onFavoriteClick = { file -> UserActionLogger.logButtonClick("Favorite", "File: ${file.name}"); viewModel.toggleFavorite(file) },
            onCopyClick = { file ->
                UserActionLogger.logButtonClick("Copy", "File: ${file.name}")
                viewModel.selectFile(file.path); showCopyDialog()
            },
            onMoveClick = { file ->
                UserActionLogger.logButtonClick("Move", "File: ${file.name}")
                viewModel.selectFile(file.path); showMoveDialog()
            },
            onRenameClick = { file ->
                UserActionLogger.logButtonClick("Rename", "File: ${file.name}")
                viewModel.selectFile(file.path); showRenameDialog()
            },
            onDeleteClick = { file ->
                UserActionLogger.logButtonClick("Delete", "File: ${file.name}")
                viewModel.selectFile(file.path); showDeleteConfirmation()
            },
            onFolderClick = { folder ->
                UserActionLogger.logItemClick(folder.name, context = "Folder click")
                if (viewModel.state.value.resource?.isAudioOnly() != true) viewModel.inlineStop()
                viewModel.navigateToFolder(folder)
            },
            onBinaryFileClick = { file -> UserActionLogger.logItemClick(file.name, context = "Binary file click"); binaryFileHandler.showBinaryFileMenu(file) },
            onOverflowMenuClick = { file, anchor -> showPerFileOverflowMenu(anchor, file) },
            apkTileBadgeBinder = browseApkTileBadgeBinder,
            getShowVideoThumbnails = showVideoThumbnailsGetter,
            getShowPdfThumbnails = showPdfThumbnailsGetter
        )

        mediaFileAdapter.setBinaryThumbnailGenerator(com.sza.fastmediasorter.util.BinaryFileThumbnailGenerator(activity))
        mediaFileAdapter.setAudioMetadataLoader(audioMetadataLoader)

        commandOverflowManager = BrowseCommandOverflowManager(binding)
        commandOverflowManager.onOverflowChanged = { activity.restitchBrowseControlChain() }

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
            onUpdateToggleViewAvailability = { disable -> updateToggleViewAvailability(disable) },
            // S0374: report runtime-gated command eligibility + trigger overflow recompute.
            setCommandEligibility = { id, eligible -> commandOverflowManager.setFeatureEligibility(id, eligible) },
            onRecomputeOverflow = { commandOverflowManager.recompute() }
        )

        recyclerViewManager = BrowseRecyclerViewManager(
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            resources = activity.resources,
            callbacks = object : BrowseRecyclerViewManager.RecyclerViewCallbacks {
                override fun onDisplayModeChanged(displayMode: DisplayMode) { stateUiUpdater.currentDisplayMode = displayMode }
                override fun updateToggleButtonIcon(iconResId: Int) { binding.btnToggleView.setIconResource(iconResId) }
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

        binding.rvMediaFiles.addOnScrollListener(BrowseScrollThumbnailListener(mediaFileAdapter) {
            scrollButtonManager.updateScrollButtonsVisibility(it)
        })

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
                    if (s.size == 1) dialogHelper.showRenameDialog(viewModel.state.value.mediaFiles.filter { it.path in s })
                }
                override fun refreshFiles() = viewModel.reloadFiles()
                override fun clearSelection() = viewModel.clearSelection()
                override fun navigateUp() {
                    if (viewModel.state.value.resource?.isAudioOnly() != true) viewModel.inlineStop()
                    viewModel.navigateUp()
                }
                override fun showCreateFolderDialog() = resourceOpsMenuManager.showCreateFolderDialog(viewModel)
                override fun showCreateTextNoteDialog() = resourceOpsMenuManager.showCreateTextNoteDialog(viewModel)
                override fun showCreateDrawingDialog() = resourceOpsMenuManager.showCreateDrawingDialog(viewModel)
                override fun showHelp() = InputHelpDialogFragment.show(activity.supportFragmentManager, UiSurface.BROWSE)
                override fun showContextMenu() {
                    val anchor = binding.rvMediaFiles.findViewHolderForAdapterPosition(stateManager.getCurrentFocusPosition())
                        ?.itemView ?: binding.rvMediaFiles
                    showBrowseResourceOpsMenu(anchor)
                }
                override fun extendSelectionUp() {
                    val pos = (stateManager.getCurrentFocusPosition() - 1).coerceAtLeast(0)
                    viewModel.state.value.mediaFiles.getOrNull(pos)?.let { viewModel.selectFileRange(it.path) }
                }
                override fun extendSelectionDown() {
                    val pos = (stateManager.getCurrentFocusPosition() + 1).coerceAtMost(viewModel.state.value.mediaFiles.size - 1)
                    viewModel.state.value.mediaFiles.getOrNull(pos)?.let { viewModel.selectFileRange(it.path) }
                }
                override fun undoLastOperation() = viewModel.undoLastOperation()
                override fun playRandomFiles() = startRandomPlay()
            }
        )

        cloudAuthManager = BrowseCloudAuthManager(
            context = activity,
            coroutineScope = lifecycleScope,
            googleDriveClient = googleDriveClient,
            dropboxClient = dropboxClient,
            oneDriveClient = oneDriveClient,
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
            sendToMenuManager = sendToMenuManager,
            dirOperationHandler = unifiedFileOperationHandler,
            callbacks = object : BrowseFileOperationsManager.FileOperationCallbacks {
                override fun onOperationCompleted() = viewModel.reloadFiles()
                override fun saveUndoOperation(undoOp: UndoOperation) = viewModel.saveUndoOperation(undoOp)
                override fun clearSelection() = viewModel.clearSelection()
                override fun getCacheDir(): File? = activity.cacheDir
                override fun getExternalCacheDir(): File? = activity.externalCacheDir
                override fun onAuthRequest(provider: String) = when (provider) {
                    "dropbox" -> cloudAuthManager.launchDropboxSignIn()
                    "google_drive" -> cloudAuthManager.launchGoogleSignIn()
                    else -> cloudAuthManager.launchOneDriveSignIn()
                }
                override fun onPermissionRequired(pendingIntent: android.app.PendingIntent) {
                    try { launcherManager.permissionRequestLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build()) }
                    catch (e: Exception) { Toast.makeText(activity, R.string.failed_to_request_permission, Toast.LENGTH_SHORT).show() }
                }
                override fun onShowMessage(message: String) { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
                override fun onShowError(message: String, details: String?) {
                    if (details != null) {
                        AlertDialog.Builder(activity)
                            .setTitle(message)
                            .setMessage(details)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    } else {
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    }
                }
                override fun onFolderPickerRequested(
                    operationType: FileOperationType, sourceFiles: List<File>, sourceCredentialsId: String?,
                    resourceType: ResourceType, resource: MediaResource, dirItems: List<MediaFile>
                ) = folderPickerHandler.requestFolderPick(operationType, sourceFiles, sourceCredentialsId, resourceType, resource, dirItems)
                override fun onSortOperationSuccess(count: Int) {
                    reviewRequestManager.onSortOperationSuccess(activity, count)
                }
            }
        )

        folderPickerHandler = BrowseFolderPickerHandler(
            activity = activity,
            coroutineScope = lifecycleScope,
            settingsRepository = settingsRepository,
            fileOperationsManager = fileOperationsManager,
            unifiedFileOperationHandler = unifiedFileOperationHandler,
            restrictedTreeTargetPolicy = restrictedTreeTargetPolicy,
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
            onExtractArchiveWithPassword = { file, password -> viewModel.extractArchiveWithPassword(file, password) },
            onCancelExtraction = { viewModel.cancelExtraction() },
            onNavigateToFolder = { viewModel.navigateToFolder(it) }
        )

        binaryFileHandler = BrowseBinaryFileHandler(
            activity = activity,
            sendToMenuManager = sendToMenuManager,
            openInHandler = openInShareTargetHandler,
            getSettings = { latestSettings },
            onSelectFile = { viewModel.selectFile(it) },
            onPrepareExtraction = { viewModel.prepareExtraction(it) },
            onShowCopyDialog = { showCopyDialog() },
            onShowMoveDialog = { showMoveDialog() },
            onShowRenameDialog = { showRenameDialog() },
            onShowDeleteConfirmation = { showDeleteConfirmation() },
            binaryFileMenuActions = binaryFileMenuActions,
        )
        // S0183: registerLaunchers MUST run before STARTED; BrowseActivity.onCreate handles it
        // synchronously (this initialize() is invoked from binding.root.post, after RESUMED).

        errorDisplayManager = BrowseErrorDisplayManager(
            activity = activity,
            rootView = binding.root,
            anchorView = binding.layoutOperations,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            onShowCloudAuthDialog = { p -> dialogHelper.showCloudAuthenticationDialog(p,
                viewModel.state.value.resource?.name ?: "", onRemoveResource = { activity.finish() }) },
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

        dragSelectManager = BrowseDragSelectManager(
            recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter,
            viewModel = viewModel,
        ).also { it.setup() }

        lifecycleHelper = BrowseLifecycleHelper(activity = activity, recyclerView = binding.rvMediaFiles,
            adapter = mediaFileAdapter, listSubmitManager = listSubmitManager)

        observerManager = BrowseObserverManager(
            lifecycleOwner = activity,
            activity = activity,
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
            onShowCloudAuthDialog = { p -> dialogHelper.showCloudAuthenticationDialog(p,
                viewModel.state.value.resource?.name ?: "", onRemoveResource = { activity.finish() }) },
            skipAvailabilityCheck = isSkipAvailabilityCheck,
            onScrollToFile = { fileName ->
                val idx = viewModel.state.value.mediaFiles.indexOfFirst { it.name == fileName }
                if (idx >= 0) binding.rvMediaFiles.post { binding.rvMediaFiles.scrollToPosition(idx) }
            }
        )

        blackScreenManager = BlackScreenOverlayManager(
            WeakReference(activity),
            SystemBarsManager(activity)
        )

        buttonSetupHelper = BrowseButtonSetupHelper(
            binding = binding,
            adapter = mediaFileAdapter,
            scrollButtonManager = scrollButtonManager
        )

        updateSortButton(viewModel.state.value.sortMode)
        binding.btnSort.setOnClickListener { UserActionLogger.logButtonClick("SortButton", "BrowseActivity"); sortMenuManager.showSortPopupMenu(binding.btnSort) }

        buttonCallbacks = object : BrowseButtonSetupHelper.ButtonCallbacks {
            override fun onFilterClicked() =
                dialogHelper.showFilterDialog(viewModel.state.value.filter, viewModel.state.value.resource?.supportedMediaTypes)
            override fun onRefreshClicked() {
                NetworkFileDataFetcher.clearFailedVideoCache()
                mediaFileAdapter.incrementRefreshVersion(); viewModel.reloadFiles()
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
                val settings = latestSettings ?: return
                fileOperationsManager.sendFilesToMenu(
                    state.mediaFiles.filter { it.path in state.selectedFiles }, resource, settings
                )
            }
            override fun onArchiveClicked() {
                val state = viewModel.state.value
                archiveDialogManager.showArchiveConfigurationDialog(state.currentPath ?: state.resource?.path ?: "",
                    state.selectedFiles, state.mediaFiles)
            }
            override fun onPlayClicked() = startSlideshow()
            override fun onPlayRandomClicked() = startRandomPlay()
            override fun onRetryClicked() { viewModel.clearError(); viewModel.reloadFiles() }
            override fun onStopScanClicked() {
                viewModel.cancelScan(forceCancel = true)
                Toast.makeText(activity, activity.getString(R.string.scan_stopped, viewModel.state.value.mediaFiles.size), Toast.LENGTH_SHORT).show()
            }
            override fun onCreateFolderClicked() {
                resourceOpsMenuManager.showCreateFolderDialog(viewModel)
            }
            override fun onCreateTextNoteClicked() {
                resourceOpsMenuManager.showCreateTextNoteDialog(viewModel)
            }
            override fun onCreateDrawingClicked() {
                resourceOpsMenuManager.showCreateDrawingDialog(viewModel)
            }
            override fun isAudioOnlyResource() = viewModel.state.value.resource?.isAudioOnly() == true
            override fun onMicRecordTouchDown() = activity.onMicRecordTouchDown()
            override fun onMicRecordTouchUp() = activity.onMicRecordTouchUp()
            override fun onMicRecordSingleTap() = activity.onMicRecordSingleTap()
            override fun onResourceOpsClicked(anchor: android.view.View) {
                showBrowseResourceOpsMenu(anchor)
            }
        }
        buttonSetupHelper.setupAllButtons(buttonCallbacks)
        
        // Warm up the cache used by onOverflowMenuClick for synchronous access.
        lifecycleScope.launch {
            settingsRepository.getSettings().collect { latestSettings = it }
        }
        lifecycleScope.launch {
            getDestinationsUseCase().collect { latestHasDestinations = it.isNotEmpty() }
        }

        buttonSetupHelper.updateToolbarButtonLabels(activity.resources.configuration)

        // S0189: wire notifyCreatedForOpen so the editor opens immediately after text note creation
        viewModel.setOpenNoteCallback { createdPath ->
            viewModel.openTextNoteInEditor(createdPath)
        }
        viewModel.setOpenDrawingCallback { createdPath ->
            viewModel.openDrawingInEditor(createdPath)
        }
    }

    /**
     * S0293 Bug A: single delegation point for the per-row ⋮ overflow menu, called both by
     * tap on the row's overflow button and by mouse-context-menu (right click) on the row.
     * Previously the latter routed into [resourceOpsMenuManager] without per-file callbacks,
     * hiding the "Open in new window" action and showing resource-level items unrelated to
     * the clicked file.
     *
     * First-frame edge case (settings cache not yet populated): silently skip the tap.
     */
    private fun showPerFileOverflowMenu(anchor: android.view.View, file: MediaFile) {
        val settings = latestSettings ?: return
        val currentState = viewModel.state.value
        // S0293: OR-compose persistent preference with runtime capability so DeX entry on phones
        // exposes the per-file "Open in new window" without requiring the user to flip the setting.
        val effectiveSettings = if (settings.allowSeparateWindow) {
            settings
        } else if (MultiWindowCapabilityDetector.isMultiWindowActiveNow(activity)) {
            settings.copy(allowSeparateWindow = true)
        } else {
            settings
        }
        browseFileOverflowMenuManager.showFor(
            anchor = anchor,
            file = file,
            appSettings = effectiveSettings,
            isWritable = currentState.resource?.isReadOnly == false,
            hasDestinations = latestHasDestinations,
            isGridMode = mediaFileAdapter.isInGridMode,
            onCopy = { f -> showCopyDialog(setOf(f.path)) },
            onMove = { f -> showMoveDialog(setOf(f.path)) },
            onRename = { f -> showRenameDialog(setOf(f.path)) },
            onDelete = { f -> showDeleteConfirmation(setOf(f.path)) },
            onMoveUp = if (currentState.sortMode == SortMode.MANUAL) {
                { f -> viewModel.moveFileUp(f) }
            } else null,
            onMoveDown = if (currentState.sortMode == SortMode.MANUAL) {
                { f -> viewModel.moveFileDown(f) }
            } else null,
            onExtractArchive = { f -> viewModel.prepareExtraction(f) },
            onFavorite = { f -> viewModel.toggleFavorite(f) },
            onSendTo = { f ->
                val resource = viewModel.state.value.resource
                if (resource != null) fileOperationsManager.sendFilesToMenu(listOf(f), resource, effectiveSettings)
            },
            onInfo = { f -> showFileInfoDialog(f) },
            onDrawOverlay = { f -> launchPlayerWithDrawOverlay(f) },
            onSearchYoutubeMusic = { f -> searchYoutubeMusicForFile(f) },
            onOpenInPlayer = { f -> viewModel.openFile(f) },
            onOpenInNewWindow = { f -> eventHandler.openPlayerInNewWindow(f) }
        )
    }

    /**
     * S0293: re-render the file adapter rows so any `allowSeparateWindow`-gated UI picks up the
     * new runtime capability flag after a multi-window / desktop-mode transition. The dropdown
     * menus already resolve the OR-composition on tap; the observer is also poked so the
     * per-row `⋮` button visibility (gated on `fileOpsInOverflowMenu`) is recomputed with the
     * OR-composed value `persisted || isMultiWindowActiveNow(activity)`.
     */
    fun notifyMultiWindowModeChanged() {
        if (::observerManager.isInitialized) {
            observerManager.notifyMultiWindowModeChanged()
        }
        if (::mediaFileAdapter.isInitialized) {
            mediaFileAdapter.notifyDataSetChanged()
        }
    }

    private fun showBrowseResourceOpsMenu(anchor: android.view.View) {
        val isScheduleEnabled = isBrowseAutomationSettingsEnabled()
        lifecycleScope.launch {
            val isDestinationsFull = runCatching { getDestinationsUseCase.isDestinationsFull() }
                .onFailure { Timber.w(it, "showBrowseResourceOpsMenu: isDestinationsFull failed") }
                .getOrDefault(false)
            val settings = settingsRepository.getSettings().first()
            val isCameraVisible = BrowseStateUiUpdater.isCameraCaptureVisible(viewModel.state.value, settings) &&
                (passthroughProvider?.isAvailable(activity) == true ||
                    BrowseCameraCaptureManager.hasCameraHandler(activity))
            // S0371: video-capture command - media-type gate AND a system video-capture handler.
            val isVideoVisible = BrowseStateUiUpdater.isVideoCaptureVisible(viewModel.state.value, settings) &&
                BrowseCameraCaptureManager.hasVideoCaptureHandler(activity)
            val resourceId = viewModel.state.value.resource?.id
            resourceOpsMenuManager.showMenu(
                anchor = anchor,
                viewModel = viewModel,
                isScheduleEnabled = isScheduleEnabled,
                onAutomateSource = if (isScheduleEnabled && resourceId != null) {
                    { openLegacyBrowseSettings(resourceId) }
                } else null,
                onAddToDestinations = { viewModel.addCurrentResourceAsDestination() },
                onArchive = { showArchiveDestinationPicker() },
                isDestinationsFull = isDestinationsFull,
                onCameraCapture = { activity.onCameraCaptureClicked() },
                isCameraVisible = isCameraVisible,
                onVideoCapture = { activity.onVideoCaptureClicked() },
                isVideoVisible = isVideoVisible,
                allowSeparateWindow = settings.allowSeparateWindow || MultiWindowCapabilityDetector.isMultiWindowActiveNow(activity),
                openBrowseInNewWindow = if (settings.allowSeparateWindow || MultiWindowCapabilityDetector.isMultiWindowActiveNow(activity)) {
                    { id -> eventHandler.openBrowseInNewWindow(id) }
                } else null,
                isAudioOnly = viewModel.state.value.resource?.isAudioOnly() == true,
                onBlackScreenClicked = if (mediaCapabilities.supportsAudio) { { blackScreenManager.show() } } else null,
                // S0374: surface top-bar commands that overflowed off the bar, routed to their actions.
                isOverflowed = { id -> commandOverflowManager.isOverflowed(id) },
                callbacks = buttonCallbacks,
                onSortClicked = { sortMenuManager.showSortPopupMenu(binding.btnSort) }
            )
        }
    }

    private fun isBrowseAutomationSettingsEnabled(): Boolean {
        return BuildConfig.ENABLE_SCHEDULED_OPERATIONS && viewModel.scheduledOperationsEnabled
    }

    private fun openLegacyBrowseSettings(resourceId: Long) {
        activity.startActivity(Intent(activity, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.EXTRA_SOURCE_RESOURCE_ID, resourceId)
        })
    }

    private fun setupDragToReorder() {
        val callback = BrowseFileDragTouchCallback(
            adapter = mediaFileAdapter,
            onDragComplete = { orderedPaths -> viewModel.saveManualOrder(orderedPaths) }
        )
        val touchHelper = ItemTouchHelper(callback).also { it.attachToRecyclerView(binding.rvMediaFiles) }
        mediaFileAdapter.setDragStartListener(object : MediaFileAdapter.DragStartListener {
            override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) { touchHelper.startDrag(viewHolder) }
        })
        updateDragHandleVisibility(viewModel.state.value.sortMode)
    }

    fun updateSortButton(sortMode: SortMode) {
        val tintColor = binding.btnSort.currentTextColor
        val sortIconRes = BrowseSortMenuManager.getSortModeIconRes(sortMode) ?: 0
        binding.btnSort.text = sortMenuManager.getSortModeShortName(sortMode)
        binding.btnSort.setCompoundDrawablesRelativeWithIntrinsicBounds(sortIconRes, 0, R.drawable.ic_arrow_drop_down, 0)
        binding.btnSort.compoundDrawablePadding =
            if (sortIconRes != 0) activity.resources.getDimensionPixelSize(R.dimen.layout_spacing_normal) else 0
        TextViewCompat.setCompoundDrawableTintList(binding.btnSort, ColorStateList.valueOf(tintColor))
    }

    fun updateDragHandleVisibility(sortMode: SortMode) {
        mediaFileAdapter.showDragHandles(sortMode == SortMode.MANUAL)
    }

    private fun performSelectAllWithToast() {
        val n = viewModel.state.value.mediaFiles.size
        viewModel.selectAll()
        if (n > 0) Toast.makeText(activity,
            activity.resources.getQuantityString(R.plurals.selected_n_files, n, n), Toast.LENGTH_SHORT).show()
    }

    /**
     * @param overridePaths When non-null, operates on this exact set instead of the current
     *   selection. Used by the per-file overflow menu to target a single file without
     *   mutating the shared multiselect state.
     */
    private fun showRenameDialog(overridePaths: Set<String>? = null) {
        val state = viewModel.state.value
        if (state.resource?.isReadOnly == true) return Toast.makeText(activity, R.string.error_read_only, Toast.LENGTH_SHORT).show()
        val selectedPaths = overridePaths ?: viewModel.currentSelectedPaths()
        dialogHelper.showRenameDialog(state.mediaFiles.filter { it.path in selectedPaths })
    }

    /**
     * @param overridePaths When non-null, operates on this exact set instead of the current
     *   selection. Used by the per-file overflow menu to target a single file without
     *   mutating the shared multiselect state.
     */
    private fun showDeleteConfirmation(overridePaths: Set<String>? = null) {
        val state = viewModel.state.value
        val resource = state.resource
        if (resource?.isReadOnly == true) return Toast.makeText(activity, R.string.error_read_only, Toast.LENGTH_SHORT).show()
        val selectedPaths = overridePaths ?: viewModel.currentSelectedPaths()
        val sel = state.mediaFiles.filter { it.path in selectedPaths }
        // Forward overridePaths so the dialog callback can target exactly these files
        // without touching the global multiselect (per-file overflow menu use case).
        lifecycleScope.launch {
            dialogHelper.showDeleteConfirmation(sel, resource, viewModel.getSettings(), overridePaths)
        }
    }

    /**
     * @param overridePaths When non-null, operates on this exact set instead of the current
     *   selection. Used by the per-file overflow menu to target a single file without
     *   mutating the shared multiselect state.
     */
    private fun showCopyDialog(overridePaths: Set<String>? = null) {
        val state = viewModel.state.value
        val resource = state.resource ?: return Toast.makeText(activity, R.string.toast_resource_not_loaded, Toast.LENGTH_SHORT).show()
        val selectedPaths = overridePaths ?: viewModel.currentSelectedPaths()
        lifecycleScope.launch {
            fileOperationsManager.showCopyDialog(selectedPaths.toList(), state.mediaFiles, resource, viewModel.getSettings())
        }
    }

    /**
     * @param overridePaths When non-null, operates on this exact set instead of the current
     *   selection. Used by the per-file overflow menu to target a single file without
     *   mutating the shared multiselect state.
     */
    private fun showMoveDialog(overridePaths: Set<String>? = null) {
        val state = viewModel.state.value
        val resource = state.resource ?: return Toast.makeText(activity, R.string.toast_resource_not_loaded, Toast.LENGTH_SHORT).show()
        if (resource.isReadOnly) return Toast.makeText(activity, R.string.error_read_only, Toast.LENGTH_SHORT).show()
        val selectedPaths = overridePaths ?: viewModel.currentSelectedPaths()
        lifecycleScope.launch {
            fileOperationsManager.showMoveDialog(selectedPaths.toList(), state.mediaFiles, resource, viewModel.getSettings())
        }
    }

    /** Picks destination folder via ARCHIVE-mode picker, then delegates to archiveDialogManager for name prompt + operation. */
    private fun showArchiveDestinationPicker() {
        val state = viewModel.state.value
        val resource = state.resource ?: return Toast.makeText(activity, R.string.toast_resource_not_loaded, Toast.LENGTH_SHORT).show()
        val selectedPaths = state.selectedFiles.toList()
        if (selectedPaths.isEmpty()) return Toast.makeText(activity, R.string.no_files_selected, Toast.LENGTH_SHORT).show()
        // S0266: cloud paths build CloudFileHandle so the display-name (e.g. "MyVideo.mp4") survives into the copy/move operation.
        val mediaFilesByPath = state.mediaFiles.associateBy { it.path }
        val sourceFiles: List<File> = selectedPaths.map { path ->
            val mf = mediaFilesByPath[path]
            if (path.startsWith("cloud://") && mf != null) {
                CloudFileHandle(cloudPath = path, displayName = mf.name, size = mf.size)
            } else {
                File(path)
            }
        }
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            FileOperationDestinationDialog(
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
                    archiveDialogManager.showArchiveConfigurationDialog(destination.path, state.selectedFiles, state.mediaFiles)
                },
                onComplete = {}
            ).show()
        }
    }

    private fun startSlideshow() {
        val state = viewModel.state.value
        val resource = state.resource
        if (state.mediaFiles.isEmpty()) { Toast.makeText(activity, R.string.toast_no_files_to_play, Toast.LENGTH_SHORT).show(); return }
        val startIndex = when {
            resource?.lastViewedFile != null -> state.mediaFiles.indexOfFirst { it.path == resource.lastViewedFile }
            state.selectedFiles.isNotEmpty() -> state.mediaFiles.indexOfFirst { it.path == state.selectedFiles.first() }
            else -> -1
        }
        val actualIndex = maxOf(0, startIndex)
        val file = state.mediaFiles[actualIndex]
        val isDoc = file.type == MediaType.TEXT || file.type == MediaType.PDF || file.type == MediaType.EPUB
        // S0241: VR stack removed - every flavor now routes to the flat PlayerActivity directly
        // via createPanelIntent. The doc branch stays so non-doc media can still pick up the
        // slideshow_mode extra; both branches share the same target activity.
        val intent = if (isDoc) {
            PlayerActivity.createPanelIntent(activity, resource?.id ?: 0L, actualIndex, isSkipAvailabilityCheck)
        } else {
            PlayerActivity.createPanelIntent(activity, resource?.id ?: 0L, actualIndex, isSkipAvailabilityCheck)
                .apply { putExtra("slideshow_mode", true) }
        }
        activity.startActivity(intent)
    }

    private fun startRandomPlay() {
        if (viewModel.state.value.mediaFiles.isEmpty()) return Toast.makeText(activity, R.string.toast_no_files_to_play, Toast.LENGTH_SHORT).show()
        viewModel.reshuffleRandom()
        val resource = viewModel.state.value.resource
        val intent = PlayerActivity.createPanelIntent(
            context = activity,
            resourceId = resource?.id ?: 0L,
            initialIndex = 0,
            skipAvailabilityCheck = isSkipAvailabilityCheck,
            shuffleOnStart = true,
        ).putExtra(
            PlaybackControlPreferences.EXTRA_PLAYBACK_ORDER_OVERRIDE,
            PlaybackOrderMode.SHUFFLE.toPrefsString()
        )
        activity.startActivity(intent)
    }

    /**
     * S0159: Show FileInfoDialog directly from Browse without opening the player.
     * Uses available file metadata; ExoPlayer-enriched duration/resolution are unavailable here.
     */
    private fun showFileInfoDialog(file: MediaFile) {
        val dialog = com.sza.fastmediasorter.ui.dialog.FileInfoDialog(
            context = activity,
            mediaFile = file,
            smbClient = smbClient.get(),
            sftpClient = sftpClient.get(),
            ftpClient = ftpClient.get(),
            credentialsRepository = credentialsRepository.get(),
            unifiedCache = unifiedFileCache,
            audioMetadataLoader = audioMetadataLoader,
        )
        dialog.show()
    }

    /**
     * S0159: Search YouTube Music directly from Browse using the filename (without extension)
     * as the query - same fallback the player uses when iTunes metadata cache is empty.
     */
    private fun searchYoutubeMusicForFile(file: MediaFile) {
        val query = file.name.substringBeforeLast(".")
        val uri = android.net.Uri.parse("https://music.youtube.com/search?q=${android.net.Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            activity.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(activity, R.string.search_in_youtube_music_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * S0159: Launch PlayerActivity for a file and immediately activate draw overlay mode.
     * PlayerActivity reads EXTRA_ACTIVATE_DRAW_MODE and calls enterDrawMode() after init.
     */
    private fun launchPlayerWithDrawOverlay(file: MediaFile) {
        val state = viewModel.state.value
        val resource = state.resource ?: return
        val index = state.mediaFiles.indexOfFirst { it.path == file.path }.coerceAtLeast(0)
        val intent = PlayerActivity.createPanelIntent(activity, resource.id, index, isSkipAvailabilityCheck)
            .putExtra(PlayerActivity.EXTRA_ACTIVATE_DRAW_MODE, true)
        activity.startActivity(intent)
    }

    private fun toggleCurrentItemSelection(position: Int) {
        if (position in 0 until viewModel.state.value.mediaFiles.size)
            viewModel.selectFile(viewModel.state.value.mediaFiles[position].path)
    }

    private fun playCurrentOrSelected(position: Int) {
        val state = viewModel.state.value
        if (state.selectedFiles.isNotEmpty())
            state.mediaFiles.firstOrNull { it.path in state.selectedFiles }?.let { viewModel.openFile(it) }
        else if (position in 0 until state.mediaFiles.size)
            viewModel.openFile(state.mediaFiles[position])
    }

    private fun updateDisplayMode(mode: DisplayMode) {
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            val resource = viewModel.state.value.resource
            val shouldForceList = resource?.isAudioOnly() == true
            val effectiveMode = if (shouldForceList) DisplayMode.LIST else mode
            val noThumbnails = resource?.disableThumbnails == true
            val iconSize = if (shouldForceList) 48 else if (noThumbnails) 32 else settings.defaultIconSize
            recyclerViewManager.updateDisplayMode(effectiveMode, iconSize, showVideoThumbnailsGetter(), settings.useCompactElements, noThumbnails)
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
        val sub = state.isSubfolderMode && state.currentPath != null
        binding.breadcrumbView.visibility = if (sub) android.view.View.VISIBLE else android.view.View.GONE
        binding.spaceAfterBack?.visibility = if (sub) android.view.View.GONE else android.view.View.VISIBLE
        if (sub) {
            val (resourceName, folders) = viewModel.getBreadcrumbParts()
            binding.breadcrumbView.setPath(resourceName, folders)
            binding.breadcrumbView.setOnSegmentClickListener { depth -> viewModel.navigateToDepth(depth) }
        } else binding.breadcrumbView.clear()
    }

    private fun launchEditResource(resourceId: Long) {
        launcherManager.editResourceLauncher.launch(ResourceEditorActivity.createEditIntent(activity, resourceId))
    }

    fun forceUpdateDisplayMode(mode: DisplayMode) = updateDisplayMode(mode)
}
