package com.sza.fastmediasorter.core.init

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.compat.ChromeOsCompat
import com.sza.fastmediasorter.core.coordinator.RemoteSourceDisableCoordinator
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.input.DefaultsMapLoader
import com.sza.fastmediasorter.data.input.InputBindingRepository
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.StatisticsRepository
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import com.sza.fastmediasorter.domain.usecase.RenameVirtualResourcesUseCase
import com.sza.fastmediasorter.util.VirtualPathUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Handles all background initialization tasks for the application.
 * Extracted from Application class for better testability and separation of concerns.
 *
 * S0194: All seven repository / use-case dependencies are passed as [dagger.Lazy]
 * so the underlying singletons are not constructed until the corresponding async
 * task actually runs. Each task dereferences `.get()` inside its own coroutine
 * body - never at construction time.
 */
@Singleton
class AppStartupInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: dagger.Lazy<SettingsRepository>,
    private val resourceRepository: dagger.Lazy<ResourceRepository>,
    private val playbackPositionRepository: dagger.Lazy<PlaybackPositionRepository>,
    private val thumbnailCacheRepository: dagger.Lazy<ThumbnailCacheRepository>,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val renameVirtualResourcesUseCase: dagger.Lazy<RenameVirtualResourcesUseCase>,
    private val inputBindingRepository: dagger.Lazy<InputBindingRepository>,
    private val defaultsMapLoader: dagger.Lazy<DefaultsMapLoader>,
    private val remoteSourceDisableCoordinator: RemoteSourceDisableCoordinator,
    private val statisticsRepository: dagger.Lazy<StatisticsRepository>,
    // S0473: foreground-session tracker. Registered eagerly on the main thread (ProcessLifecycleOwner
    // requires the main looper) so the first foreground session is captured.
    private val statsSessionTracker: dagger.Lazy<com.sza.fastmediasorter.data.stats.StatsSessionTracker>,
) {

    private val connectionThrottleManagerInitialized = AtomicBoolean(false)
    // S0473: guards the always-on baseline launch record so a re-enqueued deferred worker records
    // a single launch per process, never one per worker pass.
    private val launchRecorded = AtomicBoolean(false)
    
    /**
     * Initialize all background tasks.
     * Should be called from Application.onCreate() after dependency injection.
     */
    fun initialize() {
        // Glide still reads this SharedPreferences mirror during early disk-cache sizing,
        // so it stays on the eager path while the heavier migrations move behind Phase 06.
        syncCacheSizeToSharedPreferences()
        // S0391: observe remote-source toggles to cancel in-flight work when a source is switched off.
        remoteSourceDisableCoordinator.start()
        // S0473: register the foreground-session observer on the main thread. Cheap (one observer
        // registration); the heavier stats work stays off the gate-disabled path inside the sink.
        statsSessionTracker.get().initialize()
    }

    suspend fun runDeferredStartupTasks() {
        runDeferredTask("record-launch-baseline") { recordLaunchBaseline() }
        if (BuildConfig.DEBUG) {
            runDeferredTask("permissions-status") { logPermissionsStatus() }
        }
        runDeferredTask("fix-cloud-writable-flag") { fixCloudResourcesWritableFlag() }
        runDeferredTask("fix-local-writable-flag") { fixLocalResourcesWritableFlag() }
        runDeferredTask("fix-virtual-writable-flag") { fixVirtualAggregateWritableFlag() }
        runDeferredTask("rename-virtual-resources") { renameVirtualResourceNames() }
        runDeferredTask("cleanup-playback-positions") { cleanupPlaybackPositions() }
        runDeferredTask("migrate-thumbnail-cache") { migrateThumbnailCache() }
        runDeferredTask("cleanup-old-thumbnails") { cleanupOldThumbnails() }
        if (ChromeOsCompat.isChromeOs(context)) {
            runDeferredTask("apply-chromeos-defaults") { applyDefaultsChromeOsIfEmpty() }
        }
        runDeferredTask("connection-throttle-bootstrap") { ensureConnectionThrottleManagerInitialized() }
    }

    /**
     * S0473: always-on baseline launch record. Independent of the opt-in statistics toggle
     * (strategic §3 refinement 3, §5.2, §11.3) - it only accrues launch count, first-launch and
     * install version/flavor. Guarded so it runs at most once per process even if the deferred
     * worker is re-enqueued after process recreation.
     */
    private suspend fun recordLaunchBaseline() {
        if (!launchRecorded.compareAndSet(false, true)) return
        statisticsRepository.get().recordLaunch(BuildConfig.VERSION_NAME, BuildConfig.FLAVOR)
    }

    private suspend fun runDeferredTask(label: String, block: suspend () -> Unit) {
        runCatching { block() }
            .onSuccess { Timber.i("AppStartupInitializer: deferred task complete - %s", label) }
            .onFailure { Timber.e(it, "AppStartupInitializer: deferred task failed - %s", label) }
    }

    private suspend fun applyDefaultsChromeOsIfEmpty() {
        try {
            val bindings = inputBindingRepository.get()
            if (!bindings.hasOverrides()) {
                bindings.insertAllAsOverrides(defaultsMapLoader.get().loadChromeOsDefaults())
                Timber.i("AppStartupInitializer: Chrome OS keybinding defaults applied")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply Chrome OS keybinding defaults")
        }
    }
    
    /**
     * Sync cache size setting to SharedPreferences for Glide initialization.
     */
    private fun syncCacheSizeToSharedPreferences() {
        applicationScope.launch {
            try {
                val settings = settingsRepository.get().getSettings().first()
                context.getSharedPreferences("glide_config", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("cache_size_mb", settings.cacheSizeMb)
                    .apply()
                Timber.d("Synced cache size to SharedPreferences: ${settings.cacheSizeMb}MB")
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync cache size to SharedPreferences")
            }
        }
    }
    
    /**
     * Log status of all runtime permissions at startup (DEBUG builds only).
     */
    private fun logPermissionsStatus() {
        Timber.i("========== PERMISSIONS STATUS AT STARTUP (DEBUG) ==========")
        try {
            fun granted(perm: String) =
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

            // Media / storage permissions - depends on API level
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
                Timber.i("%-40s = %s", "READ_MEDIA_IMAGES", granted(Manifest.permission.READ_MEDIA_IMAGES))
                Timber.i("%-40s = %s", "READ_MEDIA_VIDEO", granted(Manifest.permission.READ_MEDIA_VIDEO))
                Timber.i("%-40s = %s", "READ_MEDIA_AUDIO", granted(Manifest.permission.READ_MEDIA_AUDIO))
            } else {
                Timber.i("%-40s = %s", "READ_EXTERNAL_STORAGE", granted(Manifest.permission.READ_EXTERNAL_STORAGE))
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // API <= 28
                    Timber.i("%-40s = %s", "WRITE_EXTERNAL_STORAGE", granted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            }

            // All Files Access - MANAGE_EXTERNAL_STORAGE (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Timber.i("%-40s = %s", "MANAGE_EXTERNAL_STORAGE (AllFiles)", Environment.isExternalStorageManager())
            }

            // Manage Media (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val canManage = try {
                    android.provider.MediaStore.canManageMedia(context)
                } catch (e: Exception) {
                    Timber.w(e, "Cannot check MANAGE_MEDIA")
                    false
                }
                Timber.i("%-40s = %s", "MANAGE_MEDIA", canManage)
            }

            // Normal permissions
            Timber.i("%-40s = %s", "INTERNET", granted(Manifest.permission.INTERNET))

        } catch (e: Exception) {
            Timber.e(e, "Failed to log permissions status")
        }
        Timber.i("===========================================================")
    }

    /**
     * Fix cloud resources: set isWritable = true for existing CLOUD resources.
     * Migration fix for older app versions.
     */
    private suspend fun fixCloudResourcesWritableFlag() {
        try {
            val repo = resourceRepository.get()
            val resources = repo.getAllResources().first()
            val cloudResources = resources.filter {
                it.type == com.sza.fastmediasorter.domain.model.ResourceType.CLOUD && !it.isWritable
            }
            if (cloudResources.isNotEmpty()) {
                cloudResources.forEach { resource ->
                    repo.updateResource(resource.copy(isWritable = true))
                }
                Timber.d("Fixed isWritable flag for ${cloudResources.size} cloud resources")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fix cloud resources isWritable flag")
        }
    }
    
    /**
     * Fix LOCAL resources with isWritable = false due to the edit-reset bug.
     * When a resource was edited, buildPersistenceModel() did not preserve isWritable,
     * overwriting it with the default (false). This restores the correct flag for LOCAL resources
     * that are not explicitly marked as read-only.
     */
    private suspend fun fixLocalResourcesWritableFlag() {
        try {
            val repo = resourceRepository.get()
            val resources = repo.getAllResources().first()
            val broken = resources.filter {
                it.type == com.sza.fastmediasorter.domain.model.ResourceType.LOCAL
                    && !it.isWritable
                    && !it.isReadOnly
            }
            if (broken.isNotEmpty()) {
                broken.forEach { resource ->
                    repo.updateResource(resource.copy(isWritable = true))
                }
                Timber.d("Fixed isWritable flag for ${broken.size} LOCAL resources")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fix local resources isWritable flag")
        }
    }

    /**
     * Fix aggregate virtual resources: set isWritable = true for existing records
     * that were provisioned with the old default (false).
     * Migration fix for S0130.
     */
    private suspend fun fixVirtualAggregateWritableFlag() {
        try {
            val repo = resourceRepository.get()
            val resources = repo.getAllResources().first()
            val broken = resources.filter {
                VirtualPathUtils.isAggregateVirtualPath(it.path) && !it.isWritable
            }
            if (broken.isNotEmpty()) {
                broken.forEach { resource ->
                    repo.updateResource(resource.copy(isWritable = true))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fix aggregate virtual resources isWritable flag")
        }
    }

    private suspend fun renameVirtualResourceNames() {
        renameVirtualResourcesUseCase.get().invoke()
    }

    /**
     * Cleanup playback positions by count limit on app start.
     */
    private suspend fun cleanupPlaybackPositions() {
        try {
            playbackPositionRepository.get().cleanupOldPositions()
            Timber.d("Checked playback positions count limit")
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup playback positions")
        }
    }

    /**
     * Migrate thumbnails from old cache directory to persistent storage.
     * One-time migration from cacheDir to filesDir.
     */
    private suspend fun migrateThumbnailCache() {
        try {
            val repo = thumbnailCacheRepository.get()
            // Cast to implementation to access migration method
            if (repo is com.sza.fastmediasorter.data.repository.ThumbnailCacheRepositoryImpl) {
                repo.migrateLegacyCache()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to migrate thumbnail cache")
        }
    }
    
    /**
     * Cleanup old thumbnail cache (>30 days) on app start, then enforce the user-configured
     * size limit via LRU eviction.
     */
    private suspend fun cleanupOldThumbnails() {
        try {
            val thumbnails = thumbnailCacheRepository.get()
            val deletedByAge = thumbnails.cleanupOldThumbnails(30)
            Timber.d("ThumbnailCache: age cleanup removed $deletedByAge entries")

            // Enforce size limit: use the same cacheSizeMb setting as Glide
            val settings = settingsRepository.get().getSettings().first()
            val limitBytes = settings.cacheSizeMb.toLong() * 1024L * 1024L
            val deletedBySize = thumbnails.enforceSizeLimit(limitBytes)
            if (deletedBySize > 0) {
                Timber.i("ThumbnailCache: evicted $deletedBySize entries to respect ${settings.cacheSizeMb}MB limit")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup thumbnail cache")
        }
    }

    /**
     * Initialize ConnectionThrottleManager with saved settings.
     * Observes settings changes and updates network limit dynamically.
     */
    internal fun tryStartConnectionThrottleManagerInitialization(): Boolean {
        return connectionThrottleManagerInitialized.compareAndSet(false, true)
    }

    private fun ensureConnectionThrottleManagerInitialized() {
        // WHY: the deferred startup worker can be re-enqueued after process recreation, but the
        // settings collector must remain single-shot or ConnectionThrottleManager would receive
        // duplicate updates from parallel collectors.
        if (!tryStartConnectionThrottleManagerInitialization()) return

        applicationScope.launch {
            try {
                settingsRepository.get().getSettings()
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
