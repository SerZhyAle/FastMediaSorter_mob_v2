package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import timber.log.Timber
import java.lang.ref.WeakReference

class BlackScreenOverlayManager(private val activityRef: WeakReference<Activity>) {

    var isVisible: Boolean = false
        private set

    private var overlayView: View? = null

    fun show() {
        if (isVisible) return
        val activity = activityRef.get() ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return
        val view = View(activity).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            fitsSystemWindows = false
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) hide()
                true
            }
        }
        decorView.addView(view)
        overlayView = view
        isVisible = true
        Timber.d("BlackScreenOverlayManager: overlay shown")
    }

    fun hide() {
        if (!isVisible) return
        val activity = activityRef.get() ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return
        overlayView?.let { decorView.removeView(it) }
        overlayView = null
        isVisible = false
        Timber.d("BlackScreenOverlayManager: overlay hidden")
    }

    fun onFileTypeChanged(isAudioOrVideo: Boolean) {
        if (!isAudioOrVideo && isVisible) hide()
    }
}
