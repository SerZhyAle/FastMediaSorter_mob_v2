package com.sza.fastmediasorter.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.data.capture.CameraCaptureSaver
import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S0369 - transparent, no-UI trampoline for the quick camera-capture widget.
 *
 * Hosts the CAMERA runtime-permission launcher and the capture-result launcher, then forwards every
 * decision to [CameraQuickCaptureLaunchManager] (Rule 3 - the activity carries no business logic).
 * Declared with `Theme.FastMediaSorter.Transparent` + `noHistory`, so the user stays on the home
 * screen during permission/camera handoff.
 */
@AndroidEntryPoint
class CameraQuickCaptureActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var cameraCaptureSaver: CameraCaptureSaver
    @Inject lateinit var fileOperationHandler: UnifiedFileOperationHandler

    private lateinit var launchManager: CameraQuickCaptureLaunchManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> launchManager.onPermissionResult(granted) }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> launchManager.onCaptureResult(result.resultCode) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        launchManager = CameraQuickCaptureLaunchManager(
            activity = this,
            appWidgetId = appWidgetId,
            settingsRepository = settingsRepository,
            cameraCaptureSaver = cameraCaptureSaver,
            fileOperationHandler = fileOperationHandler,
            coroutineScope = lifecycleScope,
            requestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            launchCapture = { intent -> captureLauncher.launch(intent) },
            finish = { finish() },
        )
        launchManager.start()
    }

    companion object {
        const val ACTION_CAPTURE = "com.sza.fastmediasorter.action.QUICK_CAMERA_CAPTURE"
    }
}
