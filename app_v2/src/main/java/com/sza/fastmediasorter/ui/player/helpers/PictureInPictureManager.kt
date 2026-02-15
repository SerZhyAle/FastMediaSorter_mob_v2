package com.sza.fastmediasorter.ui.player.helpers

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * Manages Picture-in-Picture mode for PlayerActivity.
 *
 * Scope: Android 12+ (API 31+) only.
 * - Auto-enter PiP on home button press (when video playing)
 * - PiP button in custom controls (hidden on API < 31)
 * - Remote actions: play/pause
 * - UI adjustments when entering/exiting PiP
 */
class PictureInPictureManager(
    private val activity: android.app.Activity,
    private val binding: ActivityPlayerUnifiedBinding,
    private val videoPlayerManager: VideoPlayerManager,
    private val isVideoPlaying: () -> Boolean
) {
    private var pipReceiver: BroadcastReceiver? = null

    /** Whether PiP is supported on this device (API 31+) */
    val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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
     * Call once during initialization.
     */
    fun setupPipButton(enablePip: Boolean) {
        val pipButton = binding.playerView.findViewById<ImageButton>(R.id.btnPictureInPicture)
        if (!isSupported || !enablePip) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            enterPipApi31()
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
     * Handle PiP mode change — update UI visibility.
     */
    fun onPictureInPictureModeChanged(isInPipMode: Boolean) {
        Timber.d("PiPManager: mode changed, isInPip=$isInPipMode")

        // Hide/show UI elements
        binding.toolbar.isVisible = !isInPipMode
        binding.topCommandPanel.isVisible = !isInPipMode

        if (isInPipMode) {
            // Hide ExoPlayer controller in PiP (controlled via remote actions)
            binding.playerView.useController = false
            registerPipReceiver()
        } else {
            binding.playerView.useController = true
            unregisterPipReceiver()
        }
    }

    /**
     * Update PiP params when playback state changes (play/pause actions update).
     */
    fun updatePipActions() {
        if (!isSupported) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun enterPipApi31() {
        try {
            val params = buildPipParams()
            activity.enterPictureInPictureMode(params)
            Timber.d("PiPManager: entered PiP mode")
        } catch (e: Exception) {
            Timber.e(e, "PiPManager: failed to enter PiP")
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun buildPipParams(): PictureInPictureParams {
        val isPlaying = videoPlayerManager.getPlayer()?.isPlaying == true

        val playAction = createRemoteAction(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) activity.getString(R.string.pip_pause) else activity.getString(R.string.pip_play),
            if (isPlaying) CONTROL_PAUSE else CONTROL_PLAY,
            if (isPlaying) REQUEST_PAUSE else REQUEST_PLAY
        )

        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(playAction))
            .setAutoEnterEnabled(true)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createRemoteAction(
        iconRes: Int,
        title: String,
        controlType: Int,
        requestCode: Int
    ): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL).apply {
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
                        videoPlayerManager.play()
                        updatePipActions()
                        Timber.d("PiPManager: remote action PLAY")
                    }
                    CONTROL_PAUSE -> {
                        videoPlayerManager.pause()
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
