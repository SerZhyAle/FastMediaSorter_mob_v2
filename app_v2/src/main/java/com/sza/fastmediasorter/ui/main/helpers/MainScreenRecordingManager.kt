package com.sza.fastmediasorter.ui.main.helpers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.screencapture.ScreenRecordingStateController
import com.sza.fastmediasorter.core.screencapture.ScreenVideoRecordingController
import com.sza.fastmediasorter.utils.collectOnLifecycle
import timber.log.Timber
import java.util.Locale

/**
 * S0774: host-neutral in-app driver for screen video recording. Checks RECORD_AUDIO and (API 33+)
 * POST_NOTIFICATIONS via host-owned launchers, then starts the consent + recording foreground service
 * through the bound [ScreenVideoRecordingController]. While [ScreenRecordingStateController.isRecording]
 * is true and the host is foreground it shows a non-modal recording card (timer + Stop) mirroring the
 * voice-recorder dialog; the card is dismissed when the host is backgrounded (the recording keeps going
 * in the foreground service, controllable from its notification) and re-shown on return.
 */
class MainScreenRecordingManager(
    private val activity: FragmentActivity,
    private val controller: ScreenVideoRecordingController?,
    private val stateController: ScreenRecordingStateController,
    private val requestRecordAudioPermission: () -> Unit,
    private val requestPostNotificationsPermission: () -> Unit,
) {

    private var recordingDialog: AlertDialog? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            val dialog = recordingDialog ?: return
            val totalSec =
                ((SystemClock.elapsedRealtime() - stateController.startedAtElapsedRealtimeMs) / 1000).toInt()
            dialog.setMessage(String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60))
            timerHandler.postDelayed(this, 1000)
        }
    }

    /** Observe recording state for the host lifecycle and manage the card. Call once from the host. */
    fun bind(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.collectOnLifecycle(stateController.isRecording) { recording ->
            if (recording) showRecordingCard() else dismissRecordingCard()
        }
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // The recording outlives the Activity in the foreground service; drop the modal card
                // while backgrounded so it is not stuck invisible, and re-show it on return.
                dismissRecordingCard()
            }
        })
    }

    fun start() {
        Timber.d("S0774: in-app screen recording start requested")
        if (controller == null) return
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            requestRecordAudioPermission()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            requestPostNotificationsPermission()
            return
        }
        controller.launch(activity)
    }

    /** Host RECORD_AUDIO launcher callback: continue the start flow on grant, surface denial otherwise. */
    fun onRecordAudioResult(granted: Boolean) {
        if (granted) start() else showPermissionDenied()
    }

    /** Host POST_NOTIFICATIONS launcher callback: the notification carries the stop control, so require it. */
    fun onPostNotificationsResult(granted: Boolean) {
        if (granted) start() else showPermissionDenied()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun showRecordingCard() {
        if (recordingDialog != null) return
        recordingDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.screen_recording_card_title)
            .setMessage("00:00")
            .setCancelable(false)
            .setPositiveButton(R.string.screen_recording_stop) { _, _ -> controller?.requestStop(activity) }
            .show()
        timerHandler.post(timerTick)
    }

    private fun dismissRecordingCard() {
        timerHandler.removeCallbacks(timerTick)
        recordingDialog?.dismiss()
        recordingDialog = null
    }

    private fun showPermissionDenied() {
        Snackbar.make(
            activity.window.decorView.rootView,
            R.string.screen_recording_permission_denied,
            Snackbar.LENGTH_LONG
        ).show()
    }
}
