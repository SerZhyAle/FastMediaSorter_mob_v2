package com.sza.fastmediasorter.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLocationProvider
import com.sza.fastmediasorter.ui.cameracapture.helpers.HeadlessPhotoCapturer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S0790-S0794 - transparent, no-UI trampoline for the edge-gesture "take photo" family. Hosts the
 * CAMERA permission launcher and the headless [HeadlessPhotoCapturer], and forwards every decision to
 * [PhotoCaptureLaunchManager] (Rule 3 - no business logic here). The optional auto-action extra selects
 * the post-capture route (null = save + toast; otherwise the standalone-viewer auto-action).
 *
 * The photo is captured headlessly (CameraX [androidx.camera.core.ImageCapture] bound to this
 * activity's lifecycle, no preview, no second activity), so there is no camera screen and no activity
 * result to lose - the trampoline stays `android:noHistory`-free so the first-use permission dialog
 * can return here.
 */
@AndroidEntryPoint
class PhotoCaptureLaunchActivity : AppCompatActivity() {

    @Inject lateinit var launchManagerFactory: PhotoCaptureLaunchManagerFactory

    private lateinit var launchManager: PhotoCaptureLaunchManager
    private val locationProvider = CameraLocationProvider()
    private lateinit var capturer: HeadlessPhotoCapturer

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> launchManager.onPermissionResult(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        capturer = HeadlessPhotoCapturer(lifecycleOwner = this, context = this)
        launchManager = launchManagerFactory.create(
            activity = this,
            coroutineScope = lifecycleScope,
            autoAction = intent?.getStringExtra(EXTRA_AUTO_ACTION),
            capturer = capturer,
            locationProvider = locationProvider,
            requestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            finish = { finish() },
        )
        launchManager.start()
    }

    override fun onDestroy() {
        // Symmetric teardown: release the camera device and the S0766 location listener the manager
        // warmed. Idempotent, so an early destroy (before capture) is safe.
        capturer.release()
        locationProvider.stop()
        super.onDestroy()
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
