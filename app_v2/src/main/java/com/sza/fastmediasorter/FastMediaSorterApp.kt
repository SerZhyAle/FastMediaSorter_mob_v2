package com.sza.fastmediasorter

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class FastMediaSorterApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var imageLoader: ImageLoader
    
    @Inject
    lateinit var workManagerScheduler: WorkManagerScheduler
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    // Application-scoped coroutine for background initialization
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        
        // Apply saved locale (fast)
        LocaleHelper.applyLocale(this)
        
        // Initialize Timber logging - deferred to avoid blocking onCreate
        initializeLogging()
        
        Timber.d("FastMediaSorter v2 initialized with locale: ${LocaleHelper.getLanguage(this)}")
        
        // Defer WorkManager scheduling to background with delay to avoid blocking app startup
        // WorkManager initialization is expensive (~100-200ms), defer until after UI is rendered
        applicationScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(500) // Wait for UI to render first
                workManagerScheduler.scheduleTrashCleanup()
                Timber.d("Background initialization: WorkManager scheduled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to schedule WorkManager in background")
            }
        }
    }
    
    /**
     * Initialize Timber logging.
     * Moved to separate method to make it easier to profile/optimize.
     */
    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // TODO: Implement custom Timber tree for release logging
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // Log only critical errors in release
                    if (priority >= android.util.Log.ERROR) {
                        // TODO: Save to file or send to crash reporting
                    }
                }
            })
        }
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }
}
