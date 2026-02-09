package com.sza.fastmediasorter.core.init

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handles all background initialization tasks for the application.
 * Extracted from Application class for better testability and separation of concerns.
 */
class AppStartupInitializer(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val playbackPositionRepository: PlaybackPositionRepository,
    private val thumbnailCacheRepository: ThumbnailCacheRepository,
    private val applicationScope: CoroutineScope
) {
    
    /**
     * Initialize all background tasks.
     * Should be called from Application.onCreate() after dependency injection.
     */
    fun initialize() {
        syncCacheSizeToSharedPreferences()
        fixCloudResourcesWritableFlag()
        cleanupPlaybackPositions()
        migrateThumbnailCache()
        cleanupOldThumbnails()
        initializeConnectionThrottleManager()
    }
    
    /**
     * Sync cache size setting to SharedPreferences for Glide initialization.
     * Also logs all settings in DEBUG builds.
     */
    private fun syncCacheSizeToSharedPreferences() {
        applicationScope.launch {
            try {
                val settings = settingsRepository.getSettings().first()
                context.getSharedPreferences("glide_config", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("cache_size_mb", settings.cacheSizeMb)
                    .apply()
                Timber.d("Synced cache size to SharedPreferences: ${settings.cacheSizeMb}MB")
                
                // Log all settings for debugging (DEBUG builds only)
                if (BuildConfig.DEBUG) {
                    logAllSettings(settings)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync cache size to SharedPreferences")
            }
        }
    }
    
    /**
     * Log all application settings for debugging in DEBUG builds.
     * Manual property logging (no reflection) for startup diagnostics.
     */
    private fun logAllSettings(settings: com.sza.fastmediasorter.domain.model.AppSettings) {
        Timber.i("========== APP SETTINGS AT STARTUP (DEBUG) ==========")
        
        try {
            with(settings) {
                // UI State
                Timber.i("%-30s = %s", "isResourceGridMode", isResourceGridMode)
                
                // General
                Timber.i("%-30s = %s", "language", language)
                Timber.i("%-30s = %s", "preventSleep", preventSleep)
                Timber.i("%-30s = %s", "showSmallControls", showSmallControls)
                Timber.i("%-30s = %s", "defaultUser", if (defaultUser.isEmpty()) "(empty)" else defaultUser)
                Timber.i("%-30s = %s", "defaultPassword", if (defaultPassword.isEmpty()) "(empty)" else "********")
                Timber.i("%-30s = %s", "networkParallelism", networkParallelism)
                Timber.i("%-30s = %s", "cacheSizeMb", cacheSizeMb)
                Timber.i("%-30s = %s", "isCacheSizeUserModified", isCacheSizeUserModified)
                
                // Network Sync
                Timber.i("%-30s = %s", "enableBackgroundSync", enableBackgroundSync)
                Timber.i("%-30s = %s", "backgroundSyncIntervalHours", backgroundSyncIntervalHours)
                
                // File Visibility
                Timber.i("%-30s = %s", "allFiles", allFiles)
                Timber.i("%-30s = %s", "showHiddenFiles", showHiddenFiles)
                
                // Media Support
                Timber.i("%-30s = %s", "supportImages", supportImages)
                Timber.i("%-30s = %s", "imageSizeMin", imageSizeMin)
                Timber.i("%-30s = %s", "imageSizeMax", imageSizeMax)
                Timber.i("%-30s = %s", "loadFullSizeImages", loadFullSizeImages)
                Timber.i("%-30s = %s", "supportGifs", supportGifs)
                Timber.i("%-30s = %s", "supportVideos", supportVideos)
                Timber.i("%-30s = %s", "videoSizeMin", videoSizeMin)
                Timber.i("%-30s = %s", "videoSizeMax", videoSizeMax)
                Timber.i("%-30s = %s", "supportAudio", supportAudio)
                Timber.i("%-30s = %s", "audioSizeMin", audioSizeMin)
                Timber.i("%-30s = %s", "audioSizeMax", audioSizeMax)
                Timber.i("%-30s = %s", "searchAudioCoversOnline", searchAudioCoversOnline)
                Timber.i("%-30s = %s", "searchAudioCoversOnlyOnWifi", searchAudioCoversOnlyOnWifi)
                
                // Documents
                Timber.i("%-30s = %s", "supportText", supportText)
                Timber.i("%-30s = %s", "supportPdf", supportPdf)
                Timber.i("%-30s = %s", "supportEpub", supportEpub)
                Timber.i("%-30s = %s", "showPdfThumbnails", showPdfThumbnails)
                Timber.i("%-30s = %s", "textSizeMax", textSizeMax)
                Timber.i("%-30s = %s", "showTextLineNumbers", showTextLineNumbers)
                
                // Translation/OCR
                Timber.i("%-30s = %s", "enableTranslation", enableTranslation)
                Timber.i("%-30s = %s", "translationSourceLanguage", translationSourceLanguage)
                Timber.i("%-30s = %s", "translationTargetLanguage", translationTargetLanguage)
                Timber.i("%-30s = %s", "translationLensStyle", translationLensStyle)
                Timber.i("%-30s = %s", "enableGoogleLens", enableGoogleLens)
                Timber.i("%-30s = %s", "enableOcr", enableOcr)
                Timber.i("%-30s = %s", "ocrDefaultFontSize", ocrDefaultFontSize)
                Timber.i("%-30s = %s", "ocrDefaultFontFamily", ocrDefaultFontFamily)
                
                // Playback/Sorting
                Timber.i("%-30s = %s", "defaultSortMode", defaultSortMode)
                Timber.i("%-30s = %s", "slideshowInterval", slideshowInterval)
                Timber.i("%-30s = %s", "playToEndInSlideshow", playToEndInSlideshow)
                Timber.i("%-30s = %s", "allowRename", allowRename)
                Timber.i("%-30s = %s", "allowDelete", allowDelete)
                Timber.i("%-30s = %s", "confirmDelete", confirmDelete)
                Timber.i("%-30s = %s", "confirmMove", confirmMove)
                Timber.i("%-30s = %s", "defaultGridMode", defaultGridMode)
                Timber.i("%-30s = %s", "hideGridActionButtons", hideGridActionButtons)
                Timber.i("%-30s = %s", "defaultIconSize", defaultIconSize)
                Timber.i("%-30s = %s", "defaultShowCommandPanel", defaultShowCommandPanel)
                Timber.i("%-30s = %s", "showDetailedErrors", showDetailedErrors)
                Timber.i("%-30s = %s", "showPlayerHintOnFirstRun", showPlayerHintOnFirstRun)
                Timber.i("%-30s = %s", "alwaysShowTouchZonesOverlay", alwaysShowTouchZonesOverlay)
                Timber.i("%-30s = %s", "showVideoThumbnails", showVideoThumbnails)
                
                // Safe Mode
                Timber.i("%-30s = %s", "enableSafeMode", enableSafeMode)
                
                // Destinations
                Timber.i("%-30s = %s", "enableCopying", enableCopying)
                Timber.i("%-30s = %s", "goToNextAfterCopy", goToNextAfterCopy)
                Timber.i("%-30s = %s", "overwriteOnCopy", overwriteOnCopy)
                Timber.i("%-30s = %s", "enableMoving", enableMoving)
                Timber.i("%-30s = %s", "overwriteOnMove", overwriteOnMove)
                Timber.i("%-30s = %s", "enableUndo", enableUndo)
                Timber.i("%-30s = %s", "maxRecipients", maxRecipients)
                Timber.i("%-30s = %s", "enableFavorites", enableFavorites)
                
                // Player UI
                Timber.i("%-30s = %s", "copyPanelCollapsed", copyPanelCollapsed)
                Timber.i("%-30s = %s", "movePanelCollapsed", movePanelCollapsed)
                
                // Misc
                Timber.i("%-30s = %s", "lastUsedResourceId", lastUsedResourceId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to log settings")
        }
        
        Timber.i("====================================================")
    }
    
    /**
     * Fix cloud resources: set isWritable = true for existing CLOUD resources.
     * Migration fix for older app versions.
     */
    private fun fixCloudResourcesWritableFlag() {
        applicationScope.launch {
            try {
                val resources = resourceRepository.getAllResources().first()
                val cloudResources = resources.filter { 
                    it.type == com.sza.fastmediasorter.domain.model.ResourceType.CLOUD && !it.isWritable 
                }
                if (cloudResources.isNotEmpty()) {
                    cloudResources.forEach { resource ->
                        resourceRepository.updateResource(resource.copy(isWritable = true))
                    }
                    Timber.d("Fixed isWritable flag for ${cloudResources.size} cloud resources")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fix cloud resources isWritable flag")
            }
        }
    }
    
    /**
     * Cleanup playback positions by count limit on app start.
     */
    private fun cleanupPlaybackPositions() {
        applicationScope.launch {
            try {
                playbackPositionRepository.cleanupOldPositions()
                Timber.d("Checked playback positions count limit")
            } catch (e: Exception) {
                Timber.e(e, "Failed to cleanup playback positions")
            }
        }
    }
    
    /**
     * Migrate thumbnails from old cache directory to persistent storage.
     * One-time migration from cacheDir to filesDir.
     */
    private fun migrateThumbnailCache() {
        applicationScope.launch {
            try {
                // Cast to implementation to access migration method
                if (thumbnailCacheRepository is com.sza.fastmediasorter.data.repository.ThumbnailCacheRepositoryImpl) {
                    thumbnailCacheRepository.migrateLegacyCache()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to migrate thumbnail cache")
            }
        }
    }
    
    /**
     * Cleanup old thumbnail cache (>30 days) on app start.
     */
    private fun cleanupOldThumbnails() {
        applicationScope.launch {
            try {
                val deletedCount = thumbnailCacheRepository.cleanupOldThumbnails(30)
                Timber.d("Cleaned up $deletedCount old thumbnail cache entries")
            } catch (e: Exception) {
                Timber.e(e, "Failed to cleanup old thumbnail cache")
            }
        }
    }
    
    /**
     * Initialize ConnectionThrottleManager with saved settings.
     * Observes settings changes and updates network limit dynamically.
     */
    private fun initializeConnectionThrottleManager() {
        applicationScope.launch {
            try {
                settingsRepository.getSettings()
                    .map { it.networkParallelism }
                    .distinctUntilChanged()
                    .collect { limit ->
                        ConnectionThrottleManager.setUserNetworkLimit(limit)
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize ConnectionThrottleManager settings")
            }
        }
    }
}
