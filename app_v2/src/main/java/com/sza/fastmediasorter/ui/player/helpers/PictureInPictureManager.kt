package com.sza.fastmediasorter.ui.player.helpers

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * Manages Picture-in-Picture mode for PlayerActivity and StandalonePlayerActivity.
 *
 * Scope: Android 8+ (API 26+); auto-enter on leaving the activity is API 31+ only.
 * - Auto-enter PiP on home button press (when video playing)
 * - PiP button in custom controls (hidden where the platform has no PiP)
 * - Remote actions: play/pause
 * - UI adjustments when entering/exiting PiP
 */
class PictureInPictureManager(
    private val activity: android.app.Activity,
    // S0393 U1: binding-agnostic - take the player view + the chrome views to hide in PiP, so both the
    // in-app player (toolbar + command panel) and the standalone hosts (command panel only) can reuse it.
    private val playerView: androidx.media3.ui.PlayerView,
    private val chromeToHide: List<android.view.View>,
    private val getPlayer: () -> ExoPlayer?,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val isVideoPlaying: () -> Boolean
) {
    private var pipReceiver: BroadcastReceiver? = null
    private val savedVisibilities = mutableMapOf<android.view.View, Int>()

    /** Whether PiP is supported on this device (API 26+ and system feature present) */
    val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Current enabled state - updated by [setupPipButton]. */
    var isEnabled: Boolean = false
        private set

    /**
     * S2026: whether the host is currently in PiP. Read by the hosts' own overlay-visibility
     * updaters, which run on state changes and would otherwise re-show chrome this manager hid.
     * Not derived from `Activity.isInPictureInPictureMode` because that is API 24+ and the legacy
     * flavor ships minSdk 23.
     */
    var isInPipMode: Boolean = false
        private set

    companion object {
        private const val ACTION_PIP_CONTROL = "com.sza.fastmediasorter.PIP_CONTROL"
        private const val EXTRA_CONTROL_TYPE = "control_type"
        private const val CONTROL_PLAY = 1
        private const val CONTROL_PAUSE = 2
        private const val REQUEST_PLAY = 100
        private const val REQUEST_PAUSE = 101
    }

    /**
     * Setup PiP button visibility and click handler.
     * Safe to call repeatedly when settings change.
     * @param isAudio Hide the button for audio-only files (PiP has no visual content for audio).
     */
    fun setupPipButton(enablePip: Boolean, isAudio: Boolean = false) {
        isEnabled = enablePip && isSupported && !isAudio
        val pipButton = playerView.findViewById<ImageButton>(R.id.btnPictureInPicture)
        if (!isEnabled) {
            pipButton?.isVisible = false
            return
        }
        pipButton?.isVisible = true
        pipButton?.setOnClickListener {
            enterPictureInPicture()
        }
    }

    /**
     * Enter PiP mode programmatically.
     * Called from PiP button click or onUserLeaveHint.
     */
    fun enterPictureInPicture() {
        if (!isSupported) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipApi26()
        }
    }

    /**
     * Try to auto-enter PiP when user leaves activity (home button).
     * Only enters if video is currently playing and PiP is enabled.
     */
    fun onUserLeaveHint(enablePip: Boolean) {
        if (!isSupported || !enablePip) return
        if (!isVideoPlaying()) return
        enterPictureInPicture()
    }

    /**
     * Handle PiP mode change - update UI visibility.
     */
    fun onPictureInPictureModeChanged(isInPipMode: Boolean) {
        Timber.d("PiPManager: mode changed, isInPip=$isInPipMode")
        this.isInPipMode = isInPipMode

        if (isInPipMode) {
            savedVisibilities.clear()
            chromeToHide.forEach { view ->
                savedVisibilities[view] = view.visibility
                view.isVisible = false
            }
            // Hide ExoPlayer controller in PiP (controlled via remote actions)
            playerView.useController = false
            registerPipReceiver()
        } else {
            chromeToHide.forEach { view ->
                savedVisibilities[view]?.let { view.visibility = it }
            }
            savedVisibilities.clear()
            // Re-enable and explicitly show the controller - useController=true only
            // *permits* showing but does NOT auto-show after being hidden in PiP.
            playerView.useController = true
            playerView.showController()
            unregisterPipReceiver()
        }
    }

    /**
     * Update PiP params when playback state changes (play/pause actions update).
     */
    fun updatePipActions() {
        if (!isSupported) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = buildPipParams()
                activity.setPictureInPictureParams(params)
            } catch (e: Exception) {
                Timber.w(e, "PiPManager: failed to update PiP params")
            }
        }
    }

    fun release() {
        unregisterPipReceiver()
    }

    // === Private ===

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPipApi26() {
        try {
            // S2186: hide the controller BEFORE requesting the mode change, not only inside
            // onPictureInPictureModeChanged. That callback fires asynchronously after the system
            // already began the transition, so a controller left visible here gets frozen into
            // the PiP window at whatever instant ExoPlayer's un-clamped currentPosition ticked -
            // observed as a time counter/seek-bar past the clip's duration that never updates
            // again once useController=false stops the controller's refresh loop.
            playerView.useController = false
            Timber.d("S2186: controller hidden before PiP mode request")
            // Register receiver BEFORE building params so that the PendingIntents
            // inside RemoteActions are guaranteed to have an active receiver.
            registerPipReceiver()
            val params = buildPipParams()
            activity.enterPictureInPictureMode(params)
            Timber.d("PiPManager: entered PiP mode")
            Timber.d("S2026: PiP accepted by host ${activity.javaClass.simpleName}, taskId=${activity.taskId}")
        } catch (e: Exception) {
            Timber.e(e, "PiPManager: failed to enter PiP")
            // S2026: the refusal used to reach the log only, so a tap on the button looked like a dead
            // control. The platform rejects PiP for whole classes of host - a home-type task above all -
            // and the user is owed an answer either way.
            unregisterPipReceiver()
            Toast.makeText(activity, R.string.pip_enter_failed, Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(): PictureInPictureParams {
        val isPlaying = isVideoPlaying()

        val playAction = createRemoteAction(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) activity.getString(R.string.pip_pause) else activity.getString(R.string.pip_play),
            if (isPlaying) CONTROL_PAUSE else CONTROL_PLAY,
            if (isPlaying) REQUEST_PAUSE else REQUEST_PLAY
        )

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(playAction))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }

        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createRemoteAction(
        iconRes: Int,
        title: String,
        controlType: Int,
        requestCode: Int
    ): RemoteAction {
        // setPackage is required on Android 14+ (API 34+): implicit PendingIntent broadcasts
        // are blocked by the system. Without it the BroadcastReceiver never fires.
        val intent = Intent(ACTION_PIP_CONTROL).apply {
            setPackage(activity.packageName)
            putExtra(EXTRA_CONTROL_TYPE, controlType)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            activity,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(
            Icon.createWithResource(activity, iconRes),
            title,
            title,
            pendingIntent
        )
    }

    private fun registerPipReceiver() {
        if (pipReceiver != null) return
        pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val controlType = intent?.getIntExtra(EXTRA_CONTROL_TYPE, 0) ?: return
                when (controlType) {
                    CONTROL_PLAY -> {
                        onPlay()
                        updatePipActions()
                        Timber.d("PiPManager: remote action PLAY")
                    }
                    CONTROL_PAUSE -> {
                        onPause()
                        updatePipActions()
                        Timber.d("PiPManager: remote action PAUSE")
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            activity,
            pipReceiver,
            IntentFilter(ACTION_PIP_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterPipReceiver() {
        pipReceiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (e: Exception) {
                Timber.w(e, "PiPManager: receiver already unregistered")
            }
            pipReceiver = null
        }
    }
}
