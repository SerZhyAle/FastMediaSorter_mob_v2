package com.sza.fastmediasorter.vr.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.vr.VrPlayerActivity
import timber.log.Timber

/**
 * VR controller / keyboard / mouse cheatsheet overlay.
 *
 * Two entry points per UX spec §5.3:
 *  - **First-run auto-show** once after install (SharedPreferences-gated) for [FIRST_RUN_DURATION_MS].
 *  - **Manual toggle** via left-controller `Y` long-press or the `F1` key.
 *
 * Content is localized through string resources so EN/RU/UK stay a single source of truth.
 */
class VrCheatsheetOverlayManager(private val activity: Activity) {

    private val prefs: SharedPreferences =
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mainHandler = Handler(Looper.getMainLooper())

    private val autoHideRunnable = Runnable { hide() }

    private val overlayRoot: FrameLayout by lazy { buildOverlay() }

    fun showIfFirstTime() {
        if (prefs.getBoolean(KEY_SHOWN_ONCE, false)) return
        prefs.edit().putBoolean(KEY_SHOWN_ONCE, true).apply()
        // In immersive (and while spec_vr-ui-composition-layer is not yet active) the
        // full Android-view overlay would be invisible. Surface a brief HUD banner with
        // the cheat summary instead, so the user actually sees something. The "shown
        // once" flag is still flipped above so this path does not repeat next launch.
        val vrActivity = activity as? VrPlayerActivity
        if (vrActivity?.isImmersiveUiLocked() == true) {
            Timber.i("VrCheatsheet: first-run HUD banner (immersive)")
            vrActivity.vrHudManager?.showBannerText(activity.getString(R.string.vr_hud_first_run_cheat))
            return
        }
        Timber.i("VrCheatsheet: first-run auto-show")
        show(durationMs = FIRST_RUN_DURATION_MS)
    }

    fun toggleManual() {
        // Manual cheatsheet from immersive — also routed via HUD banner; the full Android
        // panel will be unblocked once the composition layer is ready (spec B).
        val vrActivity = activity as? VrPlayerActivity
        if (vrActivity?.isImmersiveUiLocked() == true) {
            vrActivity.vrHudManager?.showBannerText(activity.getString(R.string.vr_hud_first_run_cheat))
            return
        }
        if (isVisible()) hide() else show(durationMs = MANUAL_DURATION_MS)
    }

    fun isVisible(): Boolean = overlayRoot.parent != null && overlayRoot.visibility == View.VISIBLE

    fun hide() {
        mainHandler.removeCallbacks(autoHideRunnable)
        (overlayRoot.parent as? ViewGroup)?.removeView(overlayRoot)
    }

    fun release() {
        hide()
    }

    // ─── Internal ─────────────────────────────────────────────────────────

    private fun show(durationMs: Long) {
        val decor = activity.window?.decorView as? ViewGroup ?: return
        hide()
        decor.addView(
            overlayRoot,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        overlayRoot.visibility = View.VISIBLE
        if (durationMs > 0) {
            mainHandler.removeCallbacks(autoHideRunnable)
            mainHandler.postDelayed(autoHideRunnable, durationMs)
        }
    }

    private fun buildOverlay(): FrameLayout {
        val root = FrameLayout(activity)
        root.setBackgroundColor(Color.argb(200, 0, 0, 0))
        root.isClickable = true
        root.setOnClickListener { hide() }

        val card = FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                cornerRadius = 32f
                setColor(Color.argb(240, 30, 30, 35))
                setStroke(2, Color.WHITE)
            }
        }
        val cardLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER
            setMargins(64, 64, 64, 64)
        }

        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = true
            setPadding(48, 48, 48, 48)
        }
        val content = TextView(activity).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            text = buildContent()
            setTextIsSelectable(false)
        }
        scroll.addView(content)
        card.addView(scroll)
        root.addView(card, cardLp)
        return root
    }

    private fun buildContent(): CharSequence {
        val sb = StringBuilder()
        sb.append(activity.getString(R.string.vr_cheatsheet_title)).append("\n\n")
        sb.append(activity.getString(R.string.vr_cheatsheet_section_controllers)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_ctrl_a)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_ctrl_b)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_ctrl_x)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_ctrl_y)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_ctrl_menu)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_ctrl_stick_l)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_ctrl_stick_r)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_ctrl_grip)).append('\n')
        sb.append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_section_keyboard)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_kb_space)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_kb_arrows)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_kb_fkeys)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_kb_ctrl)).append('\n')
        sb.append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_section_mouse)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_mouse_buttons)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_mouse_wheel)).append('\n')
        sb.append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_section_hands)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_hands_ray)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_hands_swipe)).append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_hands_double_pinch)).append('\n')
        sb.append('\n')
        sb.append(activity.getString(R.string.vr_cheatsheet_footer))
        return sb.toString()
    }

    companion object {
        private const val PREFS_NAME = "vr_cheatsheet_prefs"
        private const val KEY_SHOWN_ONCE = "vr_cheatsheet_shown_once"

        private const val FIRST_RUN_DURATION_MS = 4000L
        private const val MANUAL_DURATION_MS = 0L // 0 = until dismissed
    }
}
