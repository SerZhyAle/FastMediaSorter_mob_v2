package com.sza.fastmediasorter.widget

import android.Manifest
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber

/**
 * S0349 - transparent, no-UI trampoline for the Quick Audio Recorder widget.
 *
 * Hosts the runtime-permission launcher and forwards every decision to
 * [QuickAudioRecorderLaunchManager] (Rule 3 - the activity carries no business logic). Declared
 * with [Theme.FastMediaSorter.Transparent] + `noHistory`, so the user stays on the home screen.
 */
class QuickAudioRecorderActivity : AppCompatActivity() {

    private lateinit var launchManager: QuickAudioRecorderLaunchManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> launchManager.onPermissionResult(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchManager = QuickAudioRecorderLaunchManager(
            activity = this,
            requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            finish = { finish() },
        )
        launchManager.handleToggle()
    }

    companion object {
        const val ACTION_TOGGLE = "com.sza.fastmediasorter.action.QUICK_RECORDER_TOGGLE"
    }
}
