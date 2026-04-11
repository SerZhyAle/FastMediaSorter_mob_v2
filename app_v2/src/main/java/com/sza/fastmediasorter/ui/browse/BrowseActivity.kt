package com.sza.fastmediasorter.ui.browse

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.ui.browse.managers.BrowseManagerInitializer
import com.sza.fastmediasorter.ui.browse.managers.BrowseLauncherCallbacks
import com.sza.fastmediasorter.ui.browse.managers.BrowseLauncherManager
import com.sza.fastmediasorter.utils.UserActionLogger
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

    private var showVideoThumbnails = true
    private var showPdfThumbnails = false
    private var isFirstResume = true
    private var lastSubmittedSortMode: com.sza.fastmediasorter.domain.model.SortMode? = null

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
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
    }

    override fun getViewBinding(): ActivityBrowseBinding {
        return ActivityBrowseBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        com.sza.fastmediasorter.ui.browse.managers.BrowseEdgeToEdgeHelper.apply(binding)
        com.sza.fastmediasorter.utils.GlideCacheStats.reset()
        passwordManager = ResourcePasswordManager(context = this, layoutInflater = layoutInflater)
        Timber.d("showVideoThumbnails initialized: \$showVideoThumbnails")

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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val previousMediaFiles = viewModel.lastEmittedMediaFiles
                    val sortChanged = state.sortMode != lastSubmittedSortMode
                    val shouldSubmit = if (previousMediaFiles == null) true
                    else if (sortChanged) true
                    else state.mediaFiles !== previousMediaFiles

                    if (shouldSubmit) {
                        viewModel.markListAsSubmitted(state.mediaFiles)
                        lastSubmittedSortMode = state.sortMode
                        initializer.mediaFileAdapter.setSkipInitialThumbnailLoad(true)
                        initializer.mediaFileAdapter.submitList(state.mediaFiles) {
                            initializer.listSubmitManager.onListSubmitted(state)
                        }
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
            }
        }
        initializer.observerManager.startAll()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    initializer.eventHandler.handleEvent(event)
                }
            }
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

    override fun onResume() {
        super.onResume()
        if (::initializer.isInitialized) {
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

    companion object {
        const val EXTRA_RESOURCE_ID = "resourceId"
        const val EXTRA_SKIP_AVAILABILITY_CHECK = "skipAvailabilityCheck"
        const val EXTRA_INITIAL_FOLDER_PATH = "initialFolderPath"
        const val EXTRA_INITIAL_FILE_PATH = "initialFilePath"
        const val EXTRA_RESUME_IS_PLAYING = "resumeIsPlaying"

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
