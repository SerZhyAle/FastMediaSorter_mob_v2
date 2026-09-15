package com.sza.fastmediasorter.ui.broadcast.helpers

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.ui.player.helpers.SystemBarsManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3154: the "screen off" action of the broadcast control screen has two outcomes.
 *
 * A build that declares the camera foreground-service type keeps a video session alive off-screen, so the
 * action really lets the display sleep. A store build declares no such type, so the camera dies the moment
 * the app leaves the screen; there the action draws a black overlay over the app instead - the same
 * experience as the Black Screen sub-program - while the display stays on and the session keeps streaming.
 */
@Singleton
class BroadcastBlankScreenManager @Inject constructor(
    private val controller: BroadcastSourceController,
) {
    private var blanked = false
    private var hostRoot: View? = null
    private var overlay: View? = null
    private var systemBarsManager: SystemBarsManager? = null
    private var backCallback: OnBackPressedCallback? = null

    /** True while the current session must blank rather than sleep, which is also what labels the button. */
    fun blanksInsteadOfSleeping(): Boolean {
        val live = controller.state.value as? BroadcastState.Live ?: return false
        return live.descriptor.mode != BroadcastMode.AUDIO_ONLY.name && !controller.cameraSurvivesBackground
    }

    /** Re-applies the overlay after a configuration change, which destroys and recreates the activity. */
    fun attach(activity: AppCompatActivity, screenRoot: View) {
        hostRoot = screenRoot
        val callback = object : OnBackPressedCallback(blanked) {
            override fun handleOnBackPressed() {
                hide(activity)
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, callback)
        backCallback = callback
        if (blanked) {
            show(activity)
        }
    }

    fun onScreenOffRequested(activity: AppCompatActivity) {
        if (blanksInsteadOfSleeping()) {
            blanked = true
            show(activity)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun detach() {
        hostRoot = null
        overlay = null
        systemBarsManager = null
        backCallback = null
    }

    private fun show(activity: AppCompatActivity) {
        if (overlay != null) return
        val content = hostRoot?.parent as? ViewGroup ?: return
        // Added above the screen's root rather than inside either orientation layout: the overlay must cover
        // the inset padding those layouts apply, and both orientations then need no duplicate view.
        val view = View(activity).apply {
            setBackgroundColor(ContextCompat.getColor(activity, R.color.black))
            isClickable = true
            isFocusable = true
            contentDescription = activity.getString(R.string.broadcast_control_blank_screen_cd)
            setOnClickListener { hide(activity) }
        }
        content.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        overlay = view
        systemBarsManager = SystemBarsManager(activity).apply { enterFullscreenMode() }
        setButtonBacklight(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        backCallback?.isEnabled = true
        Timber.d("S3154: broadcast blank overlay shown")
    }

    private fun hide(activity: AppCompatActivity) {
        blanked = false
        backCallback?.isEnabled = false
        overlay?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        overlay = null
        systemBarsManager?.exitFullscreenMode()
        systemBarsManager = null
        setButtonBacklight(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    private fun setButtonBacklight(activity: AppCompatActivity, value: Float) {
        activity.window.attributes = activity.window.attributes.apply { buttonBrightness = value }
    }
}
