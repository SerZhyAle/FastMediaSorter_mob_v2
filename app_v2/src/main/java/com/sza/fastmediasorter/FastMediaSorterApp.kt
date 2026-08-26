package com.sza.fastmediasorter

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.google.android.material.color.DynamicColors
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.cache.TranslationCacheManager
import com.sza.fastmediasorter.core.debug.DebugToolsBridge
import com.sza.fastmediasorter.core.init.AppStartupInitializer
import com.sza.fastmediasorter.core.init.FirstFrameSignal
import com.sza.fastmediasorter.core.logging.LoggingHelper
import com.sza.fastmediasorter.core.memory.MemoryCheckpoint
import com.sza.fastmediasorter.core.memory.MemoryProbe
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayStartupCoordinator
import com.sza.fastmediasorter.core.util.CacheStatusHelper
import com.sza.fastmediasorter.core.util.GmsAvailabilityChecker
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.domain.model.SensitiveSetting
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.worker.DeferredStartupWorker
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class FastMediaSorterApp : Application(), Configuration.Provider {

    companion object {
        // Static context for Glide ModelLoader factory (needed for Hilt EntryPoint access)
        lateinit var appContext: Context
            private set

        private const val ENABLE_DEBUG_STRICT_MODE = true

        // Field-name fragments that mark a value as a credential / token.
        // Matched case-insensitively against lowercase AppSettings field names.
        // Extend (don't replace) when AppSettings gains a new credential-like field.
        private val SECRET_FIELD_HINTS = listOf(
            "password", "secret", "token", "apikey", "credential",
        )
    }
    
    // S0194: 13 Application-level singletons wrapped in dagger.Lazy<T> so Hilt
    // defers their construction until first .get(). The 4 fields below that
    // synchronously register lifecycle observers / OS callbacks in onCreate
    // intentionally stay eager - they are addressed separately by S0195.
    @Inject
    lateinit var workManagerScheduler: dagger.Lazy<WorkManagerScheduler>

    @Inject
    lateinit var workerFactory: dagger.Lazy<HiltWorkerFactory>

    @Inject
    lateinit var settingsRepository: dagger.Lazy<SettingsRepository>

    @Inject
    lateinit var playbackPositionRepository: dagger.Lazy<com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository>

    @Inject
    lateinit var thumbnailCacheRepository: dagger.Lazy<com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository>

    @Inject
    lateinit var resourceRepository: dagger.Lazy<com.sza.fastmediasorter.domain.repository.ResourceRepository>

    // S0869: dereferenced by the onCreate IO warm-up so Room's open + migration ladder run off the
    // main thread, ahead of MainActivity's ViewModel-construction DB touch. Lazy so the field itself
    // does not force provideAppDatabase() during Hilt field injection on the main thread.
    @Inject
    lateinit var appDatabase: dagger.Lazy<com.sza.fastmediasorter.data.local.db.AppDatabase>

    @Inject
    lateinit var unifiedCache: dagger.Lazy<com.sza.fastmediasorter.core.cache.UnifiedFileCache>

    /** S0200 Phase 05: run-once legacy auth-state wipe. Injected via Hilt; called from onCreate. */
    @Inject
    lateinit var s0200AuthStateWipe: dagger.Lazy<com.sza.fastmediasorter.data.migration.S0200AuthStateWipe>

    /** S0386 Phase 13: run-once de-bundle upgrade reconciliation (force-OFF un-installed toggles). */
    @Inject
    lateinit var s0386UpgradeReconciliation: dagger.Lazy<com.sza.fastmediasorter.data.migration.S0386UpgradeReconciliation>

    /** S0981: run-once flip of linkAutoDownloadOpenInPlayer from the old ON default to OFF. */
    @Inject
    lateinit var s0981OpenInPlayerDefaultOff:
        dagger.Lazy<com.sza.fastmediasorter.data.migration.S0981OpenInPlayerDefaultOff>

    @Inject
    lateinit var cachedFileListRepository: dagger.Lazy<com.sza.fastmediasorter.data.repository.CachedFileListRepository>

    // S0195: the four network lifecycle hooks (NetworkStateMonitor / SmbConnectionManager /
    // SmbBackgroundLifecycleManager / NetworkLifecycleObserver) used to be @Inject'd here and
    // started in onCreate. They are now bootstrapped lazily by NetworkLifecycleBootstrapper
    // on first real remote use - see data.network.lifecycle.NetworkLifecycleBootstrapper.

    @Inject
    lateinit var tempFileManager: dagger.Lazy<com.sza.fastmediasorter.domain.transfer.TempFileManager>

    @Inject
    lateinit var renameVirtualResourcesUseCase: dagger.Lazy<com.sza.fastmediasorter.domain.usecase.RenameVirtualResourcesUseCase>

    @Inject
    lateinit var backfillSmbCredentialShareNameUseCase: dagger.Lazy<com.sza.fastmediasorter.domain.usecase.BackfillSmbCredentialShareNameUseCase>

    @Inject
    lateinit var inputBindingRepository: dagger.Lazy<com.sza.fastmediasorter.data.input.InputBindingRepository>

    @Inject
    lateinit var defaultsMapLoader: dagger.Lazy<com.sza.fastmediasorter.data.input.DefaultsMapLoader>

    // S0207 Phase 01: memory observability channel - emitted at fixed lifecycle anchors.
    // Direct (non-Lazy) injection: used at end of onCreate, before any background work runs.
    @Inject
    lateinit var memoryProbe: MemoryProbe

    @Inject
    lateinit var startupInitializer: dagger.Lazy<AppStartupInitializer>

    @Inject
    lateinit var screenGestureOverlayStartupCoordinator: dagger.Lazy<ScreenGestureOverlayStartupCoordinator>

    // S0213 Pillar B: OOM-safe wrapper installed into media3 logging at process start so a
    // near-OOM stacktrace stringification cannot itself become a fatal crash.
    @Inject
    lateinit var media3Logger: com.sza.fastmediasorter.core.logging.Media3OomSafeLogger

    // S0213 Pillar C: release-safe signal channel wired into MemoryEnduranceTracker at process
    // start so verdict=FAIL / drift ≥ 50 % events reach the player UI in production builds.
    @Inject
    lateinit var memoryDegradationSignal: com.sza.fastmediasorter.core.memory.MemoryDegradationSignal

    // S0439: program-wide screen-rotation policy applier; registered as ActivityLifecycleCallbacks in onCreate.
    @Inject
    lateinit var appOrientationManager: com.sza.fastmediasorter.core.orientation.AppOrientationManager

    @Inject
    lateinit var appKeepScreenAwakeManager: com.sza.fastmediasorter.core.ui.AppKeepScreenAwakeManager

    // Application-scoped coroutine for background initialization
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val firstFrameSignal by lazy { FirstFrameSignal() }
    
    // Track if app is in foreground
    @Volatile
    private var isInForeground = true

    @Volatile
    private var gestureOverlayRestoreScheduled = false

    override fun onCreate() {
        super.onCreate()

        // S0213 Pillar B: install OOM-safe media3 logger BEFORE any media3 component is touched.
        // Timber was planted earlier in attachBaseContext via LoggingHelper.initialize(), so the
        // fallback Timber.w in the logger has a working sink.
        androidx.media3.common.util.Log.setLogger(media3Logger)
        Timber.i("FastMediaSorterApp: media3 OOM-safe logger installed")

        // S0869: warm-open Room off the main thread at the very start of onCreate. The first .get()
        // runs provideAppDatabase() - SQLite open + the full 1..38 migration ladder - so doing that
        // dereference here on Dispatchers.IO keeps it off MainActivity's main-thread ViewModel-
        // construction path. Deliberately NOT gated on firstFrameSignal (which only fires after
        // onCreate); the point is to start before MainActivity.onCreate touches the DB. The S0731
        // corruption-recovery contract inside provideAppDatabase is unchanged. Residual race accepted
        // (a main-thread .get() that still beats this warm-up blocks on the @Singleton lock) - S0869 §3.
        applicationScope.launch(Dispatchers.IO) {
            try {
                appDatabase.get()
            } catch (e: Exception) {
                // provideAppDatabase already owns backup+reset+notice recovery (S0731); this guard only
                // stops a genuine rebuild failure from crashing the process via the warm-up coroutine.
                Timber.e(e, "FastMediaSorterApp: Room warm-up failed")
            }
        }

        // S0213 Pillar C: connect the release-safe degradation signal to MemoryEnduranceTracker so
        // verdict=FAIL events reach the player UI even in non-DEBUG builds.
        com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker.wireDegradationSignal(memoryDegradationSignal)

        // S0328: apply the saved app color theme (Auto/Light/Dark) before any Activity inflates.
        com.sza.fastmediasorter.core.theme.ColorThemePrefs.applySavedMode(this)

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
                scheduleGestureOverlayRestore()
                firstFrameSignal.signal()
            }
        })

        // S0439: apply the program-wide screen-rotation policy to every non-self-managed activity.
        registerActivityLifecycleCallbacks(appOrientationManager)
        registerActivityLifecycleCallbacks(appKeepScreenAwakeManager)
        // S0943: decorate the focused view in-place with the D-pad/TV focus outline on every Activity
        // window (opt-out via FocusDecorationExcluded); one controller per window, hidden in touch mode.
        registerActivityLifecycleCallbacks(
            com.sza.fastmediasorter.core.ui.focus.FocusDecorationActivityCallbacks(),
        )
        // S0195: SMB / protocol-neutral lifecycle observers are now registered lazily by
        // NetworkLifecycleBootstrapper on first remote use - formerly attached eagerly here.

        // PDF Support: Using built-in Android PdfRenderer (API 21+)
        // No external PDF library needed - Android's PdfRenderer handles PDF rendering natively
        // PDFBox was removed to avoid BouncyCastle conflicts and reduce APK size
        
        // Apply saved locale - SharedPreferences read already wrapped in StrictModeHelper
        LocaleHelper.applyLocale(this)
        // Note: logging initialized early in attachBaseContext to capture startup crashes
        
        // Cast SDK is initialised lazily by CastController.init() (S0403 seam; GMS-backed
        // CastMediaManagerImpl in the castEnabled source set) when a player opens, not here.
        // Eager init loaded the cast.framework.dynamite module on every cold start - even on sessions
        // that never open a player - which both slowed startup and exposed the process to GMS forcing
        // a SIG 9 restart when it hot-swaps that dynamite module. The only thing lost is warm device
        // discovery before the first cast tap; the controller already creates the singleton on demand.

        Timber.d("FastMediaSorter v2 initialized with locale: ${LocaleHelper.getLanguage(this)}")

        // S1153: hasPreviousCrash() lists the log dir off disk. It only gates the warning log below
        // (nothing synchronous depends on it), so read it on IO instead of blocking Application.onCreate
        // on the main thread.
        applicationScope.launch(Dispatchers.IO) {
            if (LoggingHelper.hasPreviousCrash()) {
                Timber.w("=== PREVIOUS SESSION ENDED WITH A CRASH - use 'Export debug logs' to collect reports ===")
            }
        }

        // Keep only the genuinely early startup work here. Heavier maintenance tasks move behind
        // the shared first-frame/deferred-worker gate so cold start stays off the critical path.
        startupInitializer.get().initialize()

        // S1650: build Glide off the main thread. Deliberately NOT gated on firstFrameSignal, unlike
        // every launch below it - the first image load can happen on the very first screen, and that
        // load is exactly what this warm-up has to beat. It waits internally for the cache-size mirror
        // write started by initialize() above.
        applicationScope.launch(Dispatchers.IO) {
            startupInitializer.get().warmGlide()
        }

        applicationScope.launch(Dispatchers.IO) {
            firstFrameSignal.await(timeoutMs = 60_000)
            com.sza.fastmediasorter.core.cache.TranslationCacheManager.clearAll()
        }

        applicationScope.launch(Dispatchers.IO) {
            firstFrameSignal.await(timeoutMs = 60_000)
            logAppStartupInfo()
        }

        // S0200 Phase 05: legacy auth-state wipe - idempotent, runs once after upgrade then no-ops.
        applicationScope.launch(Dispatchers.IO) {
            firstFrameSignal.await(timeoutMs = 60_000)
            s0200AuthStateWipe.get().runIfNeeded()
        }

        // S0386 Phase 13: reconcile OCR/translation toggles after the de-bundle upgrade - run once,
        // idempotent, no downloads. Forces OFF a toggle that is ON but whose set is not installed.
        applicationScope.launch(Dispatchers.IO) {
            firstFrameSignal.await(timeoutMs = 60_000)
            s0386UpgradeReconciliation.get().runIfNeeded()
        }

        // S0981: run-once flip of linkAutoDownloadOpenInPlayer from the old ON default to OFF -
        // the field's code default changed but pre-existing installs already persisted `true`.
        applicationScope.launch(Dispatchers.IO) {
            firstFrameSignal.await(timeoutMs = 60_000)
            s0981OpenInPlayerDefaultOff.get().runIfNeeded()
        }

        // Trash cleanup now handled synchronously in BrowseViewModel (on resource open/close)
        // WorkManager periodic cleanup disabled - unnecessary with sync cleanup
        // Left for potential future background tasks (e.g., network resource sync)
        
        // Phase 06: anchor startup scheduling on the shared first-frame signal instead of a
        // hard-coded delay so every deferred startup path follows the same gate.
        applicationScope.launch(Dispatchers.IO) {
            try {
                firstFrameSignal.await(timeoutMs = 60_000)
                val settings = settingsRepository.get().getSettings().first()
                logSettingsInfo(settings)
                // Sync the synchronous SP mirror so the player picks the right controls layout
                // on first inflate after upgrade from a build that didn't write the SP file yet.
                com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs.setCompact(
                    this@FastMediaSorterApp,
                    settings.useCompactElements
                )
                // S0328: keep the synchronous color-theme mirror in sync with the authoritative
                // DataStore value (covers upgrades / settings import where the mirror is stale).
                // Re-applying the mode here fixes the current process too; otherwise a stale mirror
                // can keep the UI light until the user fully kills the process.
                val normalizedColorTheme = com.sza.fastmediasorter.core.theme.ColorThemePrefs
                    .normalizeValue(settings.colorTheme)
                com.sza.fastmediasorter.core.theme.ColorThemePrefs.setMode(
                    this@FastMediaSorterApp,
                    normalizedColorTheme
                )
                kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
                    com.sza.fastmediasorter.core.theme.ColorThemePrefs.applyMode(normalizedColorTheme)
                }
                // S0194: dereference Lazy<WorkManagerScheduler> once per coroutine entry.
                val scheduler = workManagerScheduler.get()
                if (settings.enableBackgroundSync) {
                    scheduler.scheduleResourcesSync(
                        settings.backgroundSyncIntervalHours.toLong()
                    )
                    Timber.i("FastMediaSorterApp: Background resource sync scheduled (${settings.backgroundSyncIntervalHours}h)")
                } else {
                    // Ensure legacy scheduled work is cleaned up on first launch after update
                    scheduler.cancelResourcesSync()
                    Timber.d("FastMediaSorterApp: Background sync is disabled, skipping scheduling")
                }
                // Orphan cleanup runs regardless of sync setting - lightweight maintenance task
                scheduler.scheduleOrphanCleanup()
                // Retry any OAuth token revocations that failed during sign-out (B5-T3)
                scheduler.schedulePendingRevocation()
                enqueueDeferredStartupWorker()
                // Streaming-offload cache GC (spec §5.7). TTL pulled from settings; 0 = off.
                scheduler.scheduleStreamingCacheGc(settings.streamingCacheTtlDays)
                // Reschedule all enabled scheduled file operations (survived force-stop / app update).
                // A durably-paused scheduler stays paused across process death (S0353).
                if (BuildConfig.ENABLE_SCHEDULED_OPERATIONS &&
                    settings.enableScheduledOperations &&
                    !settings.scheduledOperationsPaused
                ) {
                    scheduler.rescheduleAll()
                    Timber.d("FastMediaSorterApp: Scheduled operations rescheduled on startup")
                }
            } catch (e: Exception) {
                Timber.e(e, "FastMediaSorterApp: Failed to apply background sync settings on startup")
            }
        }

        // S0207 Phase 01: APP_STARTED memory probe - last call in onCreate so the
        // measurement reflects the post-init state of the process.
        memoryProbe.record(MemoryCheckpoint.APP_STARTED)
    }

    private fun enqueueDeferredStartupWorker() {
        val request = OneTimeWorkRequestBuilder<DeferredStartupWorker>()
            .setInitialDelay(30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            DeferredStartupWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
        Timber.i("FastMediaSorterApp: deferred startup worker enqueued")
    }

    private fun scheduleGestureOverlayRestore() {
        if (gestureOverlayRestoreScheduled) return
        gestureOverlayRestoreScheduled = true
        applicationScope.launch {
            try {
                screenGestureOverlayStartupCoordinator.get().restoreIfNeeded()
            } catch (e: Exception) {
                Timber.e(e, "FastMediaSorterApp: gesture overlay restore failed on startup")
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

    // S0195: SMB auto-reset toast wiring moved verbatim into NetworkLifecycleBootstrapper.


    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory.get())
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
                releaseRecomputableCaches()
            }
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // System is about to kill background processes
                // Clear ONLY memory cache, preserve disk cache for next launch!
                // Disk cache should persist for fast thumbnail loading on restart
                Timber.w("System killing processes: level=$level($levelName), mem=$memInfo, clearing Glide MEMORY cache only (preserving disk)")
                Glide.get(this).clearMemory()
                releaseRecomputableCaches()


                // Clean up all temp files under critical memory pressure (ML-007)
                applicationScope.launch(Dispatchers.IO) {
                    tempFileManager.get().cleanupAllTempFiles()
                    Timber.i("onTrimMemory(COMPLETE): All temp files cleaned up")
                }
                // DO NOT clear disk cache here - it should persist between app launches
                // Disk cache is cleared: manually in settings, on file delete/move/rename, or by FIFO eviction
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // App is FOREGROUND but system RAM is low - clear Glide memory cache to avoid OOM kill.
                // Disk cache is preserved for fast reload.
                Timber.w("LOW memory (foreground): level=$level($levelName), mem=$memInfo, clearing Glide memory cache")
                Glide.get(this).clearMemory()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                // App is FOREGROUND, moderate pressure - trim Glide to free LRU bitmaps.
                Timber.w("MODERATE memory (foreground): level=$level($levelName), mem=$memInfo, trimming Glide")
                Glide.get(this).trimMemory(level)
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                // App is in background - trim to release LRU bitmaps, preserve hot items.
                if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                    Timber.d("App backgrounded: level=$level($levelName), mem=$memInfo, trimming Glide")
                    Glide.get(this).trimMemory(level)
                }
            }
        }
    }

    /**
     * S1299/S1300: until now onTrimMemory only trimmed Glide, so the app's own in-memory caches -
     * the media-file lists and the PDF translation cache - stayed fully resident even at
     * RUNNING_CRITICAL, including while backgrounded. Both hold recomputable data: dropping them
     * costs a rescan or a re-translation, never user data.
     */
    private fun releaseRecomputableCaches() {
        MediaFilesCacheManager.clearAllCaches()
        TranslationCacheManager.trimForMemoryPressure()
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
        sb.append(String.format(Locale.US, "%-36s: %s\n", "Flavor", BuildConfig.FLAVOR))
        sb.append(String.format(Locale.US, "%-36s: %s\n", "Build type", BuildConfig.BUILD_TYPE))
        sb.append("------------------------------------------\n")

        // Reflection-based dump of every AppSettings field. Adding a new field to
        // the data class auto-extends the dump on next launch - no edits here.
        // Java reflection avoids the kotlin-reflect runtime (not on classpath).
        try {
            // getDeclaredFields() preserves source order on HotSpot/ART for data classes.
            val fields = settings.javaClass.declaredFields
            for (field in fields) {
                if (field.isSynthetic) continue
                val mods = field.modifiers
                if (java.lang.reflect.Modifier.isStatic(mods)) continue
                field.isAccessible = true
                val raw: Any? = field.get(settings)
                val name = field.name
                val display = formatSettingValue(field, raw)
                sb.append(String.format(Locale.US, "%-36s: %s\n", name, display))
            }
        } catch (t: Throwable) {
            sb.append("[reflection failed: ").append(t.javaClass.simpleName)
                .append(": ").append(t.message).append("]\n")
        }

        sb.append("==========================================")
        Timber.w(sb.toString())
    }

    /**
     * Renders one AppSettings value for the dump. Masks credential-like fields
     * by field-name pattern. The matcher is intentionally broad so any future
     * `*password*`, `*secret*`, `*token*`, `*apiKey*` field is masked on day one.
     *
     * S1254: the name hints die silently when R8 renames fields (a real password reached
     * exported diagnostics twice), so [SensitiveSetting] presence - checked via class
     * identity, rename-proof - is the second, authoritative trigger.
     */
    private fun formatSettingValue(field: java.lang.reflect.Field, value: Any?): String {
        val nameLower = field.name.lowercase(Locale.US)
        val isSecret = field.isAnnotationPresent(SensitiveSetting::class.java) ||
            SECRET_FIELD_HINTS.any { it in nameLower }
        if (isSecret) {
            return when (value) {
                null -> "<null>"
                is CharSequence -> if (value.isEmpty()) "<empty>" else "<set, len=${value.length}>"
                else -> "<set>"
            }
        }
        return when (value) {
            null -> "null"
            is CharSequence -> if (value.isEmpty()) "<empty>" else value.toString()
            else -> value.toString()
        }
    }
}
