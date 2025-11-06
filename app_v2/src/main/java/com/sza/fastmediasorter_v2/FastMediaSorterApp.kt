package com.sza.fastmediasorter_v2

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class FastMediaSorterApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
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
        
        Timber.d("FastMediaSorter v2 initialized")
    }
}
