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
import com.sza.fastmediasorter.core.init.AppStartupInitializer
import com.sza.fastmediasorter.core.logging.LoggingHelper
import com.sza.fastmediasorter.core.debug.DebugToolsBridge
import com.sza.fastmediasorter.core.util.CacheStatusHelper
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
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
    lateinit var smbConnectionManager: com.sza.fastmediasorter.data.network.SmbConnectionManager
    
    // Application-scoped coroutine for background initialization
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Track if app is in foreground
    @Volatile
    private var isInForeground = true

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            DebugToolsBridge.install(this)
        }

        setupDebugStrictMode()
        
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
        
        // Start network state monitoring for automatic connection recovery
        networkStateMonitor.start()
        
        // Setup SMB auto-reset callback to show user feedback
        setupSmbAutoReset()
        
        // Log Glide disk cache status at startup (off main thread)
        applicationScope.launch(Dispatchers.IO) {
            CacheStatusHelper.logGlideDiskCacheStatus(this@FastMediaSorterApp)
        }
        
        Timber.d("FastMediaSorter v2 initialized with locale: ${LocaleHelper.getLanguage(this)}")
        
        // Log detailed app startup information
        logAppStartupInfo()
        
        // Initialize all background tasks
        val startupInitializer = AppStartupInitializer(
            context = applicationContext,
            settingsRepository = settingsRepository,
            resourceRepository = resourceRepository,
            playbackPositionRepository = playbackPositionRepository,
            thumbnailCacheRepository = thumbnailCacheRepository,
            applicationScope = applicationScope
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
                        android.widget.Toast.makeText(
                            applicationContext,
                            message,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
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
        
        // Build Details
        
        // Device Info
        sb.append("------------------------------------------\n")
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Manufacturer", android.os.Build.MANUFACTURER))
        sb.append(String.format(Locale.US, "%-20s: %s\n", "Model", android.os.Build.MODEL))
        sb.append(String.format(Locale.US, "%-20s: %d (%s)\n", "SDK Version", android.os.Build.VERSION.SDK_INT, android.os.Build.VERSION.RELEASE))
        
        sb.append("==========================================")
        
        Timber.i(sb.toString())
    }
}
