package com.sza.fastmediasorter.wear

import android.app.Application
import android.util.Log
import com.sza.fastmediasorter.wear.core.logging.WearLogTree
import com.sza.fastmediasorter.wear.core.util.WearLocaleManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class FastMediaSorterWearApp : Application() {

    @Inject lateinit var preferencesRepository: WearPreferencesRepository

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
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                preferencesRepository.appLanguage.firstOrNull()?.let { lang ->
                    WearLocaleManager.applyLocale(this@FastMediaSorterWearApp, lang)
                }
            } catch (e: IllegalStateException) {
                Timber.w(e, "FastMediaSorterWearApp: Failed to initialize app locale")
            }
        }

        Timber.d("FastMediaSorter Wear OS app started")
    }

    private companion object {
        /** Release keeps warnings and errors only, matching what the phone persists in release. */
        const val RELEASE_MIN_PRIORITY = Log.WARN

        /** Debug retains everything the app emits, so a reproduction on a test watch is complete. */
        const val RELEASE_MIN_PRIORITY_DEBUG = Log.VERBOSE
    }
}
