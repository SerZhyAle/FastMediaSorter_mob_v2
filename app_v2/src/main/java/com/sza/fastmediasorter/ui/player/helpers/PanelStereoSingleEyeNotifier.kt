package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.sza.fastmediasorter.R
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Session-scoped notifier that shows a single localized toast the first time the panel
 * single-eye crop is applied to a video frame or full-screen image. Subsequent crop
 * applications in the same session are silent.
 *
 * Session boundary is defined by [resetForNewSession] (called by [com.sza.fastmediasorter.ui.player.VideoPlayerManager]
 * when a new media file starts loading). Image-side callers do not reset the flag — they
 * share the same one-shot semantics as video.
 *
 * Lifecycle: held by PlayerActivity / StandalonePlayerActivity for the duration of the
 * activity (no Hilt scope wiring; manual instantiation in the activity matches the
 * existing helper pattern).
 *
 * See spec_panel-stereo-single-eye.
 */
class PanelStereoSingleEyeNotifier {

    private val toasted = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Show the localized toast if this is the first crop application in the current session.
     * Idempotent — additional calls in the same session are silent.
     */
    fun notifyIfFirstThisSession(context: Context) {
        if (toasted.compareAndSet(false, true)) {
            Timber.d("PanelStereoSingleEyeNotifier: first crop application this session — showing toast")
            Timber.d("VR_AUDIT/11: panel single-eye crop applied (first-this-session toast shown)")
            val appContext = context.applicationContext
            mainHandler.post {
                Toast.makeText(
                    appContext,
                    R.string.toast_panel_stereo_single_eye_applied,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Reset the one-shot flag at the start of a new media session so the toast can fire
     * again for the next stereo file.
     */
    fun resetForNewSession() {
        if (toasted.getAndSet(false)) {
            Timber.d("PanelStereoSingleEyeNotifier: session reset — toast will fire on next crop application")
        }
    }
}
