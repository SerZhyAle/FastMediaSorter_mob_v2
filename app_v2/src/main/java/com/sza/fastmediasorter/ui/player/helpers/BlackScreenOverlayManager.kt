package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.graphics.Color
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import timber.log.Timber
import java.lang.ref.WeakReference

class BlackScreenOverlayManager(
    private val activityRef: WeakReference<Activity>,
    private val systemBarsManager: SystemBarsManager
) {

    var isVisible: Boolean = false
        private set

    private var overlayView: View? = null
    private var wasFullscreenBeforeOverlay = false

    fun show() {
        if (isVisible) return
        val activity = activityRef.get() ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return
        wasFullscreenBeforeOverlay = systemBarsManager.isInFullscreenMode()
        systemBarsManager.enterFullscreenMode()
        val view = View(activity).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            fitsSystemWindows = false
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) hide()
                true
            }
            setOnKeyListener { _, _, event ->
                if (event.action == KeyEvent.ACTION_DOWN) hide()
                true
            }
            setOnGenericMotionListener { _, _ ->
                hide()
                true
            }
            requestFocus()
        }
        decorView.addView(view)
        overlayView = view
        isVisible = true
        setButtonBacklight(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
        Timber.d("BlackScreenOverlayManager: overlay shown (fullscreen=true, wasFullscreen=$wasFullscreenBeforeOverlay)")
    }

    fun hide() {
        if (!isVisible) return
        val activity = activityRef.get() ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return
        overlayView?.let { decorView.removeView(it) }
        overlayView = null
        isVisible = false
        setButtonBacklight(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        if (!wasFullscreenBeforeOverlay) {
            systemBarsManager.exitFullscreenMode()
        }
        Timber.d("BlackScreenOverlayManager: overlay hidden (restoredFullscreen=$wasFullscreenBeforeOverlay)")
    }

    fun onFileTypeChanged(isAudioOrVideo: Boolean) {
        if (!isAudioOrVideo && isVisible) hide()
    }

    /**
     * S1903: a black screen that leaves the capacitive navigation keys glowing is not dark. Only this
     * window's override is touched, never a system setting - the same boundary S1796 ADR-2 drew for
     * screen brightness, and BRIGHTNESS_OVERRIDE_NONE hands the keys back to the platform on hide.
     *
     * A device with no button backlight simply has nothing to dim, which is the "if available" the
     * request asked for - the override is harmless there rather than needing a capability check.
     */
    private fun setButtonBacklight(activity: Activity, value: Float) {
        activity.window.attributes = activity.window.attributes.apply { buttonBrightness = value }
    }
}
