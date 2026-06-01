package com.sza.fastmediasorter.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.model.DeviceStorageState
import com.sza.fastmediasorter.domain.usecase.ExportSettingsUseCase
import com.sza.fastmediasorter.domain.usecase.CleanupTrashFoldersUseCase
import com.sza.fastmediasorter.domain.usecase.GetDeviceStorageUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.ImportSettingsUseCase
import com.sza.fastmediasorter.domain.usecase.ResetSmbConnectionsUseCase
import com.sza.fastmediasorter.domain.usecase.SyncNetworkResourcesUseCase
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import com.sza.fastmediasorter.widget.GameLaunchWidgetProvider
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sza.fastmediasorter.R
import timber.log.Timber
import javax.inject.Inject

data class ManualNetworkSyncUiState(
    val inProgress: Boolean = false,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val completed: Boolean = false,
    val cancelled: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    val settingsRepository: SettingsRepository,
    val resourceRepository: ResourceRepository,
    val credentialsRepository: NetworkCredentialsRepository,
    val getDestinationsUseCase: GetDestinationsUseCase,
    val getResourcesUseCase: GetResourcesUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase,
    val exportSettingsUseCase: ExportSettingsUseCase,
    val importSettingsUseCase: ImportSettingsUseCase,
    val resetSmbConnectionsUseCase: ResetSmbConnectionsUseCase,
    private val syncNetworkResourcesUseCase: SyncNetworkResourcesUseCase,
    private val cleanupTrashFoldersUseCase: CleanupTrashFoldersUseCase,
    private val workManagerScheduler: WorkManagerScheduler,
    private val getDeviceStorageUseCase: GetDeviceStorageUseCase
) : ViewModel() {

    private val _manualNetworkSyncState = MutableStateFlow(ManualNetworkSyncUiState())
    val manualNetworkSyncState: StateFlow<ManualNetworkSyncUiState> = _manualNetworkSyncState.asStateFlow()
    private var manualSyncJob: Job? = null

    private val _deviceStorage = MutableStateFlow<DeviceStorageState>(DeviceStorageState.Error("Loading.."))
    val deviceStorage: StateFlow<DeviceStorageState> = _deviceStorage.asStateFlow()

    init {
        viewModelScope.launch {
            _deviceStorage.value = getDeviceStorageUseCase()
        }
    }

    fun refreshDeviceStorage() {
        viewModelScope.launch {
            _deviceStorage.value = getDeviceStorageUseCase()
        }
    }

    // Holds the last value passed to updateSettings() so that settings.value is
    // immediately current even before the async DataStore write completes.
    // Without this, rapid consecutive switch toggles read a stale .value and overwrite
    // each other's changes (e.g. Black Screen toggle turns off on next switch press).
    private val _settingsOverride = MutableStateFlow<AppSettings?>(null)

    val settings: StateFlow<AppSettings> = combine(
        settingsRepository.getSettings(),
        _settingsOverride
    ) { persisted, override -> override ?: persisted }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    val destinations: StateFlow<List<MediaResource>> = getDestinationsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val resources: StateFlow<List<MediaResource>> = getResourcesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSettings(settings: AppSettings) {
        val prev = this.settings.value
        _settingsOverride.value = settings  // Optimistic update: makes settings.value current immediately
        viewModelScope.launch {
            try {
                settingsRepository.updateSettings(settings)
                // Reschedule background sync when relevant settings change
                if (settings.enableBackgroundSync != prev.enableBackgroundSync ||
                    settings.backgroundSyncIntervalHours != prev.backgroundSyncIntervalHours) {
                    applyBackgroundSyncSchedule(
                        enabled = settings.enableBackgroundSync,
                        intervalHours = settings.backgroundSyncIntervalHours.toLong()
                    )
                }
                if (settings.enableScheduledOperations != prev.enableScheduledOperations) {
                    applyScheduledOperationsToggle(settings.enableScheduledOperations)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating settings")
            }
        }
    }

    private fun applyScheduledOperationsToggle(enabled: Boolean) {
        viewModelScope.launch {
            try {
                if (enabled) {
                    workManagerScheduler.rescheduleAll()
                    Timber.i("SettingsViewModel: Scheduled operations enabled - rescheduled all")
                } else {
                    workManagerScheduler.cancelAllScheduledOperations()
                    Timber.i("SettingsViewModel: Scheduled operations disabled - cancelled all workers")
                }
            } catch (e: Exception) {
                Timber.e(e, "SettingsViewModel: applyScheduledOperationsToggle failed")
            }
        }
    }

    private fun applyBackgroundSyncSchedule(enabled: Boolean, intervalHours: Long) {
        if (enabled) {
            workManagerScheduler.scheduleResourcesSync(intervalHours)
            Timber.i("SettingsViewModel: Background sync enabled, interval=${intervalHours}h")
        } else {
            workManagerScheduler.cancelResourcesSync()
            Timber.i("SettingsViewModel: Background sync disabled")
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                settingsRepository.resetToDefaults()
                // Settings reset
            } catch (e: Exception) {
                Timber.e(e, "Error resetting settings")
            }
        }
    }

    fun updateEmbeddedGameEnabled(enabled: Boolean) {
        updateSettings(settings.value.copy(embeddedGameEnabled = enabled))
        GameLaunchWidgetProvider.updateAll(context, enabled)
    }

    fun resetGeneralSection() {
        val defaults = AppSettings()
        val current = settings.value
        updateSettings(
            current.copy(
                language = defaults.language,
                showSmallControls = defaults.showSmallControls,
                defaultUser = defaults.defaultUser,
                defaultPassword = defaults.defaultPassword,
                networkParallelism = defaults.networkParallelism,
                cacheSizeMb = defaults.cacheSizeMb,
                isCacheSizeUserModified = defaults.isCacheSizeUserModified,
                enableBackgroundSync = defaults.enableBackgroundSync,
                backgroundSyncIntervalHours = defaults.backgroundSyncIntervalHours,
                allFiles = defaults.allFiles,
                showHiddenFiles = defaults.showHiddenFiles,
                showSubfoldersAsItems = defaults.showSubfoldersAsItems,
                defaultGridMode = defaults.defaultGridMode,
                hideGridActionButtons = defaults.hideGridActionButtons,
                fileOpsInOverflowMenu = defaults.fileOpsInOverflowMenu,
                defaultIconSize = defaults.defaultIconSize,
                enableSafeMode = defaults.enableSafeMode,
                confirmDelete = defaults.confirmDelete,
                confirmMove = defaults.confirmMove,
                enableFavorites = defaults.enableFavorites
            )
        )
    }

    fun resetMediaSection() {
        val defaults = AppSettings()
        val current = settings.value
        updateSettings(
            current.copy(
                supportImages = defaults.supportImages,
                imageSizeMin = defaults.imageSizeMin,
                imageSizeMax = defaults.imageSizeMax,
                loadFullSizeImages = defaults.loadFullSizeImages,
                cropImagesToFullscreen = defaults.cropImagesToFullscreen,
                supportGifs = defaults.supportGifs,
                supportVideos = defaults.supportVideos,
                videoSizeMin = defaults.videoSizeMin,
                videoSizeMax = defaults.videoSizeMax,
                supportAudio = defaults.supportAudio,
                audioSizeMin = defaults.audioSizeMin,
                audioSizeMax = defaults.audioSizeMax,
                searchAudioCoversOnline = defaults.searchAudioCoversOnline,
                searchAudioCoversOnlyOnWifi = defaults.searchAudioCoversOnlyOnWifi,
                enablePhotosDuringAudio = defaults.enablePhotosDuringAudio,
                audioBackgroundPhotosResourceId = defaults.audioBackgroundPhotosResourceId,
                supportText = defaults.supportText,
                supportPdf = defaults.supportPdf,
                supportEpub = defaults.supportEpub,
                supportOfficeDocuments = defaults.supportOfficeDocuments,
                showPdfThumbnails = defaults.showPdfThumbnails,
                textSizeMax = defaults.textSizeMax,
                showTextLineNumbers = defaults.showTextLineNumbers,
                enableTranslation = defaults.enableTranslation,
                translationSourceLanguage = defaults.translationSourceLanguage,
                translationTargetLanguage = defaults.translationTargetLanguage,
                translationLensStyle = defaults.translationLensStyle,
                enableGoogleLens = defaults.enableGoogleLens,
                enableOcr = defaults.enableOcr,
                ocrDefaultFontSize = defaults.ocrDefaultFontSize,
                ocrDefaultFontFamily = defaults.ocrDefaultFontFamily,
                showVideoThumbnails = defaults.showVideoThumbnails,
                videoSnapshotResourceId = defaults.videoSnapshotResourceId,
                videoSnapshotFormat = defaults.videoSnapshotFormat
            )
        )
    }

    fun resetPlaybackSection() {
        val defaults = AppSettings()
        val current = settings.value
        updateSettings(
            current.copy(
                preventSleep = defaults.preventSleep,
                defaultSortMode = defaults.defaultSortMode,
                slideshowInterval = defaults.slideshowInterval,
                slideshowMusicUri = defaults.slideshowMusicUri,
                enableSlideshowBackgroundMusic = defaults.enableSlideshowBackgroundMusic,
                slideshowMusicResourceId = defaults.slideshowMusicResourceId,
                playToEndInSlideshow = defaults.playToEndInSlideshow,
                allowRename = defaults.allowRename,
                allowDelete = defaults.allowDelete,
                useTrash = defaults.useTrash,
                fileOpsOverflowMenuHintShown = defaults.fileOpsOverflowMenuHintShown,
                hideSystemUiInFullscreen = defaults.hideSystemUiInFullscreen,
                defaultShowCommandPanel = defaults.defaultShowCommandPanel,
                showDetailedErrors = defaults.showDetailedErrors,
                showPlayerHintOnFirstRun = defaults.showPlayerHintOnFirstRun,
                alwaysShowTouchZonesOverlay = defaults.alwaysShowTouchZonesOverlay,
                isResourceGridMode = defaults.isResourceGridMode,
                showBlackScreenButton = defaults.showBlackScreenButton,
                defaultRememberFileList = defaults.defaultRememberFileList,
                enableCalculator = defaults.enableCalculator,
                embeddedGameEnabled = defaults.embeddedGameEnabled
            )
        )
        // Also reset per-type touch zone hints
        viewModelScope.launch {
            try {
                settingsRepository.setPlayerFirstRun(true)
                settingsRepository.resetAllTouchZoneHints()
            } catch (e: Exception) {
                Timber.e(e, "Error resetting touch zone hints during playback section reset")
            }
        }
    }

    fun resetDestinationsSection() {
        val defaults = AppSettings()
        val current = settings.value
        updateSettings(
            current.copy(
                enableCopying = defaults.enableCopying,
                goToNextAfterCopy = defaults.goToNextAfterCopy,
                overwriteOnCopy = defaults.overwriteOnCopy,
                enableMoving = defaults.enableMoving,
                overwriteOnMove = defaults.overwriteOnMove,
                enableUndo = defaults.enableUndo,
                maxRecipients = defaults.maxRecipients
            )
        )
    }
    
    fun resetPlayerFirstRun() {
        viewModelScope.launch {
            try {
                settingsRepository.setPlayerFirstRun(true)
                settingsRepository.resetAllTouchZoneHints()
            } catch (e: Exception) {
                Timber.e(e, "Error resetting player first-run flag")
            }
        }
    }

    fun clearAllTrash(context: Context) {
        viewModelScope.launch {
            android.widget.Toast.makeText(context, R.string.deleting_files, android.widget.Toast.LENGTH_SHORT).show()
            val cleanedCount = withContext(Dispatchers.IO) {
                resources.value
                    .filter { it.type == ResourceType.LOCAL }
                    .sumOf { resource ->
                    try {
                        // Manual clear must reuse the same contract + legacy migration path as the worker.
                        cleanupTrashFoldersUseCase.cleanup(java.io.File(resource.path), maxAgeMs = 0L)
                    } catch (e: Exception) {
                        Timber.w(e, "SettingsViewModel: Failed to clear trash for ${resource.path}")
                        0
                    }
                    }
            }
            android.widget.Toast.makeText(context, context.getString(R.string.trash_cleared, cleanedCount), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun moveDestination(resource: MediaResource, direction: Int) {
        viewModelScope.launch {
            try {
                val allDestinations = destinations.value
                val currentIndex = allDestinations.indexOfFirst { it.id == resource.id }
                if (currentIndex == -1) return@launch
                
                val targetIndex = currentIndex + direction
                if (targetIndex < 0 || targetIndex >= allDestinations.size) return@launch
                
                val targetResource = allDestinations[targetIndex]
                
                // Swap destination orders
                val currentOrder = resource.destinationOrder ?: return@launch
                val targetOrder = targetResource.destinationOrder ?: return@launch
                
                // Wait for both updates to complete before continuing
                val result1 = updateResourceUseCase(resource.copy(destinationOrder = targetOrder))
                val result2 = updateResourceUseCase(targetResource.copy(destinationOrder = currentOrder))
                
                if (result1.isSuccess && result2.isSuccess) {
                    Timber.d("Destination moved successfully: ${resource.name} order $currentOrder→$targetOrder, ${targetResource.name} order $targetOrder→$currentOrder")
                } else {
                    Timber.e("Error moving destination: result1=${result1.exceptionOrNull()}, result2=${result2.exceptionOrNull()}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error moving destination")
            }
        }
    }

    fun removeDestination(resource: MediaResource) {
        viewModelScope.launch {
            try {
                updateResourceUseCase(resource.copy(
                    isDestination = false,
                    destinationOrder = null
                ))
                Timber.d("Destination removed successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error removing destination")
            }
        }
    }

    suspend fun getWritableNonDestinationResources(): List<MediaResource> {
        return try {
            // Get fresh data from database, not from cached stateIn()
            val allResources = withContext(Dispatchers.IO) {
                getResourcesUseCase().first()
            }
            allResources.filter { it.isWritable && !it.isDestination }
        } catch (e: Exception) {
            Timber.e(e, "Error getting writable resources")
            emptyList()
        }
    }

    suspend fun getLastNetworkSyncTimestamp(): Long? {
        return try {
            withContext(Dispatchers.IO) {
                getResourcesUseCase().first()
                    .asSequence()
                    .filter {
                        it.type == ResourceType.LOCAL ||
                            it.type == ResourceType.SMB ||
                            it.type == ResourceType.SFTP ||
                            it.type == ResourceType.FTP
                    }
                    .mapNotNull { it.lastSyncDate }
                    .filter { it > 0L }
                    .maxOrNull()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting last sync timestamp")
            null
        }
    }

    fun addDestination(resource: MediaResource) {
        viewModelScope.launch {
            try {
                // Double-check resource is writable before adding to destinations
                if (!resource.isWritable) {
                    Timber.w("Cannot add non-writable resource as destination: ${resource.name}")
                    return@launch
                }
                
                val nextOrder = getDestinationsUseCase.getNextAvailableOrder()
                if (nextOrder == -1) {
                    Timber.w("Cannot add destination: all 10 slots are full")
                    return@launch
                }
                
                val color = DestinationColors.getColorForDestination(nextOrder)
                updateResourceUseCase(resource.copy(
                    isDestination = true,
                    destinationOrder = nextOrder,
                    destinationColor = color
                ))
                Timber.d("Destination added successfully with order $nextOrder and color $color")
            } catch (e: Exception) {
                Timber.e(e, "Error adding destination")
            }
        }
    }

    fun startManualNetworkSync() {
        if (manualSyncJob?.isActive == true) {
            return
        }

        _manualNetworkSyncState.value = ManualNetworkSyncUiState(inProgress = true)

        manualSyncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = syncNetworkResourcesUseCase.syncAll { processed, total, success ->
                    _manualNetworkSyncState.value = ManualNetworkSyncUiState(
                        inProgress = true,
                        processedCount = processed,
                        totalCount = total,
                        successCount = success
                    )
                }

                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        val syncedCount = result.getOrNull() ?: 0
                        _manualNetworkSyncState.value = ManualNetworkSyncUiState(
                            inProgress = false,
                            processedCount = _manualNetworkSyncState.value.processedCount,
                            totalCount = _manualNetworkSyncState.value.totalCount,
                            successCount = syncedCount,
                            completed = true
                        )
                    } else {
                        _manualNetworkSyncState.value = ManualNetworkSyncUiState(
                            inProgress = false,
                            errorMessage = context.getString(R.string.sync_failed)
                        )
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                withContext(Dispatchers.Main) {
                    _manualNetworkSyncState.value = ManualNetworkSyncUiState(
                        inProgress = false,
                        cancelled = true
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in manual network sync")
                withContext(Dispatchers.Main) {
                    _manualNetworkSyncState.value = ManualNetworkSyncUiState(
                        inProgress = false,
                        errorMessage = context.getString(R.string.sync_failed)
                    )
                }
            }
        }
    }

    fun cancelManualNetworkSync() {
        manualSyncJob?.cancel()
    }

    fun clearManualNetworkSyncTerminalState() {
        if (_manualNetworkSyncState.value.inProgress) {
            return
        }
        _manualNetworkSyncState.value = ManualNetworkSyncUiState()
    }

    fun updateDestinationColor(resource: MediaResource, color: Int) {
        viewModelScope.launch {
            try {
                updateResourceUseCase(resource.copy(destinationColor = color))
                Timber.d("Destination color updated successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error updating destination color")
            }
        }
    }

    // Methods for importing test credentials/resources
    fun addCredentials(credentials: com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity) {
        viewModelScope.launch {
            try {
                credentialsRepository.insert(credentials)
                Timber.d("Added credentials: ${credentials.username}@${credentials.server}")
            } catch (e: Exception) {
                Timber.e(e, "Error adding credentials")
            }
        }
    }

    fun getAllCredentials() = credentialsRepository.getAllCredentials()

    fun addResourceDirectly(resource: MediaResource) {
        viewModelScope.launch {
            try {
                resourceRepository.addResource(resource)
                Timber.d("Added resource: ${resource.name}")
            } catch (e: Exception) {
                Timber.e(e, "Error adding resource")
            }
        }
    }
    fun importSzaResources(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get XML parser
                val parser = context.resources.getXml(com.sza.fastmediasorter.R.xml.sza_resources)
                var eventType = parser.eventType
                
                // Get existing data to prevent duplicates
                val existingResources = resourceRepository.getAllResourcesSync()
                val existingCredentials = credentialsRepository.getAllCredentials().first()
                
                while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "resource") {
                        try {
                            // Extract attributes
                            val name = parser.getAttributeValue(null, "name") ?: "Unknown"
                            val path = parser.getAttributeValue(null, "path") ?: ""
                            val typeStr = parser.getAttributeValue(null, "type") ?: "LOCAL"
                            val username = parser.getAttributeValue(null, "username")
                            val password = parser.getAttributeValue(null, "password")
                            val pin = parser.getAttributeValue(null, "pin")
                            val addToDestinations = parser.getAttributeValue(null, "addToDestinations")?.toBoolean() ?: false
                            val allFiles = parser.getAttributeValue(null, "allFiles")?.toBoolean() ?: false
                            val supportedTypesStr = parser.getAttributeValue(null, "supportedMediaTypes")
                            val sortModeStr = parser.getAttributeValue(null, "sortMode")
                            val displayModeStr = parser.getAttributeValue(null, "displayMode")
                            val scanSubdirs = parser.getAttributeValue(null, "scanSubdirectories")?.toBoolean() ?: false
                            val disableThumbnails = parser.getAttributeValue(null, "disableThumbnails")?.toBoolean() ?: false
                            val showHidden = parser.getAttributeValue(null, "showHiddenFiles")?.toBoolean() ?: false
                            val rememberFileList = parser.getAttributeValue(null, "rememberTheFileList")?.toBoolean() ?: false
                            
                            val type = try {
                                com.sza.fastmediasorter.domain.model.ResourceType.valueOf(typeStr)
                            } catch (e: Exception) {
                                com.sza.fastmediasorter.domain.model.ResourceType.LOCAL
                            }
                            
                            // Handle Credentials - UPDATE existing or CREATE new
                            var credId: String? = null
                            if (!username.isNullOrEmpty() && (type == com.sza.fastmediasorter.domain.model.ResourceType.SMB || type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP || type == com.sza.fastmediasorter.domain.model.ResourceType.FTP)) {
                                // Parse server from URI
                                val server = if (type == com.sza.fastmediasorter.domain.model.ResourceType.SMB) {
                                    com.sza.fastmediasorter.utils.SmbPathUtils.extractServer(path) ?: ""
                                } else if (type == com.sza.fastmediasorter.domain.model.ResourceType.FTP) {
                                    com.sza.fastmediasorter.utils.FtpPathUtils.parseFtpPath(path)?.host ?: ""
                                } else if (type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) {
                                    com.sza.fastmediasorter.utils.SftpPathUtils.parseSftpPath(path)?.host ?: ""
                                } else {
                                    try {
                                        java.net.URI(path).host ?: ""
                                    } catch (e: Exception) { "" }
                                }

                                // Legacy XML may omit shareName on the credential node even when the SMB path still contains it.
                                val smbShareName = if (type == com.sza.fastmediasorter.domain.model.ResourceType.SMB) {
                                    com.sza.fastmediasorter.utils.SmbPathUtils.extractShare(path)?.takeIf { it.isNotBlank() }
                                } else {
                                    null
                                }
                                
                                // Check existing credentials
                                val existingCred = existingCredentials.find { 
                                    it.server == server &&
                                        it.username == username &&
                                        it.type == typeStr &&
                                        (
                                            type != com.sza.fastmediasorter.domain.model.ResourceType.SMB ||
                                                smbShareName == null ||
                                                it.shareName == smbShareName ||
                                                it.shareName.isNullOrBlank()
                                        )
                                }
                                
                                if (existingCred != null) {
                                    credId = existingCred.credentialId
                                    // UPDATE password from XML
                                    if (!password.isNullOrEmpty()) {
                                        val updatedCred = existingCred.copy(
                                            encryptedPassword = com.sza.fastmediasorter.data.local.db.CryptoHelper.encrypt(password) ?: "",
                                            shareName = smbShareName ?: existingCred.shareName
                                        )
                                        credentialsRepository.update(updatedCred)
                                        Timber.d("Updated password for credential $username@$server from XML")
                                    } else if (type == com.sza.fastmediasorter.domain.model.ResourceType.SMB && existingCred.shareName.isNullOrBlank() && !smbShareName.isNullOrBlank()) {
                                        credentialsRepository.update(existingCred.copy(shareName = smbShareName))
                                        Timber.d("Updated shareName for credential $username@$server from XML")
                                    }
                                } else {
                                    // Create new
                                    val newCredId = java.util.UUID.randomUUID().toString()
                                    val port = if (type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) 22 else if (type == com.sza.fastmediasorter.domain.model.ResourceType.FTP) 21 else 445
                                    val newCred = com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity.create(
                                        credentialId = newCredId,
                                        type = typeStr,
                                        server = server,
                                        port = port,
                                        username = username,
                                        plaintextPassword = password ?: "",
                                        shareName = smbShareName
                                    )
                                    credentialsRepository.insert(newCred)
                                    credId = newCredId
                                    Timber.d("Created credential for $username@$server from XML")
                                }
                            }
                            
                            // Parse other fields
                            val mediaTypes = if (supportedTypesStr != null) {
                                supportedTypesStr.split(",").mapNotNull { 
                                    try {
                                        com.sza.fastmediasorter.domain.model.MediaType.valueOf(it.trim())
                                    } catch (e: Exception) { null }
                                }.toSet()
                            } else {
                                setOf(com.sza.fastmediasorter.domain.model.MediaType.IMAGE, com.sza.fastmediasorter.domain.model.MediaType.VIDEO)
                            }
                            
                            val sortMode = try {
                                com.sza.fastmediasorter.domain.model.SortMode.valueOf(sortModeStr ?: "NAME_ASC")
                            } catch (e: Exception) { com.sza.fastmediasorter.domain.model.SortMode.NAME_ASC }
                            
                            val displayMode = try {
                                com.sza.fastmediasorter.domain.model.DisplayMode.valueOf(displayModeStr ?: "LIST")
                            } catch (e: Exception) { com.sza.fastmediasorter.domain.model.DisplayMode.LIST }
                            
                            // Check if resource exists - UPDATE or INSERT
                            val existingResource = existingResources.find { it.path == path }
                            
                            if (existingResource != null) {
                                // UPDATE existing resource - preserve local changes, apply XML credentials
                                val updatedResource = existingResource.copy(
                                    name = name,
                                    credentialsId = credId ?: existingResource.credentialsId,
                                    accessPin = pin ?: existingResource.accessPin
                                )
                                resourceRepository.updateResource(updatedResource)
                                Timber.d("Updated SZA resource from XML: $name (path: $path)")
                            } else {
                                // Destination logic for NEW resources only
                                var isDest = false
                                var destOrder: Int? = null
                                var destColor = 0
                                
                                if (addToDestinations) {
                                    val nextOrder = getDestinationsUseCase.getNextAvailableOrder()
                                    if (nextOrder != -1) {
                                        isDest = true
                                        destOrder = nextOrder
                                        destColor = DestinationColors.getColorForDestination(nextOrder)
                                    }
                                }
                                
                                val newResource = MediaResource(
                                    name = name,
                                    path = path,
                                    type = type,
                                    credentialsId = credId,
                                    supportedMediaTypes = mediaTypes,
                                    sortMode = sortMode,
                                    displayMode = displayMode,
                                    scanSubdirectories = scanSubdirs,
                                    disableThumbnails = disableThumbnails,
                                    allFiles = allFiles,
                                    showHiddenFiles = showHidden,
                                    rememberFileList = rememberFileList,
                                    accessPin = pin,
                                    isDestination = isDest,
                                    destinationOrder = destOrder,
                                    destinationColor = destColor,
                                    isWritable = true,
                                    isAvailable = true
                                )
                                
                                resourceRepository.addResource(newResource)
                                Timber.d("Added SZA resource from XML: $name (path: $path)")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing resource entry")
                        }
                    }
                    eventType = parser.next()
                }
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, R.string.sza_resources_imported, android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error importing SZA resources")
                 withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, R.string.error_importing_resources, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
