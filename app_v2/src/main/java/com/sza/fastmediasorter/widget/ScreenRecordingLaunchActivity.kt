package com.sza.fastmediasorter.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.core.screencapture.ScreenRecordingStateController
import com.sza.fastmediasorter.core.screencapture.ScreenVideoRecordingController
import com.sza.fastmediasorter.domain.model.PermissionTask
import com.sza.fastmediasorter.ui.common.permissions.permissionRationaleShort
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S0797 - transparent, no-UI trampoline that toggles screen video recording for the edge-gesture
 * action. Owns the RECORD_AUDIO + POST_NOTIFICATIONS launchers and the FragmentActivity host that
 * [ScreenVideoRecordingController.launch] requires (the gesture dispatcher runs in a Service and has
 * neither). Mirrors [CameraLaunchActivity]'s trampoline recipe (Rule 3 - no business logic here).
 *
 * The bound controller set is empty on flavors without the capture engine, so this trampoline simply
 * finishes there; the picker already hides the action in that case.
 */
@AndroidEntryPoint
class ScreenRecordingLaunchActivity : AppCompatActivity() {

    @Inject
    lateinit var controllers: Set<@JvmSuppressWildcards ScreenVideoRecordingController>

    @Inject
    lateinit var stateController: ScreenRecordingStateController

    private val recordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startOrLaunch() else denyAndFinish(Manifest.permission.RECORD_AUDIO)
    }

    private val postNotificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startOrLaunch() else denyAndFinish(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = controllers.firstOrNull()
        if (controller == null) {
            finish()
            return
        }
        // Toggle: a second gesture while recording stops the active session (Context-only, no consent).
        if (stateController.isRecording.value) {
            controller.requestStop(this)
            finish()
            return
        }
        startOrLaunch()
    }

    /** Re-entrant: called again from each permission callback until all grants are in, then launches. */
    private fun startOrLaunch() {
        val controller = controllers.firstOrNull()
        when {
            controller == null -> finish()
            !hasPermission(Manifest.permission.RECORD_AUDIO) ->
                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            needsPostNotificationsPermission() ->
                postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> {
                controller.launch(this)
                finish()
            }
        }
    }

    private fun needsPostNotificationsPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    // S1436: which of the two permissions was refused is now what the message says, in the registry's words.
    private fun denyAndFinish(permission: String) {
        val message = permissionRationaleShort(permission, PermissionTask.SCREEN_RECORDING)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
