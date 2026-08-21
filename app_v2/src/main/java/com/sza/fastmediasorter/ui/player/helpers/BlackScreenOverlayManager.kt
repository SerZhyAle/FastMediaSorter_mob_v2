package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.graphics.Color
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
        Timber.d("BlackScreenOverlayManager: overlay shown (fullscreen=true, wasFullscreen=$wasFullscreenBeforeOverlay)")
    }

    fun hide() {
        if (!isVisible) return
        val activity = activityRef.get() ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return
        overlayView?.let { decorView.removeView(it) }
        overlayView = null
        isVisible = false
        if (!wasFullscreenBeforeOverlay) {
            systemBarsManager.exitFullscreenMode()
        }
        Timber.d("BlackScreenOverlayManager: overlay hidden (restoredFullscreen=$wasFullscreenBeforeOverlay)")
    }

    fun onFileTypeChanged(isAudioOrVideo: Boolean) {
        if (!isAudioOrVideo && isVisible) hide()
    }
}
