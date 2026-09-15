package com.sza.fastmediasorter.ui.main.helpers

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastFailure
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.broadcast.BroadcastControlActivity
import com.sza.fastmediasorter.ui.broadcast.BroadcastShareActivity
import com.sza.fastmediasorter.util.RecordingElapsedTimer
import com.sza.fastmediasorter.utils.collectOnLifecycle
import timber.log.Timber

class MainBroadcastManager(
    private val activity: FragmentActivity,
    private val controller: BroadcastSourceController,
    private val settingsRepository: SettingsRepository,
    private val requestRecordAudioPermission: () -> Unit,
    private val requestPostNotificationsPermission: () -> Unit,
    private val requestCameraPermission: () -> Unit = {},
) {

    private val indicator = RecordingIndicatorOverlayManager(activity)
    private var liveStartedAtElapsedRealtimeMs: Long? = null
    private var pendingMode: BroadcastMode = BroadcastMode.AUDIO_ONLY
    private var pendingLensId: String? = null

    // The session lives in the service and outlives this screen, so the indicator counts from the
    // session's own start moment. A screen-local accumulator restarted at zero on every return to
    // the main screen, because leaving it stops the timer and coming back starts a fresh one.
    private val recordingElapsedTimer = RecordingElapsedTimer(
        elapsedTimeSourceMs = {
            liveStartedAtElapsedRealtimeMs?.let { SystemClock.elapsedRealtime() - it } ?: 0L
        },
        onTick = indicator::updateTimer,
    )
    private var indicatorShown = false
    private var autoOpenShare = true

    fun bind(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            autoOpenShare = settings.broadcastAutoOpenShare
        }
        lifecycleOwner.collectOnLifecycle(controller.state) { state ->
            when (state) {
                is BroadcastState.Live -> {
                    showIndicator(state)
                    if (autoOpenShare) {
                        BroadcastControlActivity.launch(activity)
                    }
                }
                is BroadcastState.Failed -> {
                    endBroadcastSession()
                    showFailure(state)
                }
                BroadcastState.Idle -> {
                    endBroadcastSession()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                dismissIndicator()
            }
        })
    }

    private fun showIndicator(state: BroadcastState.Live) {
        liveStartedAtElapsedRealtimeMs = state.startedAtElapsedRealtimeMs
        if (!indicatorShown) {
            indicatorShown = true
            recordingElapsedTimer.start()
        }
        indicator.show(
            accessibleLabel = activity.getString(R.string.broadcast_notification_title),
            stopCd = activity.getString(R.string.broadcast_action_stop),
            onPauseResume = null,
            onStop = { stopBroadcast() },
            onTapRoot = {
                BroadcastControlActivity.launch(activity)
            }
        )
    }

    private fun showFailure(state: BroadcastState.Failed) {
        Timber.w("Broadcast failed: %s (%s)", state.failure, state.detail)
        val messageRes = when (state.failure) {
            BroadcastFailure.MICROPHONE_PERMISSION -> R.string.broadcast_failed_microphone_permission
            BroadcastFailure.NETWORK_UNAVAILABLE -> R.string.broadcast_failed_network
            BroadcastFailure.ENCODER_UNAVAILABLE -> R.string.broadcast_failed_encoder
            BroadcastFailure.CAPTURE_ERROR -> R.string.broadcast_failed_capture
        }
        showMessage(activity.getString(messageRes), openSettingsAction = false)
        // The state is static and its service is already gone, so nothing else retires it - without this
        // a rotation would replay the same message and the next start would begin from a failed state.
        controller.acknowledgeFailure()
    }

    /**
     * A denied permission used to end in an empty callback, which is half of the "the button does
     * nothing" report: a denial the system will no longer prompt for leaves app settings as the only
     * way back, so that case gets the route; an ordinary denial only needs to say what the feature wanted.
     */
    fun onPermissionResult(permission: String, granted: Boolean) {
        if (granted) {
            startBroadcast(pendingMode, pendingLensId)
            return
        }
        Timber.w("Broadcast permission denied: %s", permission)
        val messageRes = when (permission) {
            Manifest.permission.RECORD_AUDIO -> R.string.broadcast_permission_microphone_required
            Manifest.permission.CAMERA -> R.string.broadcast_permission_camera_required
            else -> R.string.broadcast_permission_notifications_required
        }
        val permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        showMessage(activity.getString(messageRes), openSettingsAction = permanentlyDenied)
    }

    private fun showMessage(message: String, openSettingsAction: Boolean) {
        val snackbar = Snackbar.make(activity.window.decorView.rootView, message, Snackbar.LENGTH_LONG)
        if (openSettingsAction) {
            snackbar.setAction(R.string.perm_btn_open_system_settings) { openAppSettings() }
        }
        snackbar.show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }

    private fun dismissIndicator() {
        if (indicatorShown) {
            indicatorShown = false
            recordingElapsedTimer.stop()
            indicator.dismiss()
        }
    }

    /**
     * The launch tracking belongs to the broadcast session, not to the main screen's visibility.
     * Opening the descriptor screen stops the host activity, so resetting on every dismissal cleared
     * the tracking, and the re-emitted Live state on the way back re-opened the screen the user had
     * just closed - trapping them there for as long as the broadcast ran.
     */
    private fun endBroadcastSession() {
        liveStartedAtElapsedRealtimeMs = null
        dismissIndicator()
        BroadcastShareActivity.resetLaunchTracking()
    }

    @Suppress("ReturnCount")
    fun startBroadcast(mode: BroadcastMode = BroadcastMode.AUDIO_ONLY, lensId: String? = null) {
        if (!controller.isAvailable) return
        pendingMode = mode
        pendingLensId = lensId

        if (mode != BroadcastMode.VIDEO_ONLY &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestRecordAudioPermission()
            return
        }

        if (mode != BroadcastMode.AUDIO_ONLY && ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPostNotificationsPermission()
            return
        }

        controller.start(mode, lensId)
    }

    fun stopBroadcast() {
        controller.stop()
    }
}
