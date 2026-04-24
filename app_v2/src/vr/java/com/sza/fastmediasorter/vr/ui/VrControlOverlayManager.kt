package com.sza.fastmediasorter.vr.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommandSet
import timber.log.Timber

/**
 * View-based VR playback overlay — shown on top of the 2D decor surface beneath
 * the OpenXR composition layer. Contains quick-reach buttons for playback,
 * navigation, settings and exit; dispatches [PlaybackCommand]s through
 * [onCommand]. Auto-hides after [AUTO_HIDE_DELAY_MS] of inactivity.
 *
 * Per ADR-2 (tech spec) this is a first-iteration placeholder for a true
 * OpenXR quad composition layer in world space; the API is command-oriented
 * so callers do not see the swap when it lands.
 */
class VrControlOverlayManager(
    private val activity: Activity,
    private val commandSet: PlaybackCommandSet = PlaybackCommandSet.forVrPlayback(),
    private val onCommand: (PlaybackCommand) -> Unit,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }

    private var overlay: FrameLayout? = null

    fun show() {
        Timber.d("VrControlOverlay: show")
        if (overlay != null) {
            // Re-trigger auto-hide timer on repeated show().
            scheduleAutoHide()
            return
        }
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val root = buildRoot()
        overlay = root
        decor.addView(
            root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        scheduleAutoHide()
    }

    fun hide() {
        Timber.d("VrControlOverlay: hide")
        mainHandler.removeCallbacks(hideRunnable)
        val root = overlay ?: return
        (root.parent as? ViewGroup)?.removeView(root)
        overlay = null
    }

    fun toggle() {
        if (isOverlayVisible()) hide() else show()
    }

    fun dispatchCommand(command: PlaybackCommand) {
        if (command !in commandSet.available) {
            Timber.w("VrControlOverlay: command %s not in available set, ignoring", command)
            return
        }
        onCommand(command)
    }

    fun isOverlayVisible(): Boolean = overlay?.parent != null

    fun release() {
        hide()
    }

    // ─── Internal ─────────────────────────────────────────────────────────

    private fun scheduleAutoHide() {
        mainHandler.removeCallbacks(hideRunnable)
        mainHandler.postDelayed(hideRunnable, AUTO_HIDE_DELAY_MS)
    }

    private fun buildRoot(): FrameLayout {
        val root = FrameLayout(activity)
        root.isClickable = true
        root.setOnClickListener {
            // Tap on empty area dismisses.
            hide()
        }

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 24, 32, 24)
            background = GradientDrawable().apply {
                cornerRadius = 32f
                setColor(Color.argb(220, 20, 20, 24))
                setStroke(2, Color.WHITE)
            }
            isClickable = true
            setOnClickListener { scheduleAutoHide() }
        }

        bar.addView(button(R.string.vr_overlay_btn_prev) { dispatchAndReschedule(PlaybackCommand.PreviousFile) })
        bar.addView(button(R.string.vr_overlay_btn_seek_back) { dispatchAndReschedule(PlaybackCommand.SeekBackward) })
        bar.addView(button(R.string.vr_overlay_btn_play_pause) { dispatchAndReschedule(PlaybackCommand.TogglePausePlay) })
        bar.addView(button(R.string.vr_overlay_btn_seek_fwd) { dispatchAndReschedule(PlaybackCommand.SeekForward) })
        bar.addView(button(R.string.vr_overlay_btn_next) { dispatchAndReschedule(PlaybackCommand.NextFile) })
        bar.addView(button(R.string.vr_overlay_btn_settings) { dispatchAndReschedule(PlaybackCommand.OpenControls) })
        bar.addView(button(R.string.vr_overlay_btn_exit) { dispatchAndReschedule(PlaybackCommand.Exit) })

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 64
        }
        root.addView(bar, lp)
        return root
    }

    private fun button(labelRes: Int, onClick: () -> Unit): Button {
        return Button(activity).apply {
            text = activity.getString(labelRes)
            setOnClickListener { onClick() }
        }
    }

    private fun dispatchAndReschedule(command: PlaybackCommand) {
        dispatchCommand(command)
        scheduleAutoHide()
    }

    @Suppress("unused")
    private fun noop(v: View) { /* keep View import clean */ }

    companion object {
        /** Overlay auto-hides after 5 seconds of inactivity. */
        const val AUTO_HIDE_DELAY_MS = 5_000L
    }
}
