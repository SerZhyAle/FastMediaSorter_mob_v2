package com.sza.fastmediasorter.core.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.ui.cameracapture.CameraCaptureContract
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode
import timber.log.Timber
import java.io.File

/**
 * S1986: opens the camera host in a named mode, so a host-side sweep can reach the plain photo entry
 * and video mode - both named in the owner's report, and both unreachable from a script because every
 * camera activity is `android:exported="false"` and `am start` refuses a non-exported component.
 *
 * Declared in the debug manifest rather than registered at runtime. It has to outlive every screen -
 * the camera screen cannot register the receiver that is meant to open it - and a receiver that lives
 * as long as the process has no symmetric lifecycle edge to unregister on, which is exactly the shape
 * a manifest entry expresses and a dynamic registration cannot.
 *
 * Debug builds only: this file and its manifest entry live in `src/debug`, so a release build carries
 * neither.
 *
 * Usage (the app must already be in the foreground - Android refuses a background activity launch):
 *   adb shell am broadcast -a com.sza.fastmediasorter.debug.CAMERA_TEST_OPEN --es mode VIDEO \
 *     -p com.sza.fastmediasorter.debug
 */
class CameraTestOpenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val application = context?.applicationContext ?: return
        val mode = CameraCaptureMode.fromName(intent?.getStringExtra(EXTRA_MODE)?.uppercase())
        val dir = File(application.getExternalFilesDir(null), TEST_OUTPUT_DIR)
        dir.mkdirs()
        Timber.i("CameraTestOpenReceiver: opening the camera host in $mode")
        // The same intent the general "Camera" entry builds, so this measures the shipped route
        // rather than a second one invented for the test.
        val open = CameraCaptureContract.createSwitchableIntent(
            context = application,
            outputDir = dir.absolutePath,
            outputBaseName = TEST_BASENAME,
            initialMode = mode,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { application.startActivity(open) }
            .onSuccess {
                // Ordered because `am broadcast` sends it that way, and the distinctive code is the
                // caller's only proof that a receiver ran at all - `am broadcast` prints
                // "result=0" whether one did or not.
                if (isOrderedBroadcast) resultCode = CameraTestHooks.ACK_APPLIED
            }
            .onFailure { Timber.w(it, "CameraTestOpenReceiver: could not open the camera host") }
    }

    private companion object {
        const val EXTRA_MODE = "mode"
        const val TEST_OUTPUT_DIR = "camera_test"
        const val TEST_BASENAME = "CAMTEST"
    }
}
