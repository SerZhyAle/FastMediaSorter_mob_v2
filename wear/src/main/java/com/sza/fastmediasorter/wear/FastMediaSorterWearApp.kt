package com.sza.fastmediasorter.wear

import android.app.Application
import android.util.Log
import com.sza.fastmediasorter.wear.core.logging.WearLogTree
import com.sza.fastmediasorter.wear.core.util.WearLocaleManager
import com.sza.fastmediasorter.wear.domain.repository.WearNowPlayingRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.DrainPendingVoiceNotesUseCase
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class FastMediaSorterWearApp : Application() {

    @Inject lateinit var preferencesRepository: WearPreferencesRepository

    @Inject lateinit var nowPlayingRepository: Lazy<WearNowPlayingRepository>

    /**
     * S1862: Lazy, so opening the note database is not part of process start - the drain is the only
     * thing here that touches it, and it runs off the main thread a moment later.
     */
    @Inject lateinit var drainPendingVoiceNotesUseCase: Lazy<DrainPendingVoiceNotesUseCase>

    /**
     * Outlives every screen by construction: the drain must finish even if the user closes the app
     * while it is running. Never cancelled - an Application has no end short of the process ending.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // S1802: the buffer tree is planted in every build type, not only in debug. Until this ticket
        // the watch planted no tree outside debug at all, so every logging call in a release build had
        // no destination and the "send logs" action would have shipped an empty report.
        val minPriority = if (BuildConfig.DEBUG) RELEASE_MIN_PRIORITY_DEBUG else RELEASE_MIN_PRIORITY
        Timber.plant(WearLogTree(minPriority))

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // S1814: apply persisted app language on startup
        applicationScope.launch(Dispatchers.Main.immediate) {
            try {
                preferencesRepository.appLanguage.firstOrNull()?.let { lang ->
                    WearLocaleManager.applyLocale(this@FastMediaSorterWearApp, lang)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                Timber.w(e, "FastMediaSorterWearApp: Failed to initialize app locale")
            }
        }

        // S1862: the second of the two drain triggers. The listener service covers the phone coming
        // back while the app is closed; this one covers the watch having been restarted since, which
        // leaves no capability change to observe.
        applicationScope.launch { drainPendingVoiceNotesUseCase.get().invoke() }

        // S2047: reset live playback flag on startup so a killed process doesn't leave stale complication state
        applicationScope.launch { nowPlayingRepository.get().clearPlayingFlag() }

        Timber.d("FastMediaSorter Wear OS app started")
    }

    private companion object {
        /** Release keeps warnings and errors only, matching what the phone persists in release. */
        const val RELEASE_MIN_PRIORITY = Log.WARN

        /** Debug retains everything the app emits, so a reproduction on a test watch is complete. */
        const val RELEASE_MIN_PRIORITY_DEBUG = Log.VERBOSE
    }
}
