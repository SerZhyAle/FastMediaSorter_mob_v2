package com.sza.fastmediasorter.vr

import android.content.Intent
import android.os.Bundle
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import com.sza.fastmediasorter.vr.openxr.OpenXrSessionManager
import com.sza.fastmediasorter.vr.playback.VrPlaybackEngine
import com.sza.fastmediasorter.vr.render.VrStereoRenderer
import com.sza.fastmediasorter.vr.ui.VrControlOverlayManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * VR player host — extends PlayerActivity to inherit all controls,
 * file operations and playback logic. Adds OpenXR rendering layer
 * via OpenXrSessionManager + VrStereoRenderer.
 *
 * On a phone (no XR runtime), falls back to VrPhoneFallbackActivity.
 * This is a thin coordinator — no business logic directly in the Activity.
 */
@AndroidEntryPoint
class VrPlayerActivity : PlayerActivity() {

    @Inject
    lateinit var vrPlaybackEngine: VrPlaybackEngine

    private var xrSessionManager: OpenXrSessionManager? = null
    private var stereoRenderer: VrStereoRenderer? = null
    private var controlOverlay: VrControlOverlayManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Check XR runtime BEFORE super.onCreate() to avoid PlayerActivity's
        // full initialisation (binding, ExoPlayer, file list) on non-headset devices.
        if (!isXrRuntimeAvailable()) {
            // Must call AppCompatActivity.onCreate at minimum for Activity lifecycle
            super.onCreate(savedInstanceState)
            Timber.w("VrPlayerActivity: no XR runtime, falling back to phone screen")
            startActivity(Intent(this, VrPhoneFallbackActivity::class.java))
            finish()
            return
        }

        super.onCreate(savedInstanceState)

        // Initialise VR-specific components
        xrSessionManager = OpenXrSessionManager()
        stereoRenderer = VrStereoRenderer()
        controlOverlay = VrControlOverlayManager { command ->
            handleVrCommand(command)
        }

        Timber.d("VrPlayerActivity: VR components initialised, XR session will start in onResume")
    }

    override fun onDestroy() {
        stereoRenderer?.release()
        xrSessionManager?.release()
        super.onDestroy()
    }

    /**
     * Dispatch a command from the VR overlay to the inherited player.
     */
    private fun handleVrCommand(command: PlaybackCommand) {
        Timber.d("VrPlayerActivity: handling VR command $command")
        when (command) {
            PlaybackCommand.Play -> viewModel.togglePause()
            PlaybackCommand.Pause -> viewModel.togglePause()
            PlaybackCommand.SeekForward -> videoPlayerManager.seekForward(VR_SEEK_SECONDS)
            PlaybackCommand.SeekBackward -> videoPlayerManager.seekBackward(VR_SEEK_SECONDS)
            PlaybackCommand.NextFile -> viewModel.nextFile()
            PlaybackCommand.PreviousFile -> viewModel.previousFile()
            PlaybackCommand.Exit -> finish()
            else -> Timber.w("VrPlayerActivity: unhandled VR command $command")
        }
    }

    /**
     * Check if an OpenXR runtime is available on this device.
     * On phones this will return false; on Quest headsets it returns true.
     * Result is cached — System.loadLibrary is idempotent but the check only needs to run once.
     */
    private fun isXrRuntimeAvailable(): Boolean = xrAvailable

    companion object {
        /** Seek increment for VR controller seek commands (seconds). */
        private const val VR_SEEK_SECONDS = 15

        /** Cached XR runtime probe — avoids repeated loadLibrary calls. */
        private val xrAvailable: Boolean by lazy {
            try {
                System.loadLibrary("openxr_loader")
                true
            } catch (_: UnsatisfiedLinkError) {
                Timber.d("VrPlayerActivity: OpenXR loader not available")
                false
            }
        }
    }
}
