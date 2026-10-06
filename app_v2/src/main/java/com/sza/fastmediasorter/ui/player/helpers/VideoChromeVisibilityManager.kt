package com.sza.fastmediasorter.ui.player.helpers

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.media3.ui.PlayerView

/** Keeps timed chrome presentation separate from the host's fullscreen mode. */
class VideoChromeVisibilityManager(
    private val playerView: PlayerView,
    private val commandPanel: ViewGroup
) : View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
    private val enabledStates = mutableMapOf<View, Boolean>()
    private var hidden = false
    private var originalAlpha = commandPanel.alpha
    private var originalAccessibility = commandPanel.importantForAccessibility
    private var originalFocusability = commandPanel.descendantFocusability
    private var observer: ViewTreeObserver? = null
    private var keyHost: View? = null
    private val keyListener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
        if (hidden && event.action == KeyEvent.ACTION_DOWN) playerView.showController()
        false
    }

    init {
        commandPanel.setOnTouchListener { view, event ->
            if (!hidden) return@setOnTouchListener false
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                view.performClick()
                playerView.showController()
            }
            true
        }
        playerView.addOnAttachStateChangeListener(this)
        if (playerView.isAttachedToWindow) onViewAttachedToWindow(playerView)
    }

    override fun onViewAttachedToWindow(view: View) {
        observer = playerView.viewTreeObserver.also { it.addOnPreDrawListener(this) }
        keyHost = playerView.rootView.also { ViewCompat.addOnUnhandledKeyEventListener(it, keyListener) }
    }

    override fun onViewDetachedFromWindow(view: View) {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observer = null
        keyHost?.let { ViewCompat.removeOnUnhandledKeyEventListener(it, keyListener) }
        keyHost = null
        setHidden(false)
    }

    override fun onPreDraw(): Boolean {
        val player = playerView.player
        setHidden(
            playerView.visibility == View.VISIBLE && player?.isPlaying == true &&
                player.videoSize.width > 0 && !playerView.isControllerFullyVisible
        )
        return true
    }

    fun release() {
        onViewDetachedFromWindow(playerView)
        playerView.removeOnAttachStateChangeListener(this)
        commandPanel.setOnTouchListener(null)
    }

    private fun setHidden(value: Boolean) {
        if (value == hidden) return
        hidden = value
        if (value) {
            originalAlpha = commandPanel.alpha
            originalAccessibility = commandPanel.importantForAccessibility
            originalFocusability = commandPanel.descendantFocusability
            for (index in 0 until commandPanel.childCount) rememberAndDisable(commandPanel.getChildAt(index))
            commandPanel.alpha = 0f
            commandPanel.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            commandPanel.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } else {
            commandPanel.alpha = originalAlpha
            commandPanel.importantForAccessibility = originalAccessibility
            commandPanel.descendantFocusability = originalFocusability
            enabledStates.forEach { (view, enabled) -> view.isEnabled = enabled }
            enabledStates.clear()
        }
    }

    private fun rememberAndDisable(view: View) {
        enabledStates[view] = view.isEnabled
        view.isEnabled = false
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) rememberAndDisable(view.getChildAt(index))
        }
    }
}
