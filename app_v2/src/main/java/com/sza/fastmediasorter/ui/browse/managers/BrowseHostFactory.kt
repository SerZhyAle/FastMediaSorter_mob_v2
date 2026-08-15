package com.sza.fastmediasorter.ui.browse.managers

import android.graphics.Bitmap
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.core.save.SaveFallbackNotifier
import com.sza.fastmediasorter.core.share.handlers.OpenInShareTargetHandler
import com.sza.fastmediasorter.core.storage.RestrictedTreeTargetPolicy
import com.sza.fastmediasorter.core.util.AudioMetadataLoader
import com.sza.fastmediasorter.data.capture.CameraCaptureSaver
import com.sza.fastmediasorter.data.capture.MicRecordingSaver
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import com.sza.fastmediasorter.ui.browse.helpers.BrowseFileOverflowMenuManager
import com.sza.fastmediasorter.ui.browse.helpers.ReviewRequestManager
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferCoordinator
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import java.io.File
import javax.inject.Inject

/**
 * S1329: owns the four domain dependencies the browse host used to field-inject and hands them to each
 * manager itself, so `BrowseActivity` declares no repository or use case of its own (CLAUDE.md Rule 3).
 *
 * Every method adapts to its manager exactly as that manager is written today. **No manager constructor
 * signature may change** (strategic ADR-1), which is why the host-supplied surface is mirrored one-to-one
 * rather than grouped: the views, scopes and callbacks below are things Hilt cannot supply.
 *
 * Unscoped on purpose - what it builds is Activity-scoped and must not outlive the host that asked.
 */
class BrowseHostFactory @Inject constructor(
    private val fileOperationUseCase: FileOperationUseCase,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val credentialsRepository: Lazy<NetworkCredentialsRepository>,
) {

    @Suppress("LongParameterList") // Mirrors the manager's host-supplied surface one-to-one.
    fun createManagerInitializer(
        activity: BrowseActivity,
        binding: ActivityBrowseBinding,
        viewModel: BrowseViewModel,
        lifecycleScope: LifecycleCoroutineScope,
        passwordManager: ResourcePasswordManager,
        smbClient: Lazy<SmbClient>,
        sftpClient: Lazy<SftpClient>,
        ftpClient: Lazy<FtpClient>,
        googleDriveClient: Lazy<GoogleDriveRestClient>,
        dropboxClient: Lazy<DropboxClient>,
        oneDriveClient: Lazy<OneDriveRestClient>,
        unifiedFileOperationHandler: UnifiedFileOperationHandler,
        audioMetadataLoader: AudioMetadataLoader,
        unifiedFileCache: UnifiedFileCache,
        resourceOpsMenuManager: ResourceOpsMenuManager,
        browseFileOverflowMenuManager: BrowseFileOverflowMenuManager,
        launcherManager: BrowseLauncherManager,
        showVideoThumbnailsGetter: () -> Boolean,
        showPdfThumbnailsGetter: () -> Boolean,
        updateShowVideoThumbnails: (Boolean) -> Unit,
        updateShowPdfThumbnails: (Boolean) -> Unit,
        isSkipAvailabilityCheck: Boolean,
        passthroughProvider: BrowsePassthroughCaptureProvider?,
        binaryFileMenuActions: Set<@JvmSuppressWildcards BrowseBinaryFileMenuAction>,
        browseApkTileBadgeBinder: BrowseApkTileBadgeBinder,
        reviewRequestManager: ReviewRequestManager,
        browseTransferCoordinator: BrowseFileTransferCoordinator,
        restrictedTreeTargetPolicy: RestrictedTreeTargetPolicy,
        mediaCapabilities: MediaCapabilities,
        sendToMenuManager: SendToMenuManager,
        openInShareTargetHandler: OpenInShareTargetHandler,
        faviconResolver: (String) -> Int?,
        faviconTileLoader: suspend (Int) -> Bitmap?,
    ): BrowseManagerInitializer = BrowseManagerInitializer(
        activity = activity,
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
        showVideoThumbnailsGetter = showVideoThumbnailsGetter,
        showPdfThumbnailsGetter = showPdfThumbnailsGetter,
        updateShowVideoThumbnails = updateShowVideoThumbnails,
        updateShowPdfThumbnails = updateShowPdfThumbnails,
        isSkipAvailabilityCheck = isSkipAvailabilityCheck,
        passthroughProvider = passthroughProvider,
        binaryFileMenuActions = binaryFileMenuActions,
        browseApkTileBadgeBinder = browseApkTileBadgeBinder,
        reviewRequestManager = reviewRequestManager,
        browseTransferCoordinator = browseTransferCoordinator,
        restrictedTreeTargetPolicy = restrictedTreeTargetPolicy,
        mediaCapabilities = mediaCapabilities,
        sendToMenuManager = sendToMenuManager,
        openInShareTargetHandler = openInShareTargetHandler,
        faviconResolver = faviconResolver,
        faviconTileLoader = faviconTileLoader,
    )

    /**
     * S0367/S0375: `resourceRepository` rides here rather than on the host because resolving the configured
     * capture-destination resource id is the only thing it was ever injected for.
     */
    @Suppress("LongParameterList") // Mirrors the manager's host-supplied surface one-to-one.
    fun createCameraCaptureManager(
        activity: FragmentActivity,
        coroutineScope: CoroutineScope,
        cameraCaptureSaver: CameraCaptureSaver,
        onFileSaved: (fileName: String) -> Unit,
        onCapturedForEditing: (path: String, resourceId: Long) -> Unit,
        onVideoCaptured: (fileName: String) -> Unit,
        onUploadFile: suspend (tempFile: File, name: String, resource: MediaResource) -> Boolean,
        networkStateMonitor: NetworkStateMonitor,
        saveFallbackNotifier: SaveFallbackNotifier,
    ): BrowseCameraCaptureManager = BrowseCameraCaptureManager(
        activity = activity,
        settingsRepository = settingsRepository,
        resourceRepository = resourceRepository,
        coroutineScope = coroutineScope,
        cameraCaptureSaver = cameraCaptureSaver,
        onFileSaved = onFileSaved,
        onCapturedForEditing = onCapturedForEditing,
        onVideoCaptured = onVideoCaptured,
        onUploadFile = onUploadFile,
        networkStateMonitor = networkStateMonitor,
        saveFallbackNotifier = saveFallbackNotifier,
    )

    @Suppress("LongParameterList") // Mirrors the manager's host-supplied surface one-to-one.
    fun createMicRecordingManager(
        activity: FragmentActivity,
        coroutineScope: CoroutineScope,
        appScope: CoroutineScope,
        onFileSaved: (fileName: String) -> Unit,
        onRecordingStateChanged: (isRecording: Boolean) -> Unit,
        onUploadFile: suspend (tempFile: File, name: String, resource: MediaResource) -> Boolean,
        micRecordingSaver: MicRecordingSaver,
        saveFallbackNotifier: SaveFallbackNotifier,
    ): BrowseMicRecordingManager = BrowseMicRecordingManager(
        activity = activity,
        settingsRepository = settingsRepository,
        coroutineScope = coroutineScope,
        appScope = appScope,
        onFileSaved = onFileSaved,
        onRecordingStateChanged = onRecordingStateChanged,
        onUploadFile = onUploadFile,
        micRecordingSaver = micRecordingSaver,
        saveFallbackNotifier = saveFallbackNotifier,
    )
}
