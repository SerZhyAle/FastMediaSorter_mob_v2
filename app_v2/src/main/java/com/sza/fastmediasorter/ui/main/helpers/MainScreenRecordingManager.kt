package com.sza.fastmediasorter.ui.main.helpers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.screencapture.ScreenRecordingStateController
import com.sza.fastmediasorter.core.screencapture.ScreenVideoRecordingController
import com.sza.fastmediasorter.domain.model.PermissionTask
import com.sza.fastmediasorter.ui.common.permissions.permissionRationale
import com.sza.fastmediasorter.util.RecordingElapsedTimer
import com.sza.fastmediasorter.utils.collectOnLifecycle
import timber.log.Timber

/**
 * S0774: host-neutral in-app driver for screen video recording. Checks RECORD_AUDIO and (API 33+)
 * POST_NOTIFICATIONS via host-owned launchers, then starts the consent + recording foreground service
 * through the bound [ScreenVideoRecordingController]. While [ScreenRecordingStateController.isRecording]
 * is true and the host is foreground it shows the shared, non-modal [RecordingIndicatorOverlayManager]
 * (timer + pause/resume + stop) - also used by [MainVoiceCaptureManager] for a consistent UX between
 * both programs-panel recording scenarios. The indicator is dismissed when the host is backgrounded
 * (the recording keeps going in the foreground service, controllable from its notification) and
 * re-shown on return.
 */
class MainScreenRecordingManager(
    private val activity: FragmentActivity,
    private val controller: ScreenVideoRecordingController?,
    private val stateController: ScreenRecordingStateController,
    private val requestRecordAudioPermission: () -> Unit,
    private val requestPostNotificationsPermission: () -> Unit,
) {

    private val indicator = RecordingIndicatorOverlayManager(activity)
    private val recordingElapsedTimer = RecordingElapsedTimer(
        onTick = indicator::updateTimer,
        elapsedTimeSourceMs = stateController::elapsedMs,
    )
    private var indicatorShown = false

    /** Observe recording state for the host lifecycle and manage the indicator. Call once from the host. */
    fun bind(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.collectOnLifecycle(stateController.isRecording) { recording ->
            if (recording) showRecordingIndicator() else dismissRecordingIndicator()
        }
        lifecycleOwner.collectOnLifecycle(stateController.isPaused) { paused ->
            if (indicatorShown) applyPausedState(paused)
        }
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // The recording outlives the Activity in the foreground service; drop the indicator
                // while backgrounded so it is not stuck invisible, and re-show it on return.
                dismissRecordingIndicator()
            }
        })
    }

    // Guard-clause early returns (no controller / missing RECORD_AUDIO / missing POST_NOTIFICATIONS) -
    // clearer than nesting the launch call under successive checks.
    @Suppress("ReturnCount")
    fun start() {
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
        if (granted) start() else showPermissionDenied(Manifest.permission.RECORD_AUDIO)
    }

    /** Host POST_NOTIFICATIONS launcher callback: the notification carries the stop control, so require it. */
    fun onPostNotificationsResult(granted: Boolean) {
        if (granted) start() else showPermissionDenied(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun showRecordingIndicator() {
        if (indicatorShown) return
        indicatorShown = true
        indicator.show(
            accessibleLabel = activity.getString(R.string.screen_recording_card_title),
            stopCd = activity.getString(R.string.screen_recording_stop),
            onPauseResume = {
                if (stateController.isPaused.value) {
                    controller?.requestResume(activity)
                } else {
                    controller?.requestPause(activity)
                }
            },
            onStop = { controller?.requestStop(activity) },
        )
        recordingElapsedTimer.start()
        applyPausedState(stateController.isPaused.value)
    }

    private fun dismissRecordingIndicator() {
        if (!indicatorShown) return
        indicatorShown = false
        recordingElapsedTimer.stop()
        indicator.dismiss()
    }

    private fun applyPausedState(paused: Boolean) {
        if (paused) {
            recordingElapsedTimer.pause()
        } else {
            recordingElapsedTimer.resume()
        }
        indicator.setPaused(
            paused,
            pauseCd = activity.getString(R.string.recording_pause),
            resumeCd = activity.getString(R.string.recording_resume),
        )
    }

    // S1436: one message used to name the microphone and notifications together, so a denial never said
    // which of the two was refused. Each denial now explains its own permission, in the registry's words.
    private fun showPermissionDenied(permission: String) {
        Snackbar.make(
            activity.window.decorView.rootView,
            activity.permissionRationale(permission, PermissionTask.SCREEN_RECORDING),
            Snackbar.LENGTH_LONG
        ).show()
    }
}
