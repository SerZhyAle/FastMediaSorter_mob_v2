package com.sza.fastmediasorter.ui.browse.managers

import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.core.save.SaveFallbackNotifier
import com.sza.fastmediasorter.core.storage.RestrictedTreeTargetPolicy
import com.sza.fastmediasorter.core.util.AudioMetadataLoader
import com.sza.fastmediasorter.data.capture.CameraCaptureSaver
import com.sza.fastmediasorter.data.capture.MicRecordingSaver
import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
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

    /**
     * S1269: host-supplied inputs arrive pre-bundled (see `BrowseInitializerDependencies.kt`); the five
     * loose services below are folded into [BrowseDomainServices] together with the factory-injected
     * repositories and use cases, so `BrowseActivity` still never sees the domain layer (S1329).
     */
    @Suppress("LongParameterList") // Five bundles plus the five services folded into BrowseDomainServices.
    fun createManagerInitializer(
        hostUi: BrowseHostUi,
        remoteClients: BrowseRemoteClients,
        hostManagers: BrowseHostManagers,
        uiHooks: BrowseUiHooks,
        flavorHooks: BrowseFlavorHooks,
        unifiedFileOperationHandler: UnifiedFileOperationHandler,
        audioMetadataLoader: AudioMetadataLoader,
        unifiedFileCache: UnifiedFileCache,
        restrictedTreeTargetPolicy: RestrictedTreeTargetPolicy,
        mediaCapabilities: MediaCapabilities,
    ): BrowseManagerInitializer = BrowseManagerInitializer(
        hostUi = hostUi,
        remoteClients = remoteClients,
        domainServices = BrowseDomainServices(
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            settingsRepository = settingsRepository,
            credentialsRepository = credentialsRepository,
            unifiedFileOperationHandler = unifiedFileOperationHandler,
            audioMetadataLoader = audioMetadataLoader,
            unifiedFileCache = unifiedFileCache,
            restrictedTreeTargetPolicy = restrictedTreeTargetPolicy,
            mediaCapabilities = mediaCapabilities,
        ),
        hostManagers = hostManagers,
        uiHooks = uiHooks,
        flavorHooks = flavorHooks,
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
