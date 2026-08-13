package com.sza.fastmediasorter.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S0568 - transparent, no-UI trampoline for the home-screen camera launch widget.
 *
 * Hosts the CAMERA runtime-permission launcher and the capture-result launcher, then forwards every
 * decision to [CameraLaunchWidgetManager] (Rule 3 - the activity carries no business logic). Declared
 * with `Theme.FastMediaSorter.Transparent` + `excludeFromRecents`, so the user stays on the home screen
 * during the permission/camera handoff.
 *
 * S1174 - deliberately NOT `noHistory`, same as the sibling quick-capture trampoline: the opaque capture
 * host stops this transparent activity, and the flag then finished it before the result came back.
 */
@AndroidEntryPoint
class CameraLaunchActivity : AppCompatActivity() {

    @Inject lateinit var launchManagerFactory: CameraLaunchWidgetManagerFactory

    private lateinit var launchManager: CameraLaunchWidgetManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> launchManager.onPermissionResult(granted) }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { launchManager.onCaptureResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchManager = launchManagerFactory.create(
            activity = this,
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
