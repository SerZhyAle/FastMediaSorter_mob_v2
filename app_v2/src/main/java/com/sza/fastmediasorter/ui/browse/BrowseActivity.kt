package com.sza.fastmediasorter.ui.browse

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker
import com.sza.fastmediasorter.core.input.GamepadInputManager
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.core.memory.MemoryCheckpoint
import com.sza.fastmediasorter.core.memory.MemoryProbe
import com.sza.fastmediasorter.core.memory.MemoryProfileCoordinator
import com.sza.fastmediasorter.core.memory.MemoryScenario
import com.sza.fastmediasorter.core.storage.RestrictedTreeTargetPolicy
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.transfer.strategies.LocalToFtpStrategy
import com.sza.fastmediasorter.data.transfer.strategies.LocalToSftpStrategy
import com.sza.fastmediasorter.data.transfer.strategies.LocalToSmbStrategy
import com.sza.fastmediasorter.data.transfer.strategy.CloudOperationStrategy
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.input.InputSurface
import com.sza.fastmediasorter.domain.model.GamepadAction
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.ui.browse.managers.BrowseApkTileBadgeBinder
import com.sza.fastmediasorter.ui.browse.managers.BrowseBinaryFileMenuAction
import com.sza.fastmediasorter.ui.browse.managers.BrowseCameraCaptureManager
import com.sza.fastmediasorter.ui.browse.managers.BrowseLauncherCallbacks
import com.sza.fastmediasorter.ui.browse.managers.BrowseLauncherManager
import com.sza.fastmediasorter.ui.browse.managers.BrowseManagerInitializer
import com.sza.fastmediasorter.ui.browse.managers.BrowseMicRecordingManager
import com.sza.fastmediasorter.ui.browse.managers.BrowsePassthroughCaptureProvider
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferCoordinator
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.utils.UserActionLogger
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.LazyThreadSafetyMode

@AndroidEntryPoint
class BrowseActivity : BaseActivity<ActivityBrowseBinding>() {

