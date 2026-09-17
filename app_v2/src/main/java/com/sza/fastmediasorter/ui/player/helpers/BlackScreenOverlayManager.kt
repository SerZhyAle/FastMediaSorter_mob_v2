package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.sza.fastmediasorter.ui.common.widget.DimOverlayView
import timber.log.Timber
import java.lang.ref.WeakReference

class BlackScreenOverlayManager(
    private val activityRef: WeakReference<Activity>,
    private val systemBarsManager: SystemBarsManager
) {

    var isVisible: Boolean = false
        private set

    /**
     * S2667: true for the whole episode in which this overlay owns the window's system-bar insets -
     * from before the bars are hidden until after they are handed back. A host whose layout is
     * expensive to re-pad reads this to sit the episode out; [isVisible] cannot serve that purpose,
     * because [hide] clears it before restoring the bars and the restore is the costlier half.
     */
    var isChangingSystemBars: Boolean = false
        private set

    private var overlayView: View? = null
    private var wasFullscreenBeforeOverlay = false

    fun show() {
        if (isVisible) return
        val activity = activityRef.get() ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return
        wasFullscreenBeforeOverlay = systemBarsManager.isInFullscreenMode()
        isChangingSystemBars = true
        systemBarsManager.enterFullscreenMode()
        val view = DimOverlayView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onExit = { hide() }
            setOnKeyListener { _, _, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE)
                ) {
                    hide()
                    true
                } else {
                    false
                }
            }
            requestFocus()
        }
        decorView.addView(view)
        overlayView = view
        isVisible = true
        setButtonBacklight(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
        Timber.d(
            "BlackScreenOverlayManager: overlay shown (fullscreen=true, wasFullscreen=$wasFullscreenBeforeOverlay)"
        )
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val view = overlayView as? DimOverlayView
        return if (isVisible && view != null) {
            view.dispatchTouchEvent(event)
            true
        } else {
            false
        }
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
        isChangingSystemBars = false
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
