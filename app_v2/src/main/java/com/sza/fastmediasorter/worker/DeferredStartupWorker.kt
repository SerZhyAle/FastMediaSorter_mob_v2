package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.core.init.AppStartupInitializer
import com.sza.fastmediasorter.core.init.DefaultPlayerStateBootstrapper
import com.sza.fastmediasorter.core.util.CacheStatusHelper
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.transfer.TempFileManager
import com.sza.fastmediasorter.domain.usecase.BackfillSmbCredentialShareNameUseCase
import com.sza.fastmediasorter.domain.usecase.apps.RefreshInstalledAppsUseCase
import com.sza.fastmediasorter.domain.usecase.streams.MigrateStreamShortcutsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Best-effort Phase 06 startup maintenance worker.
 *
 * Finite cleanup/migration tasks run sequentially here. The ConnectionThrottleManager collector is
 * bootstrapped indirectly through [AppStartupInitializer.runDeferredStartupTasks], which launches
 * that long-lived settings flow into the application scope exactly once.
 */
@HiltWorker
class DeferredStartupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val appStartupInitializer: dagger.Lazy<AppStartupInitializer>,
    private val settingsRepository: dagger.Lazy<SettingsRepository>,
    private val tempFileManager: dagger.Lazy<TempFileManager>,
    private val backfillSmbCredentialShareNameUseCase: dagger.Lazy<BackfillSmbCredentialShareNameUseCase>,
    private val refreshInstalledApps: dagger.Lazy<RefreshInstalledAppsUseCase>,
    private val migrateStreamShortcuts: dagger.Lazy<MigrateStreamShortcutsUseCase>,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // S1323: no "clear-failed-video-cache" task here. VideoExtractionFailurePersistence exists
        // to survive restarts - it stamps every entry and expires it after seven days - and wiping
        // it on every launch made that storage dead weight: a video whose frame extraction timed
        // out (10 s per file over SFTP) paid the same timeout again on the next start. The reset
        // now happens only on an explicit refresh gesture.
        runTask("cleanup-old-temp-files") {
            tempFileManager.get().cleanupOldTempFiles(24 * 60 * 60 * 1000L)
        }
        runTask("backfill-smb-credential-share-name") {
            backfillSmbCredentialShareNameUseCase.get().invoke()
        }
        runTask("default-player-state-bootstrap") {
            DefaultPlayerStateBootstrapper.apply(applicationContext, settingsRepository.get())
        }
        runTask("log-glide-disk-cache-status") {
            CacheStatusHelper.logGlideDiskCacheStatus(applicationContext)
        }
        // S1480: Glide builds itself lazily, and its applyOptions() reads SharedPreferences and creates
        // the cache directory ON THE CALLING THREAD - so whichever screen loads the first image pays
        // tens of milliseconds of disk work on the main thread. Warmed here, after the status log above
        // so S1322's observation of the pre-Glide directory state still measures what it claims to.
        runTask("warm-glide") {
            Glide.get(applicationContext)
        }
        runTask("run-app-startup-initializer-deferred-tasks") {
            appStartupInitializer.get().runDeferredStartupTasks()
        }
        // S1471: ahead of the app-cache seed below, which is the slowest task here - a shortcut the user
        // may tap at any moment should not wait behind it.
        runTask("migrate-stream-shortcuts") {
            migrateStreamShortcuts.get().invoke()
        }
        // S1401: last, and here rather than in AppStartupInitializer - this is the deferred-work host,
        // and the cache must be filled before the user asks for a list, not while they wait for one.
        runTask("seed-installed-app-cache") {
            refreshInstalledApps.get().refreshIfStale()
        }
        return Result.success()
    }

    private suspend fun runTask(label: String, block: suspend () -> Unit) {
        runCatching { block() }
            .onSuccess { Timber.i("DeferredStartupWorker: completed %s", label) }
            .onFailure { Timber.e(it, "DeferredStartupWorker: failed %s", label) }
    }

    companion object {
        const val WORK_NAME = "deferred_startup"
    }
}