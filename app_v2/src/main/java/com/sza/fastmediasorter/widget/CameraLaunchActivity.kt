package com.sza.fastmediasorter.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SaveCapturedMediaUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S0568 - transparent, no-UI trampoline for the home-screen camera launch widget.
 *
 * Hosts the CAMERA runtime-permission launcher and the capture-result launcher, then forwards every
 * decision to [CameraLaunchWidgetManager] (Rule 3 - the activity carries no business logic). Declared
 * with `Theme.FastMediaSorter.Transparent` + `noHistory`, so the user stays on the home screen during
 * the permission/camera handoff.
 */
@AndroidEntryPoint
class CameraLaunchActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var mediaCapabilities: MediaCapabilities
    @Inject lateinit var saveCapturedMedia: SaveCapturedMediaUseCase

    private lateinit var launchManager: CameraLaunchWidgetManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> launchManager.onPermissionResult(granted) }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> launchManager.onCaptureResult(result.resultCode, result.data) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchManager = CameraLaunchWidgetManager(
            activity = this,
            settingsRepository = settingsRepository,
            mediaCapabilities = mediaCapabilities,
            saveCapturedMedia = saveCapturedMedia,
            coroutineScope = lifecycleScope,
            forceVideo = intent?.getBooleanExtra(EXTRA_FORCE_VIDEO, false) == true,
            requestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            launchCapture = { intent -> captureLauncher.launch(intent) },
            finish = { finish() },
        )
        launchManager.start()
    }

    companion object {
        const val ACTION_LAUNCH = "com.sza.fastmediasorter.action.LAUNCH_CAMERA"

        /** S0795: open the camera fixed in video mode (edge-gesture "start video recording"). */
        private const val EXTRA_FORCE_VIDEO = "force_video"

        fun videoIntent(context: Context): Intent =
            Intent(context, CameraLaunchActivity::class.java).putExtra(EXTRA_FORCE_VIDEO, true)
    }
}
