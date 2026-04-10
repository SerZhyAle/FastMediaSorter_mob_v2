package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.os.Bundle
import timber.log.Timber

/**
 * Lightweight lifecycle coordinator for StandalonePlayerActivity.
 * Delegates onResume/onPause/onDestroy to StandaloneViewManager so that
 * playback stops when the app goes to background and resumes on return.
 */
class StandalonePlayerLifecycleManager(
    @Suppress("UnusedPrivateProperty")
    private val activity: Activity,
    private val viewManager: StandaloneViewManager
) {

    fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("StandalonePlayerLifecycleManager.onCreate")
    }

    fun onResume() {
        Timber.d("StandalonePlayerLifecycleManager.onResume")
        viewManager.onResume()
    }

    fun onPause() {
        Timber.d("StandalonePlayerLifecycleManager.onPause")
        viewManager.onPause()
    }

    fun onDestroy() {
        Timber.d("StandalonePlayerLifecycleManager.onDestroy")
        // Cleanup handled by viewManager.release() called in StandalonePlayerActivity.onDestroy()
    }
}
