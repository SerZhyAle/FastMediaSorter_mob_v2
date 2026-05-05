package com.sza.fastmediasorter.ui.browse

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.input.GamepadInputManager
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.domain.input.InputSurface
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.transfer.strategies.LocalToFtpStrategy
import com.sza.fastmediasorter.data.transfer.strategies.LocalToSmbStrategy
import com.sza.fastmediasorter.data.transfer.strategies.LocalToSftpStrategy
import com.sza.fastmediasorter.data.transfer.strategy.CloudOperationStrategy
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.model.GamepadAction
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.ui.browse.managers.BrowseCameraCaptureManager
import com.sza.fastmediasorter.ui.browse.managers.BrowseManagerInitializer
import com.sza.fastmediasorter.ui.browse.managers.BrowseLauncherCallbacks
import com.sza.fastmediasorter.ui.browse.managers.BrowseLauncherManager
import com.sza.fastmediasorter.utils.UserActionLogger
import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BrowseActivity : BaseActivity<ActivityBrowseBinding>() {

    private val viewModel: BrowseViewModel by viewModels()
    private lateinit var passwordManager: ResourcePasswordManager
    private lateinit var initializer: BrowseManagerInitializer
    private lateinit var launcherManager: BrowseLauncherManager
    private lateinit var cameraCaptureManager: BrowseCameraCaptureManager

    @Inject lateinit var googleDriveClient: GoogleDriveRestClient
    @Inject lateinit var resourceOpsMenuManager: com.sza.fastmediasorter.ui.browse.managers.ResourceOpsMenuManager
    @Inject lateinit var dropboxClient: com.sza.fastmediasorter.data.cloud.DropboxClient
    @Inject lateinit var oneDriveClient: com.sza.fastmediasorter.data.cloud.OneDriveRestClient
    @Inject lateinit var fileOperationUseCase: FileOperationUseCase
    @Inject lateinit var getDestinationsUseCase: GetDestinationsUseCase
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var smbClient: SmbClient
    @Inject lateinit var sftpClient: SftpClient
    @Inject lateinit var ftpClient: FtpClient
    @Inject lateinit var credentialsRepository: com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
    @Inject lateinit var unifiedFileOperationHandler: com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
    @Inject lateinit var audioMetadataLoader: com.sza.fastmediasorter.core.util.AudioMetadataLoader
    @Inject lateinit var gamepadInputManager: GamepadInputManager
    @Inject lateinit var keyBindingManager: KeyBindingManager
    @Inject lateinit var localToFtpStrategy: LocalToFtpStrategy
    @Inject lateinit var localToSmbStrategy: LocalToSmbStrategy
    @Inject lateinit var localToSftpStrategy: LocalToSftpStrategy

    private var showVideoThumbnails = true
    private var showPdfThumbnails = false
    private var isFirstResume = true
    private var lastSubmittedSortMode: com.sza.fastmediasorter.domain.model.SortMode? = null
    // S0028: per-window resume state isolation
    private var windowId: String = ResumeStateRepository.WINDOW_ID_MAIN

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        // S0028: resolve windowId before any use-case call (ViewModel reads same key from SavedStateHandle)
        windowId = savedInstanceState?.getString(EXTRA_WINDOW_ID)
            ?: intent.getStringExtra(EXTRA_WINDOW_ID)
            ?: ResumeStateRepository.WINDOW_ID_MAIN
        launcherManager = BrowseLauncherManager(this, object : BrowseLauncherCallbacks {
            override fun handleGoogleSignInResult(data: Intent?) {
                if (::initializer.isInitialized) {
                    initializer.cloudAuthManager.handleGoogleSignInResult(data)
                }
            }
            override fun onPlayerActivityReturned(modifiedPaths: ArrayList<String>) {
                viewModel.removeFilesFromList(modifiedPaths)
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
        val cloudStrategy = CloudOperationStrategy(this, googleDriveClient, dropboxClient, oneDriveClient)
        cameraCaptureManager = BrowseCameraCaptureManager(
            activity = this,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            onFileSaved = { fileName -> onCapturedFileSaved(fileName) },
            onUploadFile = { tempFile, name, resource ->
                val sourceUri = Uri.fromFile(tempFile)
                val destUri = Uri.parse(resource.path.trimEnd('/') + '/' + Uri.encode(name))
                when (resource.type) {
                    ResourceType.FTP -> localToFtpStrategy.copy(sourceUri, destUri, true, null, null)
                    ResourceType.SMB -> localToSmbStrategy.copy(sourceUri, destUri, true, null, null)
                    ResourceType.SFTP -> localToSftpStrategy.copy(sourceUri, destUri, true, null, null)
                    ResourceType.CLOUD -> cloudStrategy.copyFile(
                        tempFile.absolutePath,
                        resource.path.trimEnd('/') + '/' + name,
                        true, null
                    ).isSuccess
                    else -> false
                }
            }
        )
        // Restore pending camera-capture context if the process was killed while the system
        // camera was open. Must run after the manager is constructed so launcher is registered.
        savedInstanceState?.let { state ->
            cameraCaptureManager.restoreState(state) { id ->
                viewModel.state.value.resource?.takeIf { it.id == id }
            }
        }
    }

    override fun getViewBinding(): ActivityBrowseBinding {
        return ActivityBrowseBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        com.sza.fastmediasorter.ui.browse.managers.BrowseEdgeToEdgeHelper.apply(binding)
        com.sza.fastmediasorter.utils.GlideCacheStats.reset()
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
            resourceOpsMenuManager = resourceOpsMenuManager,
            launcherManager = launcherManager,
            showVideoThumbnailsGetter = { showVideoThumbnails },
            showPdfThumbnailsGetter = { showPdfThumbnails },
            updateShowVideoThumbnails = { showVideoThumbnails = it },
            updateShowPdfThumbnails = { showPdfThumbnails = it },
            isSkipAvailabilityCheck = intent.getBooleanExtra(EXTRA_SKIP_AVAILABILITY_CHECK, false)
        )

        initializer.initialize()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.canNavigateUp() && viewModel.navigateUp()) {
                    Timber.d("Navigated back to parent folder")
                } else {
                    viewModel.clearResumeState()
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        binding.btnBack.setOnClickListener {
            UserActionLogger.logButtonClick("Back", "BrowseActivity")
            if (!viewModel.canNavigateUp() || !viewModel.navigateUp()) {
                viewModel.clearResumeState()
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }
    }

    override fun observeData() {
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
        lifecycleScope.launch {
            initializer.stateUiUpdater.currentDisplayMode?.let { mode ->
                initializer.stateUiUpdater.currentDisplayMode = null
                initializer.forceUpdateDisplayMode(mode)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (::initializer.isInitialized) {
            initializer.keyboardNavigationManager.handleKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
        } else super.onKeyDown(keyCode, event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // D-pad / left stick focus moves rely on Android's default focus search
        // (item_media_file.xml and item_media_file_grid.xml are focusable).
        // A/B/X/Y/Start/L1/R1 are intercepted here via GamepadInputManager.
        val action = gamepadInputManager.handleKeyEvent(event, GamepadInputManager.Surface.BROWSER)
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

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll != 0f) {
                val scrollFactor = binding.rvMediaFiles.context.resources.displayMetrics.density * 64f
                binding.rvMediaFiles.scrollBy(0, (-vScroll * scrollFactor).toInt())
                return true
            }
        }
        return super.onGenericMotionEvent(event)
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
            viewModel.checkAndReloadIfResourceChanged()
            initializer.lifecycleHelper.restoreScrollOnResume(viewModel.state.value)
        }
        viewModel.clearExpiredUndoOperation()
        if (viewModel.state.value.resource?.type != null) {
            initializer.mediaStoreObserver.start(viewModel.state.value.resource?.type)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::initializer.isInitialized) {
            initializer.mediaStoreObserver.stop()
            initializer.stateManager.saveScrollPosition()
            initializer.stateManager.saveLastViewedFile()
            initializer.listSubmitManager.shouldScrollToLastViewed = true
        }
        viewModel.cancelBackgroundThumbnailLoading()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
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
            Timber.w("S0022-CAM: BrowseActivity.onCameraCaptureClicked ABORT — viewModel resource is null")
            return
        }
        cameraCaptureManager.launch(resource)
    }

    private fun onCapturedFileSaved(fileName: String) {
        viewModel.reloadFiles()
        viewModel.scrollToFileAfterRefresh(fileName)
    }

    companion object {
        const val EXTRA_RESOURCE_ID = "resourceId"
        const val EXTRA_SKIP_AVAILABILITY_CHECK = "skipAvailabilityCheck"
        const val EXTRA_INITIAL_FOLDER_PATH = "initialFolderPath"
        const val EXTRA_INITIAL_FILE_PATH = "initialFilePath"
        const val EXTRA_RESUME_IS_PLAYING = "resumeIsPlaying"
        // S0028: multi-window — window identity and tear-off state
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
