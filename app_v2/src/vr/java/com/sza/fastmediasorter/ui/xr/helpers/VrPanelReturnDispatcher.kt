package com.sza.fastmediasorter.ui.xr.helpers

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.core.xr.VrLaunchDeliveryMode
import com.sza.fastmediasorter.core.xr.VrLaunchInput
import com.sza.fastmediasorter.core.xr.VrLaunchPayloadHolder
import com.sza.fastmediasorter.core.xr.VrLaunchResult
import com.sza.fastmediasorter.core.xr.VrPanelReturnTarget
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * S0989: the immersive exit/return playbook, extracted from DiagnosticXrActivity. Branches on
 * [VrLaunchInput.deliveryMode] between the ACTIVITY_RESULT hand-back and the legacy Home +
 * PendingIntent panel restore, builds the return intent, and keeps the flat-panel handoff
 * idempotent.
 *
 * @param launchInputProvider nullable so the onCreate preflight-failure path (which runs before the
 * host assigns `launchInput`) falls back to [VrLaunchDeliveryMode.LEGACY_PANEL_RETURN], matching the
 * original `runCatching { launchInput.deliveryMode }` behaviour.
 */
class VrPanelReturnDispatcher(
    private val activity: ComponentActivity,
    private val payloadHolder: VrLaunchPayloadHolder,
    private val returnTargetProvider: () -> VrPanelReturnTarget,
    private val launchInputProvider: () -> VrLaunchInput?,
    private val playerProvider: () -> ExoPlayer?,
) {

    // Quest Home may redispatch the panel PendingIntent during immersive teardown. Keep the
    // flat-panel handoff idempotent so one exit cannot spawn multiple SettingsActivity instances.
    private val panelReturnDispatched = AtomicBoolean(false)

    fun deliverReturnAndFinish(result: VrLaunchResult) {
        val deliveryMode = launchInputProvider()?.deliveryMode ?: VrLaunchDeliveryMode.LEGACY_PANEL_RETURN
        when (deliveryMode) {
            VrLaunchDeliveryMode.ACTIVITY_RESULT -> deliverViaActivityResult(result)
            VrLaunchDeliveryMode.LEGACY_PANEL_RETURN -> returnToSettingsTaskOrFinish(result)
        }
    }

    private fun deliverViaActivityResult(result: VrLaunchResult) {
        val data = Intent().apply { putExtra(VrLaunchInput.EXTRA_LAUNCH_RESULT_TOKEN, payloadHolder.put(result)) }
        activity.setResult(Activity.RESULT_OK, data)
        Timber.d("DiagnosticXrActivity: deliverViaActivityResult result=$result")
        activity.finish()
    }

    /**
     * Return to Home and ask HorizonOS to foreground the canonical panel task. Recent Quest logs show
     * HorizonOS restoring this app as a launcher-rooted panel task whose base activity is MainActivity
     * even when SettingsActivity is on top. Relaunching SettingsActivity directly competes with that
     * restore path and duplicates the panel surface.
     */
    private fun returnToSettingsTaskOrFinish(result: VrLaunchResult) {
        if (!panelReturnDispatched.compareAndSet(false, true)) {
            Timber.d("DiagnosticXrActivity: panel return already dispatched")
            return
        }
        val returnedToFlatTask = launchPanelHostInHome(result) || launchPanelHostFallback(result)
        Timber.d("DiagnosticXrActivity: returnToSettingsTaskOrFinish -> $returnedToFlatTask result=$result")
        if (!returnedToFlatTask) {
            panelReturnDispatched.set(false)
            activity.finish()
            return
        }
        scheduleHostFinish()
    }

    private fun launchPanelHostInHome(result: VrLaunchResult): Boolean {
        val context = activity.applicationContext
        return runCatching {
            val panelIntent = buildReturnIntent(context, result)
            val pendingPanelIntent = PendingIntent.getActivity(
                context,
                0,
                panelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val homeIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_LAUNCH_IN_HOME_PENDING_INTENT, pendingPanelIntent)
            activity.startActivity(homeIntent)
            true
        }.getOrElse {
            Timber.w(it, "DiagnosticXrActivity: failed to return to launcher task through Home")
            false
        }
    }

    private fun launchPanelHostFallback(result: VrLaunchResult): Boolean {
        val intent = buildReturnIntent(activity, result)
        return runCatching {
            activity.startActivity(intent)
            true
        }.getOrElse {
            Timber.w(it, "DiagnosticXrActivity: failed to return to launcher task")
            false
        }
    }

    private fun buildPlayerReturnTarget(target: VrPanelReturnTarget.Player): VrPanelReturnTarget.Player {
        val player = playerProvider()
        val returnSnapshot = if (player != null) {
            target.snapshot.copy(
                videoPositionMs = player.currentPosition,
                videoPlaybackSpeed = player.playbackParameters.speed,
                videoIsPlaying = player.isPlaying,
                videoVolume = player.volume
            )
        } else {
            target.snapshot
        }
        return target.copy(snapshot = returnSnapshot)
    }

    private fun buildReturnIntent(context: Context, result: VrLaunchResult): Intent {
        return when (val target = returnTargetProvider()) {
            is VrPanelReturnTarget.Player -> {
                val updatedTarget = buildPlayerReturnTarget(target)
                val detectedStereoMode = updatedTarget.detectedStereoModeName
                    ?.let { modeName -> runCatching { StereoMode.valueOf(modeName) }.getOrNull() }
                PlayerActivity.createPanelIntent(
                    context = context,
                    resourceId = updatedTarget.resourceId,
                    initialIndex = updatedTarget.playlistIndex,
                    skipAvailabilityCheck = false,
                    initialFilePath = updatedTarget.sourceFilePath,
                    isPlaying = updatedTarget.snapshot.videoIsPlaying,
                    isSlideshowEnabled = updatedTarget.resumeSlideshowEnabled,
                    detectedStereoMode = detectedStereoMode,
                    windowId = updatedTarget.windowId,
                ).apply {
                    putExtra(VrLaunchInput.EXTRA_LAUNCH_RESULT_TOKEN, payloadHolder.put(result))
                    putExtra(VrLaunchInput.EXTRA_RETURN_TARGET_TOKEN, payloadHolder.put(updatedTarget))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            }
            is VrPanelReturnTarget.Settings -> MainActivity.createReturnToSettingsIntent(
                context,
                target.initialTab,
            ).apply {
                putExtra(VrLaunchInput.EXTRA_LAUNCH_RESULT_TOKEN, payloadHolder.put(result))
            }
        }
    }

    private fun scheduleHostFinish() {
        // Meta's handoff sample stops at HOME + PendingIntent. Force-removing the XR task here made
        // HorizonOS spin up FocusPlaceholderActivity and re-dispatch the panel launch, which recreated
        // Settings and prevented a clean second immersive entry.
        activity.window.decorView.post {
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_LAUNCH_IN_HOME_PENDING_INTENT = "extra_launch_in_home_pending_intent"
    }
}
