package com.sza.fastmediasorter.core.assistant

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.sza.fastmediasorter.core.assistant.functions.MediaNavigationFunctions
import com.sza.fastmediasorter.core.assistant.functions.MediaSearchFunctions
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

private const val API_APP_FUNCTIONS = 36

/**
 * Android 16+ (API 36) platform service entry point for system assistant AppFunctions (S2920).
 * Exposes self-describing callable functions (search, open media, open folder) to the platform assistant.
 */
@RequiresApi(API_APP_FUNCTIONS)
@AndroidEntryPoint
class MediaAssistantAppFunctionService : Service() {

    @Inject
    lateinit var mediaSearchFunctions: MediaSearchFunctions

    @Inject
    lateinit var mediaNavigationFunctions: MediaNavigationFunctions

    override fun onCreate() {
        super.onCreate()
        Timber.d("S2920: MediaAssistantAppFunctionService created")
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("MediaAssistantAppFunctionService bound with intent action: %s", intent?.action)
        return null
    }

    companion object {
        const val API_LEVEL: Int = API_APP_FUNCTIONS
    }
}
