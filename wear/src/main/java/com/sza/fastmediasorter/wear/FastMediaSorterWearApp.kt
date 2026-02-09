package com.sza.fastmediasorter.wear

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class FastMediaSorterWearApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.d("FastMediaSorter Wear OS app started")
    }
}
