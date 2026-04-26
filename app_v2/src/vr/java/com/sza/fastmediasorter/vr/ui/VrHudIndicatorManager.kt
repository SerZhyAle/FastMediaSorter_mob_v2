package com.sza.fastmediasorter.vr.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.vr.render.ActionBadge
import com.sza.fastmediasorter.vr.render.RepeatMode
import timber.log.Timber

/**
 * Auto-dismissing HUD indicators for VR immersive playback.
 *
 * Implemented as an Android `FrameLayout` overlay added to the activity's decor view.
 * Per ADR-2 (tech spec) this is a first-iteration placeholder for a future OpenXR
 * composition-layer quad — the API here is already command-oriented so the renderer
 * swap does not ripple into callers.
 *
 * Four independent slots:
 *  - `center` — pause/play icon, zoom factor, boundary messages
 *  - `bottom` — seek indicator "[01:32 / 14:20] +10s ▶▶"
 *  - `right`  — volume bar
 *  - `topRight` — file name + "3 / 12"
 *
 * Each slot has its own timer, so a volume change does not interrupt a file-name
 * indicator that is still auto-dismissing.
 */
class VrHudIndicatorManager(private val activity: Activity) : VrHudSink {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val root: FrameLayout by lazy { buildRoot() }
    private val centerSlot: TextView by lazy { buildSlot(Gravity.CENTER) }
    private val bottomSlot: TextView by lazy { buildSlot(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, bottomMargin = 96) }
    private val rightSlot: TextView by lazy { buildSlot(Gravity.END or Gravity.CENTER_VERTICAL, endMargin = 48) }
    private val topRightSlot: TextView by lazy { buildSlot(Gravity.TOP or Gravity.END, topMargin = 48, endMargin = 48) }

    private val centerHideRunnable = Runnable { centerSlot.visibility = View.GONE }
    private val bottomHideRunnable = Runnable { bottomSlot.visibility = View.GONE }
    private val rightHideRunnable  = Runnable { rightSlot.visibility  = View.GONE }
    private val topRightHideRunnable = Runnable { topRightSlot.visibility = View.GONE }

    override fun showPauseIndicator(paused: Boolean) {
        val txt = if (paused) PAUSE_ICON else PLAY_ICON
        showCenter(txt, durationMs = DURATION_PAUSE_MS)
    }

    override fun showVolumeIndicator(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        showRight(activity.getString(R.string.vr_hud_volume_percent, clamped), durationMs = DURATION_VOLUME_MS)
    }

    override fun showSeekIndicator(deltaSeconds: Int, positionMs: Long, totalMs: Long) {
        val forward = deltaSeconds >= 0
        val arrow = if (forward) "▶▶" else "◀◀"
        val prefix = if (forward) "+" else ""
        val posStr = formatMs(positionMs)
        val totalStr = formatMs(totalMs)
        val text = "[$posStr / $totalStr]  $prefix${deltaSeconds}s $arrow"
        showBottom(text, durationMs = DURATION_SEEK_MS)
    }

    override fun showFileIndicator(name: String, index: Int, total: Int) {
        val shortened = if (name.length > 40) name.take(37) + ".." else name
        val text = "$shortened  $index / $total"
        showTopRight(text, durationMs = DURATION_FILE_MS)
    }

    override fun showZoomIndicator(factor: Float) {
        val text = activity.getString(R.string.vr_hud_zoom_factor, factor)
        showCenter(text, durationMs = DURATION_ZOOM_MS)
    }

    override fun showRecenterFlash() {
        // Minimal visual: brief "✦ recentered" center toast. A full frame-border flash
        // would require a dedicated View; deferred per ADR-2 to the composition-layer iteration.
        showCenter(activity.getString(R.string.vr_hud_recenter_done), durationMs = DURATION_RECENTER_MS)
    }

    override fun showBoundaryMessage(isFirst: Boolean) {
        val resId = if (isFirst) R.string.vr_hud_first_file else R.string.vr_hud_last_file
        showTopRight(activity.getString(resId), durationMs = DURATION_BOUNDARY_MS)
    }

    override fun showErrorMessage(message: String) {
        showCenter(message, durationMs = DURATION_ERROR_MS)
    }

    override fun showImmersiveModeChanged(immersive: Boolean) {
        val resId = if (immersive) R.string.vr_hud_immersive_on else R.string.vr_hud_immersive_off
        showCenter(activity.getString(resId), durationMs = DURATION_IMMERSIVE_MS)
    }

    override fun showActionBadge(badge: ActionBadge) {
        val glyph = when (badge) {
            ActionBadge.PREV_FILE -> "⏮ prev"
            ActionBadge.NEXT_FILE -> "⏭ next"
            ActionBadge.REWIND -> "⏪ -10s"
            ActionBadge.FORWARD -> "⏩ +30s"
            ActionBadge.OPEN_CONTROLS -> "☰"
            ActionBadge.REPEAT_TOGGLE -> "↻"
        }
        showCenter(glyph, durationMs = DURATION_PAUSE_MS)
    }

