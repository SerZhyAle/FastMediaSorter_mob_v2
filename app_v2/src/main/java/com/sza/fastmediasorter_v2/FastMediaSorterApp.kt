package com.sza.fastmediasorter_v2

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.sza.fastmediasorter_v2.core.util.LocaleHelper
import com.sza.fastmediasorter_v2.worker.WorkManagerScheduler
import dagger.hilt.android.HiltAndroidApp
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

    override fun onCreate() {
        super.onCreate()
        
        // Apply saved locale
        LocaleHelper.applyLocale(this)
        
        // Initialize Timber logging
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
        
        Timber.d("FastMediaSorter v2 initialized with locale: ${LocaleHelper.getLanguage(this)}")
        
        // Schedule periodic trash cleanup worker
        workManagerScheduler.scheduleTrashCleanup()
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
