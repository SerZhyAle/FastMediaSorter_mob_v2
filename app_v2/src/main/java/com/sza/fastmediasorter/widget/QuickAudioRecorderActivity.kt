package com.sza.fastmediasorter.widget

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.ui.ProgramScreenGuardManager
import com.sza.fastmediasorter.core.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S0349 - transparent, no-UI trampoline for the Quick Audio Recorder widget.
 *
 * Hosts the runtime-permission launcher and forwards every decision to
 * [QuickAudioRecorderLaunchManager] (Rule 3 - the activity carries no business logic). Declared
 * with [Theme.FastMediaSorter.Transparent] + `noHistory`, so the user stays on the home screen.
 */
@AndroidEntryPoint
class QuickAudioRecorderActivity : AppCompatActivity() {
    // S2930: BaseActivity is generic over a ViewBinding, so the locale wrapper is applied directly here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject lateinit var programScreenGuard: ProgramScreenGuardManager

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
            admitStart = { start -> programScreenGuard.admit(this, InternalRouteCatalog.KEY_QUICK_VOICE, start) },
        )
        launchManager.handleToggle()
    }

    companion object {
        const val ACTION_TOGGLE = "com.sza.fastmediasorter.action.QUICK_RECORDER_TOGGLE"
    }
}