    override fun showRepeatMode(mode: RepeatMode?) {
        if (mode == null) return
        val text = when (mode) {
            RepeatMode.OFF -> "↻ OFF"
            RepeatMode.ONE -> "↻ 1"
            RepeatMode.ALL -> "↻ ALL"
        }
        showTopRight(text, durationMs = DURATION_BOUNDARY_MS)
    }

    override fun showBannerText(text: String?) {
        if (text == null) {
            mainHandler.removeCallbacks(centerHideRunnable)
            centerSlot.visibility = View.GONE
        } else {
            showCenter(text, durationMs = DURATION_BANNER_MS)
        }
    }

    override fun updateProgress(positionMs: Long, bufferedMs: Long, totalMs: Long) {
        // Android-view fallback never showed a live progress bar; keep no-op
        // so phone-fallback visuals are unchanged.
    }

    // WHY: reportActivity() and showStereoModeLabel() are used only by the OpenXR composition-
    // layer backend (VrHudSceneDriver). The view-overlay fallback has no idle-hide timer and
    // no stereo mode badge, so both are intentional no-ops here.
    override fun reportActivity() = Unit
    override fun showStereoModeLabel(label: String?) = Unit

    override fun hideAll() {
        listOf(centerSlot, bottomSlot, rightSlot, topRightSlot).forEach { it.visibility = View.GONE }
        mainHandler.removeCallbacks(centerHideRunnable)
        mainHandler.removeCallbacks(bottomHideRunnable)
        mainHandler.removeCallbacks(rightHideRunnable)
        mainHandler.removeCallbacks(topRightHideRunnable)
    }

    fun release() {
        hideAll()
        try {
            (root.parent as? ViewGroup)?.removeView(root)
        } catch (t: Throwable) {
            Timber.w(t, "VrHud: failed to remove overlay root")
        }
    }

    // ── Internal slot rendering ──────────────────────────────────────────

    private fun showCenter(text: CharSequence, durationMs: Long) {
        showSlot(centerSlot, centerHideRunnable, text, durationMs)
    }

    private fun showBottom(text: CharSequence, durationMs: Long) {
        showSlot(bottomSlot, bottomHideRunnable, text, durationMs)
    }

    private fun showRight(text: CharSequence, durationMs: Long) {
        showSlot(rightSlot, rightHideRunnable, text, durationMs)
    }

    private fun showTopRight(text: CharSequence, durationMs: Long) {
        showSlot(topRightSlot, topRightHideRunnable, text, durationMs)
    }

    private fun showSlot(slot: TextView, hideRunnable: Runnable, text: CharSequence, durationMs: Long) {
        slot.text = text
        slot.visibility = View.VISIBLE
        ensureAttached()
        mainHandler.removeCallbacks(hideRunnable)
        mainHandler.postDelayed(hideRunnable, durationMs)
    }

    private fun ensureAttached() {
        if (root.parent == null) {
            val decor = activity.window?.decorView as? ViewGroup ?: return
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            decor.addView(root, params)
        }
    }

    private fun buildRoot(): FrameLayout {
        val fl = FrameLayout(activity)
        fl.isClickable = false
        fl.isFocusable = false
        fl.setBackgroundColor(Color.TRANSPARENT)
        return fl
    }

    private fun buildSlot(
        gravity: Int,
        topMargin: Int = 0,
        bottomMargin: Int = 0,
        endMargin: Int = 0,
    ): TextView {
        val tv = TextView(activity)
        tv.setTextColor(Color.WHITE)
        tv.textSize = 18f
        tv.setPadding(32, 16, 32, 16)
        tv.background = GradientDrawable().apply {
            cornerRadius = 24f
            setColor(Color.argb(180, 0, 0, 0))
        }
        tv.visibility = View.GONE
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            this.gravity = gravity
            this.topMargin = topMargin
            this.bottomMargin = bottomMargin
            this.marginEnd = endMargin
        }
        root.addView(tv, lp)
        return tv
    }

    private fun formatMs(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    companion object {
        private const val PAUSE_ICON = "❚❚"
        private const val PLAY_ICON  = "▶"

        private const val DURATION_PAUSE_MS    = 800L
        private const val DURATION_VOLUME_MS   = 1500L
        private const val DURATION_SEEK_MS     = 1200L
        private const val DURATION_FILE_MS     = 2000L
        private const val DURATION_ZOOM_MS     = 1000L
        private const val DURATION_RECENTER_MS = 400L
        private const val DURATION_BOUNDARY_MS = 1500L
        private const val DURATION_IMMERSIVE_MS = 1200L
        private const val DURATION_ERROR_MS    = 3000L
        private const val DURATION_BANNER_MS   = 3000L

        // Suppress unused param warning while we keep a single dependency hook.
        @Suppress("unused")
        private val LM_WARN = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }
}
