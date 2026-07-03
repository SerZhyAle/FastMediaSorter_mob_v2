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
 * S0790-S0794 - transparent, no-UI trampoline for the edge-gesture "take photo" family. Hosts the
 * CAMERA permission + capture-result launchers and forwards every decision to
 * [PhotoCaptureLaunchManager] (Rule 3 - no business logic here). The optional auto-action extra selects
 * the post-capture route (null = save + toast; otherwise the standalone-viewer auto-action).
 */
@AndroidEntryPoint
class PhotoCaptureLaunchActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var mediaCapabilities: MediaCapabilities
    @Inject lateinit var saveCapturedMedia: SaveCapturedMediaUseCase

    private lateinit var launchManager: PhotoCaptureLaunchManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> launchManager.onPermissionResult(granted) }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> launchManager.onCaptureResult(result.resultCode, result.data) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchManager = PhotoCaptureLaunchManager(
            activity = this,
            settingsRepository = settingsRepository,
            mediaCapabilities = mediaCapabilities,
            saveCapturedMedia = saveCapturedMedia,
            coroutineScope = lifecycleScope,
            autoAction = intent?.getStringExtra(EXTRA_AUTO_ACTION),
            requestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            launchCapture = { intent -> captureLauncher.launch(intent) },
            finish = { finish() },
        )
        launchManager.start()
    }

    companion object {
        private const val EXTRA_AUTO_ACTION = "photo_auto_action"

        /**
         * @param autoAction null for a plain save+toast (S0790), or a
         *   [com.sza.fastmediasorter.ui.player.standalone.PhotoVideoStandaloneActivity] AUTO_ACTION_* to
         *   route the captured photo into the viewer (S0791 send-to / S0792 editor / S0794 OCR-translate).
         */
        fun intent(context: Context, autoAction: String?): Intent =
            Intent(context, PhotoCaptureLaunchActivity::class.java)
                .apply { autoAction?.let { putExtra(EXTRA_AUTO_ACTION, it) } }
    }
}