    private val viewModel: BrowseViewModel by viewModels()
    private lateinit var passwordManager: ResourcePasswordManager
    private lateinit var initializer: BrowseManagerInitializer
    private lateinit var launcherManager: BrowseLauncherManager
    private lateinit var cameraCaptureManager: BrowseCameraCaptureManager
    private lateinit var micRecordingManager: BrowseMicRecordingManager

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.state.value.resource?.let { micRecordingManager.startRecording(it) }
        } else {
            Toast.makeText(this, R.string.mic_recording_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    private var pendingCameraPermissionResourceId: Long? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingId = pendingCameraPermissionResourceId
        pendingCameraPermissionResourceId = null
        if (granted) {
            val resource = viewModel.state.value.resource?.takeIf { it.id == pendingId }
            if (resource != null) {
                cameraCaptureManager.launch(resource)
            }
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    // S0371: separate pending id + launcher for the video-capture command so a granted CAMERA
    // permission resumes into launchVideo (not the photo path).
    private var pendingVideoCaptureResourceId: Long? = null

    private val videoCapturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingId = pendingVideoCaptureResourceId
        pendingVideoCaptureResourceId = null
        if (granted) {
            val resource = viewModel.state.value.resource?.takeIf { it.id == pendingId }
            if (resource != null) {
                cameraCaptureManager.launchVideo(resource)
            }
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    @Inject lateinit var googleDriveClient: Lazy<GoogleDriveRestClient>
    @Inject lateinit var resourceOpsMenuManager: com.sza.fastmediasorter.ui.browse.managers.ResourceOpsMenuManager
    @Inject lateinit var browseFileOverflowMenuManager: com.sza.fastmediasorter.ui.browse.helpers.BrowseFileOverflowMenuManager
    @Inject lateinit var dropboxClient: Lazy<com.sza.fastmediasorter.data.cloud.DropboxClient>
    @Inject lateinit var oneDriveClient: Lazy<com.sza.fastmediasorter.data.cloud.OneDriveRestClient>
    @Inject lateinit var fileOperationUseCase: FileOperationUseCase
    @Inject lateinit var getDestinationsUseCase: GetDestinationsUseCase
    @Inject lateinit var settingsRepository: SettingsRepository
    // S0367: resolves a configured capture-destination resource id to a MediaResource for the
    // camera-photos / mic-recording destination override.
    @Inject lateinit var resourceRepository: com.sza.fastmediasorter.domain.repository.ResourceRepository
    @Inject lateinit var smbClient: Lazy<SmbClient>
    @Inject lateinit var sftpClient: Lazy<SftpClient>
    @Inject lateinit var ftpClient: Lazy<FtpClient>
    @Inject lateinit var credentialsRepository: Lazy<com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository>
    @Inject lateinit var unifiedFileOperationHandler: com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
    @Inject lateinit var audioMetadataLoader: com.sza.fastmediasorter.core.util.AudioMetadataLoader
    @Inject lateinit var unifiedFileCache: com.sza.fastmediasorter.core.cache.UnifiedFileCache
    @Inject lateinit var gamepadInputManager: GamepadInputManager
    @Inject lateinit var keyBindingManager: KeyBindingManager
    @Inject lateinit var localToFtpStrategy: LocalToFtpStrategy
    @Inject lateinit var localToSmbStrategy: LocalToSmbStrategy
    @Inject lateinit var localToSftpStrategy: LocalToSftpStrategy
    @Inject lateinit var cameraCaptureSaver: com.sza.fastmediasorter.data.capture.CameraCaptureSaver
    @Inject lateinit var passthroughCaptureProvider: java.util.Optional<BrowsePassthroughCaptureProvider>
    // Flavor multibinding keeps flavor-only Browse actions out of market APKs.
    @Inject lateinit var binaryFileMenuActions: Set<@JvmSuppressWildcards BrowseBinaryFileMenuAction>
    @Inject lateinit var browseApkTileBadgeBinder: BrowseApkTileBadgeBinder
    @Inject lateinit var reviewRequestManager: com.sza.fastmediasorter.ui.browse.helpers.ReviewRequestManager
    @Inject lateinit var browseTransferCoordinator: BrowseFileTransferCoordinator
    @Inject lateinit var restrictedTreeTargetPolicy: RestrictedTreeTargetPolicy
    @Inject lateinit var mediaCapabilities: com.sza.fastmediasorter.core.capability.MediaCapabilities
    @Inject lateinit var sendToMenuManager: com.sza.fastmediasorter.ui.share.SendToMenuManager
    @Inject lateinit var openInShareTargetHandler: com.sza.fastmediasorter.core.share.handlers.OpenInShareTargetHandler
    // S0242 Phase 03: sole consumer of the MutationJournal on the Browse side.
    @Inject lateinit var browseReconcilerManager: com.sza.fastmediasorter.ui.browse.managers.BrowseReconcilerManager

    // S0207 Phase 01: BROWSE_OPENED memory checkpoint emitter.
    @Inject lateinit var memoryProbe: MemoryProbe
    @Inject lateinit var memoryProfileCoordinator: MemoryProfileCoordinator
    // S0189: staging infra for text notes (and S0191 drawings) created in network/cloud directories
    @Inject lateinit var stagingDirectoryProvider: com.sza.fastmediasorter.data.local.staging.StagingDirectoryProvider
    @Inject lateinit var localStagingRegistry: com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
    // S0231: scoped-storage-aware writer injected for ad-hoc CloudOperationStrategy construction.
    @Inject lateinit var destinationClassifier: com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
    @Inject lateinit var destinationWriter: com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
    // S0473: usage-statistics sink, threaded into BrowseMicRecordingManager for voice-note capture.
    @Inject lateinit var statsSink: com.sza.fastmediasorter.domain.stats.StatsSink
    @Inject lateinit var networkStateMonitor: com.sza.fastmediasorter.core.network.NetworkStateMonitor
    @Inject lateinit var saveFallbackNotifier: com.sza.fastmediasorter.core.save.SaveFallbackNotifier
    @Inject lateinit var micRecordingSaver: com.sza.fastmediasorter.data.capture.MicRecordingSaver

    private var showVideoThumbnails = true
    private var showPdfThumbnails = false
    private var isFirstResume = true
    private var lastSubmittedSortMode: com.sza.fastmediasorter.domain.model.SortMode? = null
    // S0028: per-window resume state isolation
    private var windowId: String = ResumeStateRepository.WINDOW_ID_MAIN
    // S0196 Phase 04 measurement hook: one-shot tag emitted on the first non-empty list bind so
    // perf traces can mark "primary content rendered" for the browse surface.
    private var firstListBoundLogged = false
    private val cloudOperationStrategy by lazy(LazyThreadSafetyMode.NONE) {
        CloudOperationStrategy(
            this,
            googleDriveClient.get(),
            dropboxClient.get(),
            oneDriveClient.get(),
            stagingDirectoryProvider,
            localStagingRegistry,
            destinationClassifier,
            destinationWriter
        )
    }
    private val cloudOperationStrategyProvider: () -> CloudOperationStrategy = { cloudOperationStrategy }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        // S0183: ActivityResultLaunchers MUST be registered before the lifecycle progresses past
        // CREATED - registerForActivityResult throws IllegalStateException once STARTED.
        // BaseActivity defers setupViews() via binding.root.post, which fires after RESUMED, so we
        // cannot register from there.
        binaryFileMenuActions.forEach { it.registerLaunchers(this) }
        // S0028: resolve windowId before any use-case call (ViewModel reads same key from SavedStateHandle)
        windowId = savedInstanceState?.getString(EXTRA_WINDOW_ID)
            ?: intent.getStringExtra(EXTRA_WINDOW_ID)
            ?: ResumeStateRepository.WINDOW_ID_MAIN
        launcherManager = BrowseLauncherManager(this, object : BrowseLauncherCallbacks {
            override fun onPlayerActivityReturned() {
                // S0242 Phase 02: Player no longer ships a modified-paths payload. The Browse
                // Reconciler (Phase 03) reads the MutationJournal on onResume and applies the
                // structural diff against the cached file list. The note save-and-close flow
                // (PlayerViewerFactory.finishActivity) is the remaining producer of RESULT_OK.
                Timber.d("BrowseActivity: PlayerActivity returned RESULT_OK; Reconciler will run on next onResume")
            }
            override fun onEditResourceReturned() {
                Timber.i("Resource updated, reloading files")
                viewModel.reloadFiles(clearList = true)
            }
            override fun onDeletePermissionGranted() {
                viewModel.onDeletePermissionGranted()
            }
            override fun onPermissionDenied() {
                android.widget.Toast.makeText(this@BrowseActivity, R.string.permission_denied, android.widget.Toast.LENGTH_SHORT).show()
            }
            override fun clearPendingMoveOperation() {
                if (::initializer.isInitialized) {
                    initializer.fileOperationsManager.clearPendingMoveOperation()
                }
            }
            override fun onFolderPicked(uri: Uri?) {
                if (::initializer.isInitialized) {
                    initializer.folderPickerHandler.onFolderPicked(uri)
                }
            }
        })
        cameraCaptureManager = BrowseCameraCaptureManager(
            activity = this,
            settingsRepository = settingsRepository,
            resourceRepository = resourceRepository,
            coroutineScope = lifecycleScope,
            cameraCaptureSaver = cameraCaptureSaver,
            onFileSaved = { fileName -> onCapturedFileSaved(fileName) },
            onCapturedForEditing = { path, _ -> onCapturedFileSavedForEditing(path) },
            onVideoCaptured = { fileName -> onVideoCaptured(fileName) },
            onUploadFile = { tempFile, name, resource ->
                val sourceUri = Uri.fromFile(tempFile)
                val destUri = Uri.parse(resource.path.trimEnd('/') + '/' + Uri.encode(name))
                when (resource.type) {
                    ResourceType.FTP -> localToFtpStrategy.copy(sourceUri, destUri, true, null, null)
                    ResourceType.SMB -> localToSmbStrategy.copy(sourceUri, destUri, true, null, null)
                    ResourceType.SFTP -> localToSftpStrategy.copy(sourceUri, destUri, true, null, null)
                    ResourceType.CLOUD -> cloudOperationStrategyProvider().copyFile(
                        tempFile.absolutePath,
                        resource.path.trimEnd('/') + '/' + name,
                        true, null
                    ).isSuccess
                    else -> false
                }
            },
            networkStateMonitor = networkStateMonitor,
            saveFallbackNotifier = saveFallbackNotifier,
        )
        // Restore pending camera-capture context if the process was killed while the system
        // camera was open. Must run after the manager is constructed so launcher is registered.
        savedInstanceState?.let { state ->
            cameraCaptureManager.restoreState(state) { id ->
                viewModel.state.value.resource?.takeIf { it.id == id }
            }
        }

        micRecordingManager = BrowseMicRecordingManager(
            activity = this,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            onFileSaved = { fileName -> onCapturedFileSaved(fileName) },
            onRecordingStateChanged = { isRecording ->
                val tint = if (isRecording) {
                    ColorStateList.valueOf(getColor(R.color.recording_active_tint))
                } else null
                binding.btnMicRecord?.iconTint = tint
            },
            onUploadFile = { tempFile, name, resource ->
                val sourceUri = Uri.fromFile(tempFile)
                val destUri = Uri.parse(resource.path.trimEnd('/') + '/' + Uri.encode(name))
                when (resource.type) {
                    ResourceType.FTP -> localToFtpStrategy.copy(sourceUri, destUri, true, null, null)
                    ResourceType.SMB -> localToSmbStrategy.copy(sourceUri, destUri, true, null, null)
                    ResourceType.SFTP -> localToSftpStrategy.copy(sourceUri, destUri, true, null, null)
                    ResourceType.CLOUD -> cloudOperationStrategyProvider().copyFile(
                        tempFile.absolutePath,
                        resource.path.trimEnd('/') + '/' + name,
                        true, null
                    ).isSuccess
                    else -> false
                }
            },
            micRecordingSaver = micRecordingSaver,
            saveFallbackNotifier = saveFallbackNotifier,
        )

        // S0207 Phase 01: BROWSE_OPENED probe - fired at the end of onCreate so the measurement
        // captures the cost of inflating the browse UI plus initial dependency wiring.
        memoryProbe.record(MemoryCheckpoint.BROWSE_OPENED)
    }

    override fun getViewBinding(): ActivityBrowseBinding {
        return ActivityBrowseBinding.inflate(layoutInflater)
    }

    // S0230 Phase 02 - TV initial focus on the file list so the first D-pad press lands on a row.
    // S0289: when list is empty, fall back to btnBack so D-pad has a visible target.
    override fun getInitialFocusView(): android.view.View {
        val state = viewModel.state.value
        return if (state.mediaFiles.isNotEmpty()) binding.rvMediaFiles else binding.btnBack
    }

    /** S0289 Phase 08: route mouse wheel through the shared activity helper. */
    override fun getMouseScrollTargetView(): android.view.View? = binding.rvMediaFiles

    /**
     * S0289: rebuild the horizontal focus chain across only the currently-visible control buttons.
     * Skipped (View.GONE) buttons drop out of nextFocusLeft / nextFocusRight so the chain stays
     * contiguous after a state-driven visibility flip.
     */
    // S0374: callable from BrowseCommandOverflowManager to repair focus after an overflow flip.
    internal fun restitchBrowseControlChain() {
        val candidates = listOf(
            binding.btnBack,
            binding.btnSort,
            binding.btnFilter,
            binding.btnRefresh,
            binding.btnToggleView,
            binding.btnSelectAll,
            binding.btnDeselectAll,
            binding.btnCreateFolder,
            binding.btnCreateTextFile,
            binding.btnCreateDrawing,
            binding.btnResourceOps,
            binding.btnMicRecord,
            binding.btnPlayRandom,
            binding.btnPlay
        ).filter { it.visibility == android.view.View.VISIBLE }
        if (candidates.isEmpty()) return
        candidates.forEachIndexed { i, btn ->
            val prev = if (i > 0) candidates[i - 1].id else android.view.View.NO_ID
            val next = if (i < candidates.lastIndex) candidates[i + 1].id else android.view.View.NO_ID
            btn.nextFocusLeftId = prev
            btn.nextFocusRightId = next
        }
    }

    override fun setupViews() {
        com.sza.fastmediasorter.ui.browse.managers.BrowseEdgeToEdgeHelper.apply(binding)
        com.sza.fastmediasorter.utils.GlideCacheStats.reset()
        restitchBrowseControlChain()
        passwordManager = ResourcePasswordManager(context = this, layoutInflater = layoutInflater)
        Timber.d("showVideoThumbnails initialized: $showVideoThumbnails")

        initializer = BrowseManagerInitializer(
            activity = this,
            binding = binding,
            viewModel = viewModel,
            lifecycleScope = lifecycleScope,
            passwordManager = passwordManager,
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            settingsRepository = settingsRepository,
            smbClient = smbClient,
            sftpClient = sftpClient,
            ftpClient = ftpClient,
            googleDriveClient = googleDriveClient,
            dropboxClient = dropboxClient,
            oneDriveClient = oneDriveClient,
            credentialsRepository = credentialsRepository,
            unifiedFileOperationHandler = unifiedFileOperationHandler,
            audioMetadataLoader = audioMetadataLoader,
            unifiedFileCache = unifiedFileCache,
            resourceOpsMenuManager = resourceOpsMenuManager,
            browseFileOverflowMenuManager = browseFileOverflowMenuManager,
            launcherManager = launcherManager,
            showVideoThumbnailsGetter = { showVideoThumbnails },
            showPdfThumbnailsGetter = { showPdfThumbnails },
            updateShowVideoThumbnails = { showVideoThumbnails = it },
            updateShowPdfThumbnails = { showPdfThumbnails = it },
            isSkipAvailabilityCheck = intent.getBooleanExtra(EXTRA_SKIP_AVAILABILITY_CHECK, false),
            passthroughProvider = passthroughCaptureProvider.orElse(null),
            binaryFileMenuActions = binaryFileMenuActions,
            browseApkTileBadgeBinder = browseApkTileBadgeBinder,
            reviewRequestManager = reviewRequestManager,
            browseTransferCoordinator = browseTransferCoordinator,
            restrictedTreeTargetPolicy = restrictedTreeTargetPolicy,
            mediaCapabilities = mediaCapabilities,
            sendToMenuManager = sendToMenuManager,
            openInShareTargetHandler = openInShareTargetHandler,
        )

        initializer.initialize()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.canNavigateUp()) {
                    if (viewModel.state.value.resource?.isAudioOnly() != true) viewModel.inlineStop()
                    if (viewModel.navigateUp()) {
                        Timber.d("Navigated back to parent folder")
                        return
                    }
                }
                viewModel.clearResumeState()
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        binding.btnBack.setOnClickListener {
            UserActionLogger.logButtonClick("Back", "BrowseActivity")
            if (viewModel.canNavigateUp()) {
                if (viewModel.state.value.resource?.isAudioOnly() != true) viewModel.inlineStop()
                if (viewModel.navigateUp()) return@setOnClickListener
            }
            viewModel.clearResumeState()
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    override fun observeData() {
        if (mediaCapabilities.supportsMicRecording) {
            collectOnLifecycle(viewModel.settings.map { it.micRecordingEnabled }.distinctUntilChanged()) { showMic ->
                binding.btnMicRecord?.isVisible = showMic
                // S0374: mic eligibility is owned here (not by the state collector) - report + re-partition.
                if (::initializer.isInitialized) {
                    initializer.commandOverflowManager.setFeatureEligibility(R.id.btnMicRecord, showMic)
                    initializer.commandOverflowManager.recompute()
                }
            }
        }
        collectOnLifecycle(viewModel.state) { state ->
            val previousMediaFiles = viewModel.lastEmittedMediaFiles
            val sortChanged = state.sortMode != lastSubmittedSortMode
            val shouldSubmit = if (previousMediaFiles == null) true
            else if (sortChanged) true
            else state.mediaFiles !== previousMediaFiles

            if (shouldSubmit) {
                viewModel.markListAsSubmitted(state.mediaFiles)
                lastSubmittedSortMode = state.sortMode
                initializer.mediaFileAdapter.setSkipInitialThumbnailLoad(true)
                // isSorting stays true through DiffUtil: only clear on the submission that
                // carries the sorted mediaFiles (sortChanged = false at that point).
                val isSortingSubmit = state.isSorting && !sortChanged
                initializer.mediaFileAdapter.submitList(state.mediaFiles) {
                    if (isSortingSubmit) viewModel.clearSorting()
                    initializer.listSubmitManager.onListSubmitted(state)
                    // S0196 Phase 04: emit once after the first non-empty list is committed.
                    if (!firstListBoundLogged && state.mediaFiles.isNotEmpty()) {
                        firstListBoundLogged = true
                        Timber.d("BrowseActivity: primaryContentBound itemCount=${state.mediaFiles.size}")
                    }
                }
            }
            // Update drag handle visibility whenever sort mode changes
            if (sortChanged) {
                initializer.updateDragHandleVisibility(state.sortMode)
                initializer.updateSortButton(state.sortMode)
            }

            initializer.mediaFileAdapter.setSelectedPaths(state.selectedFiles)
            state.resource?.let { resource ->
                initializer.mediaFileAdapter.setAudioOnlyMode(resource.isAudioOnly())
                initializer.mediaFileAdapter.setCredentialsId(resource.credentialsId)
                initializer.mediaFileAdapter.setDisableThumbnails(resource.disableThumbnails)
                lifecycleScope.launch {
                    val hasDestinations = getDestinationsUseCase.getDestinationsExcluding(resource.id).isNotEmpty()
                    initializer.mediaFileAdapter.setResourcePermissions(
                        hasDestinations = hasDestinations,
                        isWritable = resource.isWritable && !resource.isReadOnly
                    )
                }
            }
            initializer.stateUiUpdater.onStateChanged(state)
        }
        initializer.observerManager.startAll()
        collectOnLifecycle(viewModel.events) { event ->
            initializer.eventHandler.handleEvent(event)
        }
    }

    override fun onLayoutConfigurationChanged(newConfig: Configuration) {
        initializer.buttonSetupHelper.updateToolbarButtonLabels(newConfig)
        // S0374: labels change button widths in landscape - re-partition the bar (cached eligibility).
        initializer.commandOverflowManager.recompute()
        lifecycleScope.launch {
            initializer.stateUiUpdater.currentDisplayMode?.let { mode ->
                initializer.stateUiUpdater.currentDisplayMode = null
                initializer.forceUpdateDisplayMode(mode)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::initializer.isInitialized && intent.getBooleanExtra(EXTRA_REATTACH_TRANSFER, false)) {
            initializer.fileOperationsManager.requestTransferDialogReattach()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (::initializer.isInitialized) {
            initializer.keyboardNavigationManager.handleKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
        } else super.onKeyDown(keyCode, event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == com.sza.fastmediasorter.core.util.PermissionHelper.REQUEST_CODE_LOCAL_NETWORK) {
            com.sza.fastmediasorter.core.util.PermissionHelper.onLocalNetworkPermissionResult(this, grantResults)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // D-pad / left stick focus moves rely on Android's default focus search
        // (item_media_file.xml and item_media_file_grid.xml are focusable).
        // A/B/X/Y/Start/L1/R1 are intercepted here via GamepadInputManager.
        val action = gamepadInputManager.handleKeyEvent(event, InputSurface.BROWSER)
        if (action is GamepadAction.BrowserAction && routeBrowserGamepadAction(action)) return true
        if (event.action == KeyEvent.ACTION_DOWN) {
            val commandId = keyBindingManager.resolveKeyAction(event.keyCode, event.metaState, InputSurface.BROWSER)
            if (commandId != null && routeBrowserCommandId(commandId)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun routeBrowserCommandId(commandId: String): Boolean {
        if (!::initializer.isInitialized) return false
        return initializer.keyboardNavigationManager.dispatchCommandId(commandId)
    }

    private fun routeBrowserGamepadAction(action: GamepadAction.BrowserAction): Boolean {
        when (action) {
            is GamepadAction.BrowserAction.Select -> {
                currentFocus?.performClick() ?: return false
            }
            is GamepadAction.BrowserAction.Back -> onBackPressedDispatcher.onBackPressed()
            is GamepadAction.BrowserAction.MultiSelect -> {
                // Long-click on a file row toggles multi-selection mode in MediaFileAdapter.
                currentFocus?.performLongClick() ?: return false
            }
            is GamepadAction.BrowserAction.ContextMenu -> {
                currentFocus?.performLongClick() ?: return false
            }
            is GamepadAction.BrowserAction.Search -> binding.btnFilter.performClick()
            is GamepadAction.BrowserAction.SwitchTab -> binding.btnToggleView.performClick()
        }
        return true
    }

    override fun onResumeWithViews() {
        initializer.cloudAuthManager.onResume()
        initializer.lifecycleHelper.checkAndRequestStoragePermission(
            resource = viewModel.state.value.resource,
            onReloadFiles = { viewModel.reloadFiles(clearList = true) }
        )
        if (isFirstResume) {
            isFirstResume = false
        } else {
            // S0242 Phase 03 - Reconciler runs unconditionally before any structural diff
            // path. Reads pending MutationJournal entries (Player writes) and folds them
            // into the cached/visible file list. Single adapter rebind only when the
            // visible set actually changed (no flicker on no-op).
            runReconciler()
            viewModel.checkAndReloadIfResourceChanged()
            initializer.lifecycleHelper.restoreScrollOnResume(viewModel.state.value)
        }
        viewModel.clearExpiredUndoOperation()
        if (viewModel.state.value.resource?.type != null) {
            initializer.mediaStoreObserver.start(viewModel.state.value.resource?.type)
        }
        memoryProfileCoordinator.enter(MemoryScenario.BROWSE_LIST)
    }

    /**
     * S0242 Phase 03 - apply pending journal mutations to the visible list before the
     * resource-settings check fires. Synchronous because the Reconciler does no IO; the
     * adapter rebind is the standard `state` collector path so DiffUtil still runs on the
     * background thread chosen by `AsyncListDiffer`.
     */
    private fun runReconciler() {
        val resource = viewModel.state.value.resource ?: return
        val current = viewModel.state.value.mediaFiles
        val result = browseReconcilerManager.reconcile(resource.id, resource.type, current)
        if (result.visibleChanged) {
            viewModel.replaceMediaFiles(result.updatedList)
        }
        // S0242 Phase 04 - fire-and-forget background probe of the first N visible files.
        // Findings flow back through the journal as `Mutation.Delete` and are picked up by
        // the next `reconcile()` call; no immediate UI update this resume.
        browseReconcilerManager.scheduleQuickVerify(resource.id, resource.type, result.updatedList)
    }

    // S0293: forward Activity multi-window / desktop-mode transitions so the per-row overflow
    // and resource-ops menus pick up the runtime capability flag on their next render.
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (::initializer.isInitialized) initializer.notifyMultiWindowModeChanged()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::initializer.isInitialized) initializer.notifyMultiWindowModeChanged()
    }

    override fun onPause() {
        super.onPause()
        if (::initializer.isInitialized) {
            initializer.mediaStoreObserver.stop()
            initializer.stateManager.saveScrollPosition()
            initializer.stateManager.saveLastViewedFile()
            initializer.listSubmitManager.shouldScrollToLastViewed = true
            initializer.blackScreenManager.hide()
        }
        viewModel.cancelBackgroundThumbnailLoading()
        memoryProfileCoordinator.enter(MemoryScenario.IDLE)
        // Leaving browse is the cheapest stable release point for thumbnail-heavy memory.
        runCatching {
            Glide.get(applicationContext).trimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        }.onFailure { error ->
            Timber.w(error, "BrowseActivity: best-effort Glide trim failed on pause")
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            // S0120: emit BRW-sort SUMMARY and schedule cooldown on genuine browse exit
            MemoryEnduranceTracker.endScenario()
            viewModel.inlineStop()
        }
        if (!isFinishing) {
            viewModel.cancelScan(forceCancel = true)
            viewModel.cancelBackgroundThumbnailLoading()
        }
    }

    override fun onDestroy() {
        com.sza.fastmediasorter.utils.GlideCacheStats.logStats()
        if (::initializer.isInitialized) {
            initializer.mediaStoreObserver.stop()
        }
        binding.rvMediaFiles.clearOnScrollListeners()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_WINDOW_ID, windowId)
        // Persist pending camera-capture context so it survives process death.
        if (::cameraCaptureManager.isInitialized) {
            cameraCaptureManager.saveState(outState)
        }
    }

    internal fun onCameraCaptureClicked() {
        val resource = viewModel.state.value.resource
        Timber.i(
            "S0022-CAM: BrowseActivity.onCameraCaptureClicked resource=%s",
            resource?.let { "{id=${it.id}, type=${it.type}, name=${it.name}}" } ?: "NULL",
        )
        if (resource == null) {
            Timber.w("BrowseActivity.onCameraCaptureClicked ABORT - viewModel resource is null")
            return
        }
        val passthrough = passthroughCaptureProvider.orElse(null)
        if (passthrough != null) {
            Timber.i("routing camera capture to passthrough provider")
            passthrough.launch(this, resource) { fileName -> onCapturedFileSaved(fileName) }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                cameraCaptureManager.launch(resource)
            } else {
                pendingCameraPermissionResourceId = resource.id
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * S0371: record-video command. Always uses the system video-capture intent (no passthrough OEM
     * provider, which is photo-only), gated by CAMERA permission. The save outcome is handled by
     * [onVideoCaptured].
     */
    internal fun onVideoCaptureClicked() {
        val resource = viewModel.state.value.resource ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            cameraCaptureManager.launchVideo(resource)
        } else {
            pendingVideoCaptureResourceId = resource.id
            videoCapturePermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    internal fun onMicRecordTouchDown() {
        val resource = viewModel.state.value.resource ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            micRecordingManager.startRecording(resource)
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    internal fun onMicRecordTouchUp() {
        if (::micRecordingManager.isInitialized) micRecordingManager.stopRecording()
    }

    internal fun onMicRecordSingleTap() {
        Toast.makeText(this, R.string.mic_recording_hold_hint, Toast.LENGTH_SHORT).show()
    }

    private fun onCapturedFileSaved(fileName: String) {
        viewModel.reloadFiles()
        viewModel.scrollToFileAfterRefresh(fileName)
    }

    /**
     * S0371: completion of a video recording. Always reload + scroll to the new file (never the
     * drawing editor). When the opt-in [AppSettings.videoCaptureOpenInPlayer] is set, open it in the
     * player at its resolved index; otherwise leave the user in Browse.
     */
    private fun onVideoCaptured(fileName: String) {
        viewModel.reloadFiles()
        viewModel.scrollToFileAfterRefresh(fileName)
        if (viewModel.settings.value.videoCaptureOpenInPlayer) {
            viewModel.openCapturedVideoAfterRefresh(fileName)
        }
    }

    private fun onCapturedFileSavedForEditing(path: String) {
        viewModel.reloadFiles()
        viewModel.openDrawingInEditor(path)
    }

    companion object {
        const val EXTRA_RESOURCE_ID = "resourceId"
        const val EXTRA_SKIP_AVAILABILITY_CHECK = "skipAvailabilityCheck"
        const val EXTRA_INITIAL_FOLDER_PATH = "initialFolderPath"
        const val EXTRA_INITIAL_FILE_PATH = "initialFilePath"
        const val EXTRA_REATTACH_TRANSFER = "reattachTransfer"
        const val EXTRA_RESUME_IS_PLAYING = "resumeIsPlaying"
        // S0028: multi-window - window identity and tear-off state
        const val EXTRA_WINDOW_ID = "extra_window_id"
        const val EXTRA_SCROLL_POSITION = "extra_scroll_position"

        fun createIntent(context: Context, resourceId: Long, skipAvailabilityCheck: Boolean = false, initialFolderPath: String? = null, initialFilePath: String? = null, isPlaying: Boolean? = null): Intent {
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
