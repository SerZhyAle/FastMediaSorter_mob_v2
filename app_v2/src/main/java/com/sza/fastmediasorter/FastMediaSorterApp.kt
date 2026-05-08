package com.sza.fastmediasorter

import android.app.Application
import android.content.Context
import android.content.ComponentCallbacks2
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.bumptech.glide.Glide
import com.google.android.material.color.DynamicColors
import com.sza.fastmediasorter.core.init.AppStartupInitializer
import com.sza.fastmediasorter.core.logging.LoggingHelper
import com.sza.fastmediasorter.core.debug.DebugToolsBridge
import com.sza.fastmediasorter.core.util.CacheStatusHelper
import com.sza.fastmediasorter.core.util.GmsAvailabilityChecker
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.SmbConnectionManager
import com.sza.fastmediasorter.data.network.SmbBackgroundLifecycleManager
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class FastMediaSorterApp : Application(), Configuration.Provider {

    companion object {
        // Static context for Glide ModelLoader factory (needed for Hilt EntryPoint access)
        lateinit var appContext: Context
            private set

        private const val ENABLE_DEBUG_STRICT_MODE = false
    }
    
    @Inject
    lateinit var workManagerScheduler: WorkManagerScheduler
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var playbackPositionRepository: com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
    
    @Inject
    lateinit var thumbnailCacheRepository: com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
    
    @Inject
    lateinit var resourceRepository: com.sza.fastmediasorter.domain.repository.ResourceRepository
    
    @Inject
    lateinit var unifiedCache: com.sza.fastmediasorter.core.cache.UnifiedFileCache

    @Inject
    lateinit var cachedFileListRepository: com.sza.fastmediasorter.data.repository.CachedFileListRepository
    
    @Inject
    lateinit var networkStateMonitor: com.sza.fastmediasorter.core.network.NetworkStateMonitor
    
    @Inject
    lateinit var smbConnectionManager: SmbConnectionManager

    @Inject
    lateinit var smbBackgroundLifecycleManager: SmbBackgroundLifecycleManager

    @Inject
    lateinit var networkLifecycleObserver: com.sza.fastmediasorter.core.lifecycle.NetworkLifecycleObserver

    @Inject
    lateinit var tempFileManager: com.sza.fastmediasorter.domain.transfer.TempFileManager

    @Inject
    lateinit var renameVirtualResourcesUseCase: com.sza.fastmediasorter.domain.usecase.RenameVirtualResourcesUseCase

    @Inject
    lateinit var inputBindingRepository: com.sza.fastmediasorter.data.input.InputBindingRepository

    @Inject
    lateinit var defaultsMapLoader: com.sza.fastmediasorter.data.input.DefaultsMapLoader

    // Application-scoped coroutine for background initialization
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Track if app is in foreground
    @Volatile
    private var isInForeground = true

    override fun onCreate() {
        super.onCreate()

        // Material You: apply wallpaper-based dynamic colors on Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this)

        if (BuildConfig.DEBUG) {
            DebugToolsBridge.install(this)
        }

        setupDebugStrictMode()
        GmsAvailabilityChecker.check(this)

        // Initialize static context for Glide ModelLoader
        appContext = applicationContext
        
        // Monitor app lifecycle to optimize background behavior
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // All activities stopped - app fully in background
                isInForeground = false
                Timber.d("App moved to BACKGROUND - optimizing resources")
                onAppBackgrounded()
            }
            
            override fun onStart(owner: LifecycleOwner) {
                // At least one activity visible - app in foreground
                isInForeground = true
                Timber.d("App moved to FOREGROUND")
                onAppForegrounded()
            }
        })
        // S0061 Phase 04: close UI SMB connections on background (preserves BACKGROUND_WORKER).
        ProcessLifecycleOwner.get().lifecycle.addObserver(smbBackgroundLifecycleManager)

        // S0067 Phase 06: protocol-neutral lifecycle gates (SMB / SFTP / FTP / Cloud).
        networkLifecycleObserver.attach()
        
        // PDF Support: Using built-in Android PdfRenderer (API 21+)
        // No external PDF library needed - Android's PdfRenderer handles PDF rendering natively
        // PDFBox was removed to avoid BouncyCastle conflicts and reduce APK size
        
        // Apply saved locale - SharedPreferences read already wrapped in StrictModeHelper
        LocaleHelper.applyLocale(this)
        // Note: logging initialized early in attachBaseContext to capture startup crashes
        
        // Clear failed video thumbnail cache on app start (off main thread)
        applicationScope.launch(Dispatchers.IO) {
            NetworkFileDataFetcher.clearFailedVideoCache()
        }
        
        // Clear translation cache on app start
        com.sza.fastmediasorter.core.cache.TranslationCacheManager.clearAll()
        
        // Clean up orphaned temp files (ML-007) — handles crashes that prevented cleanup
        applicationScope.launch(Dispatchers.IO) {
            tempFileManager.cleanupOldTempFiles(24 * 60 * 60 * 1000L) // 24 hours max age
            Timber.d("App startup: cleaned up old orphaned temp files")
        }
        
        // Start network state monitoring for automatic connection recovery
        networkStateMonitor.start()
        
        // Setup SMB auto-reset callback to show user feedback
        setupSmbAutoReset()
        
        // Log Glide disk cache status at startup (off main thread)
        applicationScope.launch(Dispatchers.IO) {
            CacheStatusHelper.logGlideDiskCacheStatus(this@FastMediaSorterApp)
        }
        
        // Initialise Cast SDK early so device discovery begins before PlayerActivity opens.
        // Skipped on flavors without Cast support (VR — Horizon OS has no Google Play Services Cast module).
        if (BuildConfig.SUPPORT_CAST) {
            try {
                com.google.android.gms.cast.framework.CastContext.getSharedInstance(this)
                Timber.d("FastMediaSorterApp: Cast SDK initialized")
            } catch (e: Exception) {
                Timber.w("FastMediaSorterApp: Cast SDK not available — ${e.message}")
            }
        }

        Timber.d("S0112: TV launcher support active — LEANBACK_LAUNCHER registered")
        Timber.d("FastMediaSorter v2 initialized with locale: ${LocaleHelper.getLanguage(this)}")
        
        // Log detailed app startup information
        logAppStartupInfo()

        // Warn if the previous session ended with a crash (user should export logs)
        if (LoggingHelper.hasPreviousCrash()) {
            Timber.w("=== PREVIOUS SESSION ENDED WITH A CRASH — use 'Export debug logs' to collect reports ===")
        }

        // Initialize all background tasks
        val startupInitializer = AppStartupInitializer(
            context = applicationContext,
            settingsRepository = settingsRepository,
            resourceRepository = resourceRepository,
            playbackPositionRepository = playbackPositionRepository,
            thumbnailCacheRepository = thumbnailCacheRepository,
            applicationScope = applicationScope,
            renameVirtualResourcesUseCase = renameVirtualResourcesUseCase,
            inputBindingRepository = inputBindingRepository,
            defaultsMapLoader = defaultsMapLoader,
        )
        startupInitializer.initialize()
        
        // Trash cleanup now handled synchronously in BrowseViewModel (on resource open/close)
        // WorkManager periodic cleanup disabled - unnecessary with sync cleanup
        // Left for potential future background tasks (e.g., network resource sync)
        
        // Defer WorkManager scheduling to background with delay to avoid blocking app startup
        // WorkManager initialization is expensive (~100-200ms), defer until after UI is rendered
        applicationScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(2000) // Wait for UI to render first
                val settings = settingsRepository.getSettings().first()
                logSettingsInfo(settings)
                // Sync the synchronous SP mirror so the player picks the right controls layout
                // on first inflate after upgrade from a build that didn't write the SP file yet.
                com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs.setCompact(
                    this@FastMediaSorterApp,
                    settings.useCompactElements
                )
                if (settings.enableBackgroundSync) {
                    workManagerScheduler.scheduleResourcesSync(
                        settings.backgroundSyncIntervalHours.toLong()
                    )
                    Timber.i("FastMediaSorterApp: Background resource sync scheduled (${settings.backgroundSyncIntervalHours}h)")
                } else {
                    // Ensure legacy scheduled work is cleaned up on first launch after update
                    workManagerScheduler.cancelResourcesSync()
                    Timber.d("FastMediaSorterApp: Background sync is disabled, skipping scheduling")
                }
                // Orphan cleanup runs regardless of sync setting — lightweight maintenance task
                workManagerScheduler.scheduleOrphanCleanup()
                // Retry any OAuth token revocations that failed during sign-out (B5-T3)
                workManagerScheduler.schedulePendingRevocation()
                // Streaming-offload cache GC (spec §5.7). TTL pulled from settings; 0 = off.
                workManagerScheduler.scheduleStreamingCacheGc(settings.streamingCacheTtlDays)
                // Reschedule all enabled scheduled file operations (survived force-stop / app update)
                if (BuildConfig.ENABLE_SCHEDULED_OPERATIONS && settings.enableScheduledOperations) {
                    workManagerScheduler.rescheduleAll()
                    Timber.d("FastMediaSorterApp: Scheduled operations rescheduled on startup")
                }
            } catch (e: Exception) {
                Timber.e(e, "FastMediaSorterApp: Failed to apply background sync settings on startup")
            }
        }
    }

    private fun setupDebugStrictMode() {
        if (!BuildConfig.DEBUG || !ENABLE_DEBUG_STRICT_MODE) return

        // Configure StrictMode to detect issues while allowing necessary startup operations
        // Note: Early initialization (attachBaseContext, onCreate) wrapped in StrictModeHelper
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                // Use penaltyLog() instead of penaltyDeath() to log violations without crashing
                // This allows development to continue while identifying real issues
                .penaltyLog()
                .build()
        )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build()
            )
    }

    /**
     * Setup SMB connection auto-reset callback.
     * Shows toast to user when system automatically resets SMB connections
     * due to authentication or configuration errors.
     */
    private fun setupSmbAutoReset() {
        smbConnectionManager.setResetCallback(object : com.sza.fastmediasorter.data.network.SmbResetCallback {
            override fun onAutoReset(reason: String) {
                applicationScope.launch(Dispatchers.Main) {
                    try {
                        val message = getString(R.string.smb_auto_reset_notification)
                        com.sza.fastmediasorter.util.ToastThrottler.showNetworkError(
                            applicationContext, message, android.widget.Toast.LENGTH_SHORT
                        )
                        Timber.d("SMB auto-reset notification shown: $reason")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to show SMB auto-reset notification")
                    }
                }
            }
        })
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    /**
     * Called when app moves to background (all activities stopped).
     * Optimize resource usage to reduce battery drain and memory pressure.
     */
    private fun onAppBackgrounded() {
        // Note: Don't stop NetworkStateMonitor - it's needed for automatic reconnection
        // when network changes while app is in background
        
        // Suggest GC to clean up any temporary objects from UI
        // This reduces memory pressure and frequency of system-initiated GC
        System.gc()
        
        Timber.d("Background optimization complete")
    }
    
    /**
     * Called when app moves to foreground (at least one activity visible).
     * Restore any services stopped during backgrounding.
     */
    private fun onAppForegrounded() {
        // Currently no services need restarting
        // NetworkStateMonitor remains active
        Timber.d("Foreground restoration complete")
    }

    override fun attachBaseContext(base: Context) {
        // StrictMode is not yet configured here, but wrap in helper for consistency
        val contextWithLocale = com.sza.fastmediasorter.core.debug.StrictModeHelper.allowDiskReads {
            LocaleHelper.applyLocale(base)
        }
        super.attachBaseContext(contextWithLocale)
        
        // Initialize logging as early as possible so file logging exists even if app
        // crashes during or before onCreate(). Fail-safe: don't throw if logging fails.
        com.sza.fastmediasorter.core.debug.StrictModeHelper.allowDiskIO {
            try {
                LoggingHelper.initialize(base)
                LoggingHelper.installCrashHandler()
            } catch (e: Exception) {
                Timber.tag("FastMediaSorterApp").e(e, "Early logging init failed")
            }
        }
    }
    
    /**
     * Handle system memory pressure events.
     * Clear image cache ONLY on critical memory pressure to preserve thumbnails.
     * Large cache is intentional for Browse workflow - don't clear on background.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        val levelName = when(level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
            else -> "UNKNOWN"
        }
        val memInfo = "${Runtime.getRuntime().totalMemory()/1024/1024}MB / ${Runtime.getRuntime().maxMemory()/1024/1024}MB"
        
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // App is running but system is CRITICALLY low on memory - clear memory cache
                Timber.w("CRITICAL memory: level=$level($levelName), mem=$memInfo, clearing Glide memory cache")
                Glide.get(this).clearMemory()
            }
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // System is about to kill background processes
                // Clear ONLY memory cache, preserve disk cache for next launch!
                // Disk cache should persist for fast thumbnail loading on restart
                Timber.w("System killing processes: level=$level($levelName), mem=$memInfo, clearing Glide MEMORY cache only (preserving disk)")
                Glide.get(this).clearMemory()
                
                // Clean up all temp files under critical memory pressure (ML-007)
                applicationScope.launch(Dispatchers.IO) {
                    tempFileManager.cleanupAllTempFiles()
                    Timber.i("onTrimMemory(COMPLETE): All temp files cleaned up")
                }
                // DO NOT clear disk cache here - it should persist between app launches
                // Disk cache is cleared: manually in settings, on file delete/move/rename, or by FIFO eviction
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // App is FOREGROUND but system RAM is low — clear Glide memory cache to avoid OOM kill.
                // Disk cache is preserved for fast reload.
                Timber.w("LOW memory (foreground): level=$level($levelName), mem=$memInfo, clearing Glide memory cache")
                Glide.get(this).clearMemory()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                // App is FOREGROUND, moderate pressure — trim Glide to free LRU bitmaps.
                Timber.w("MODERATE memory (foreground): level=$level($levelName), mem=$memInfo, trimming Glide")
                Glide.get(this).trimMemory(level)
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                // App is in background — trim to release LRU bitmaps, preserve hot items.
                if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                    Timber.d("App backgrounded: level=$level($levelName), mem=$memInfo, trimming Glide")
                    Glide.get(this).trimMemory(level)
                }
            }
        }
    }

    private fun logAppStartupInfo() {
        val sb = StringBuilder()
        sb.append("\n==========================================\n")
        sb.append("   FAST MEDIA SORTER V2 - STARTUP INFO\n")
        sb.append("==========================================\n")

        // App Info
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Version Name", BuildConfig.VERSION_NAME))
        sb.append(String.format(Locale.US, "%-20s: %d\n", "Version Code", BuildConfig.VERSION_CODE))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "App ID", BuildConfig.APPLICATION_ID))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Build Type", BuildConfig.BUILD_TYPE))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Flavor", BuildConfig.FLAVOR))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Git Hash", BuildConfig.GIT_HASH))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Build Time", BuildConfig.BUILD_TIME))

        // Android & Device Info
        sb.append("------------------------------------------\n")
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Android Version", android.os.Build.VERSION.RELEASE))
        sb.append(String.format(Locale.US, "%-20s: %d\n", "SDK / API Level", android.os.Build.VERSION.SDK_INT))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Security Patch", android.os.Build.VERSION.SECURITY_PATCH))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Manufacturer", android.os.Build.MANUFACTURER))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Brand", android.os.Build.BRAND))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Model", android.os.Build.MODEL))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Device", android.os.Build.DEVICE))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Product", android.os.Build.PRODUCT))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Hardware", android.os.Build.HARDWARE))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Board", android.os.Build.BOARD))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Supported ABIs", android.os.Build.SUPPORTED_ABIS.joinToString()))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Fingerprint", android.os.Build.FINGERPRINT))

        // Screen Info
        sb.append("------------------------------------------\n")
        val dm = resources.displayMetrics
        sb.append(String.format(Locale.US, "%-20s: %d x %d px\n", "Screen Resolution", dm.widthPixels, dm.heightPixels))
        sb.append(String.format(Locale.US, "%-20s: %.1f\n", "Density", dm.density))
        sb.append(String.format(Locale.US, "%-20s: %d dpi\n", "DPI", dm.densityDpi))
        sb.append(String.format(Locale.US, "%-20s: %.1f x %.1f dp\n", "Screen (dp)", dm.widthPixels / dm.density, dm.heightPixels / dm.density))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Density Bucket", when {
            dm.densityDpi <= android.util.DisplayMetrics.DENSITY_LOW -> "ldpi"
            dm.densityDpi <= android.util.DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
            dm.densityDpi <= android.util.DisplayMetrics.DENSITY_HIGH -> "hdpi"
            dm.densityDpi <= android.util.DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
            dm.densityDpi <= android.util.DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
            else -> "xxxhdpi"
        }))

        // Memory Info
        sb.append("------------------------------------------\n")
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)
        sb.append(String.format(Locale.US, "%-20s: %d MB\n", "Total RAM", totalRamMb))
        sb.append(String.format(Locale.US, "%-20s: %d MB\n", "Available RAM", availRamMb))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Low Memory", if (memInfo.lowMemory) "YES" else "no"))
        val rt = Runtime.getRuntime()
        sb.append(String.format(Locale.US, "%-20s: %d MB\n", "Heap Max", rt.maxMemory() / (1024L * 1024L)))
        sb.append(String.format(Locale.US, "%-20s: %d MB\n", "Heap Total", rt.totalMemory() / (1024L * 1024L)))
        sb.append(String.format(Locale.US, "%-20s: %d MB\n", "Heap Free", rt.freeMemory() / (1024L * 1024L)))
        sb.append(String.format(Locale.US, "%-20s: %d\n", "Memory Class", activityManager.memoryClass))
        sb.append(String.format(Locale.US, "%-20s: %d\n", "Large Memory Class", activityManager.largeMemoryClass))

        // Storage Info
        sb.append("------------------------------------------\n")
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val internalTotalGb = (stat.blockSizeLong * stat.blockCountLong) / (1024L * 1024L * 1024L)
        val internalFreeGb = (stat.blockSizeLong * stat.availableBlocksLong) / (1024L * 1024L * 1024L)
        sb.append(String.format(Locale.US, "%-20s: %d GB\n", "Internal Total", internalTotalGb))
        sb.append(String.format(Locale.US, "%-20s: %d GB\n", "Internal Free", internalFreeGb))

        // CPU Info
        sb.append("------------------------------------------\n")
        sb.append(String.format(Locale.US, "%-20s: %d\n", "CPU Cores", Runtime.getRuntime().availableProcessors()))

        // Locale & Timezone
        sb.append("------------------------------------------\n")
        val currentLocale = resources.configuration.locales[0]
        sb.append(String.format(Locale.US, "%-20s: %s\n", "System Locale", currentLocale.toLanguageTag()))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Timezone", java.util.TimeZone.getDefault().id))

        sb.append("==========================================")

        Timber.w(sb.toString())
    }

    private fun logSettingsInfo(settings: com.sza.fastmediasorter.domain.model.AppSettings) {
        val sb = StringBuilder()
        sb.append("\n==========================================\n")
        sb.append("   FAST MEDIA SORTER V2 - SETTINGS DUMP\n")
        sb.append("==========================================\n")
        sb.append(String.format(Locale.US, "%-30s: %s\n", "Language", settings.language))
        sb.append(String.format(Locale.US, "%-30s: %s\n", "Flavor", BuildConfig.FLAVOR))
        sb.append(String.format(Locale.US, "%-30s: %s\n", "Build type", BuildConfig.BUILD_TYPE))
        sb.append(String.format(Locale.US, "%-30s: %b\n", "supportAudio", settings.supportAudio))
        sb.append(String.format(Locale.US, "%-30s: %b\n", "supportVideos", settings.supportVideos))
        sb.append(String.format(Locale.US, "%-30s: %b\n", "supportImages", settings.supportImages))
        sb.append(String.format(Locale.US, "%-30s: %b\n", "enablePersistentAudio", settings.enablePersistentAudioPlayback))
        sb.append(String.format(Locale.US, "%-30s: %s\n", "audioEmptyStateMode", settings.audioEmptyStateMode))
        sb.append(String.format(Locale.US, "%-30s: %b\n", "enablePhotosDuringAudio", settings.enablePhotosDuringAudio))
        sb.append(String.format(Locale.US, "%-30s: %s\n", "defaultSortMode", settings.defaultSortMode))
        sb.append(String.format(Locale.US, "%-30s: %d\n", "networkParallelism", settings.networkParallelism))
        sb.append(String.format(Locale.US, "%-30s: %d MB\n", "cacheSizeMb", settings.cacheSizeMb))
        sb.append(String.format(Locale.US, "%-30s: %b\n", "loadFullSizeImages", settings.loadFullSizeImages))
        sb.append(String.format(Locale.US, "%-30s: %b\n", "rendererMigrationEnabled", settings.rendererMigrationEnabled))
        sb.append(String.format(Locale.US, "%-30s: %b\n", "enableTranslation", settings.enableTranslation))
        sb.append(String.format(Locale.US, "%-30s: %b\n", "enableBackgroundSync", settings.enableBackgroundSync))
        sb.append(String.format(Locale.US, "%-30s: %b\n", "showDetailedErrors", settings.showDetailedErrors))
        sb.append("==========================================")
        Timber.w(sb.toString())
    }
}
