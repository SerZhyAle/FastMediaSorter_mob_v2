package com.sza.fastmediasorter.vr.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommandSet
import com.sza.fastmediasorter.vr.render.VrInteractivePanelDriver
import timber.log.Timber

/**
 * View-based VR playback overlay — shown on top of the 2D decor surface beneath
 * the OpenXR composition layer. Contains two rows of quick-reach buttons for playback,
 * navigation, volume, brightness, speed, track and format; dispatches [PlaybackCommand]s
 * through [onCommand]. Auto-hides after [AUTO_HIDE_DELAY_MS] of inactivity.
 *
 * Per ADR-2 (tech spec) this is a placeholder for a true OpenXR quad composition layer;
 * Phase 03 of spec_vr-immersive-controls-panel delegates show/hide/toggle to an optional
 * GL panel driver without changing the external API.
 */
class VrControlOverlayManager(
    private val activity: Activity,
    private val commandSet: PlaybackCommandSet = PlaybackCommandSet.forVrPlayback(),
    private val onCommand: (PlaybackCommand) -> Unit,
    private val panelDriver: VrInteractivePanelDriver? = null,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }

    private var overlay: FrameLayout? = null
    private var formatLabelView: TextView? = null
    private var speedButton: Button? = null
    private var currentSpeed = 1.0f

    fun show() {
        if (panelDriver != null) {
            panelDriver.show()
            return
        }
        Timber.d("VrControlOverlay: show")
        if (overlay != null) {
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
        if (panelDriver != null) {
            panelDriver.hide()
            return
        }
        Timber.d("VrControlOverlay: hide")
        mainHandler.removeCallbacks(hideRunnable)
        val root = overlay ?: return
        (root.parent as? ViewGroup)?.removeView(root)
        overlay = null
        formatLabelView = null
        speedButton = null
    }

    fun toggle() {
        if (panelDriver != null) {
            panelDriver.toggle()
            return
        }
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

    fun updateStereoLabel(label: String) {
        val view = formatLabelView ?: return
        view.text = activity.getString(R.string.vr_overlay_format_label, label)
    }

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
        root.setOnClickListener { hide() }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setOnClickListener { scheduleAutoHide() }
        }

        // Row 2 (extended) — appears above Row 1.
        val row2 = buildRow(
            button(R.string.vr_overlay_btn_vol_down) { dispatchAndReschedule(PlaybackCommand.VolumeDown) },
            button(R.string.vr_overlay_btn_vol_up) { dispatchAndReschedule(PlaybackCommand.VolumeUp) },
            button(R.string.vr_overlay_btn_bright_down) { dispatchAndReschedule(PlaybackCommand.BrightnessDown) },
            button(R.string.vr_overlay_btn_bright_up) { dispatchAndReschedule(PlaybackCommand.BrightnessUp) },
            button(R.string.vr_overlay_btn_speed) {
                dispatchAndReschedule(PlaybackCommand.SetPlaybackSpeed(nextSpeed()))
            }.also { btn ->
                speedButton = btn
                updateSpeedLabel(btn)
            },
            button(R.string.vr_overlay_btn_track) { dispatchAndReschedule(PlaybackCommand.CycleAudioTrack) },
            button(R.string.vr_overlay_btn_format) { dispatchAndReschedule(PlaybackCommand.CycleStereoFormat) },
        )

        val fmtLabel = TextView(activity).apply {
            text = activity.getString(R.string.vr_overlay_format_label, "")
            setTextColor(Color.WHITE)
            setPadding(8, 2, 8, 2)
        }
        formatLabelView = fmtLabel

        // Row 1 (primary).
        val row1 = buildRow(
            button(R.string.vr_overlay_btn_prev) { dispatchAndReschedule(PlaybackCommand.PreviousFile) },
            button(R.string.vr_overlay_btn_seek_back) { dispatchAndReschedule(PlaybackCommand.SeekBackward) },
            button(R.string.vr_overlay_btn_play_pause) { dispatchAndReschedule(PlaybackCommand.TogglePausePlay) },
            button(R.string.vr_overlay_btn_seek_fwd) { dispatchAndReschedule(PlaybackCommand.SeekForward) },
            button(R.string.vr_overlay_btn_next) { dispatchAndReschedule(PlaybackCommand.NextFile) },
            button(R.string.vr_overlay_btn_settings) { dispatchAndReschedule(PlaybackCommand.OpenControls) },
            button(R.string.vr_overlay_btn_exit) { dispatchAndReschedule(PlaybackCommand.Exit) },
        )

        container.addView(row2)
        container.addView(fmtLabel)
        container.addView(row1)

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 64
        }
        root.addView(container, lp)
        return root
    }

    private fun buildRow(vararg buttons: Button): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 24, 32, 24)
            background = GradientDrawable().apply {
                cornerRadius = 32f
                setColor(Color.argb(220, 20, 20, 24))
                setStroke(2, Color.WHITE)
            }
            isClickable = true
            setOnClickListener { scheduleAutoHide() }
            buttons.forEach { addView(it) }
        }
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

    private fun nextSpeed(): Float {
        val speeds = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f)
        val idx = speeds.indexOfFirst { it == currentSpeed }
        currentSpeed = speeds[(idx + 1) % speeds.size]
        speedButton?.let { updateSpeedLabel(it) }
        return currentSpeed
    }

    private fun updateSpeedLabel(button: Button) {
        button.text = activity.getString(R.string.vr_overlay_speed_label, currentSpeed)
    }

    companion object {
        const val AUTO_HIDE_DELAY_MS = 10_000L
    }
}
