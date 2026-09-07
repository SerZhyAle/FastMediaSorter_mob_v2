package com.sza.fastmediasorter.ui.main.helpers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.ui.broadcast.BroadcastShareActivity
import com.sza.fastmediasorter.util.RecordingElapsedTimer
import com.sza.fastmediasorter.utils.collectOnLifecycle
import timber.log.Timber

class MainBroadcastManager(
    private val activity: FragmentActivity,
    private val controller: BroadcastSourceController,
    private val requestRecordAudioPermission: () -> Unit,
    private val requestPostNotificationsPermission: () -> Unit,
) {

    private val indicator = RecordingIndicatorOverlayManager(activity)
    private val recordingElapsedTimer = RecordingElapsedTimer(
        onTick = indicator::updateTimer,
    )
    private var indicatorShown = false

    fun bind(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.collectOnLifecycle(controller.state) { state ->
            when (state) {
                is BroadcastState.Live -> {
                    showIndicator(state)
                    BroadcastShareActivity.launchIfNew(
                        activity,
                        state.descriptor.url,
                        state.descriptor.title,
                        state.descriptor.mode
                    )
                }
                else -> {
                    dismissIndicator()
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
                BroadcastShareActivity.launchIfNew(
                    activity,
                    state.descriptor.url,
                    state.descriptor.title,
                    state.descriptor.mode
                )
            }
        )
    }

    private fun dismissIndicator() {
        if (indicatorShown) {
            indicatorShown = false
            recordingElapsedTimer.stop()
            indicator.dismiss()
            BroadcastShareActivity.resetLaunchTracking()
        }
    }

    @Suppress("ReturnCount")
    fun startBroadcast() {
        if (!controller.isAvailable) return

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestRecordAudioPermission()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPostNotificationsPermission()
            return
        }

        Timber.d("S2508: starting live audio broadcast")
        controller.start(BroadcastMode.AUDIO_ONLY)
    }

    fun stopBroadcast() {
        controller.stop()
    }
}
