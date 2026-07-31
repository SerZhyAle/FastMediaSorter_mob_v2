package com.sza.fastmediasorter.ui.main.helpers

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * S0774 rework: shared, non-modal recording indicator - a small corner pill (dot + timer + pause/resume
 * + stop) mounted once in `activity_main.xml`, reused by both [MainScreenRecordingManager] (screen video
 * recording) and [MainVoiceCaptureManager] (quick voice capture) instead of each showing its own
 * full-screen modal `AlertDialog`. Host-neutral: locates its views via [FragmentActivity.findViewById],
 * so it does not depend on `ActivityMainBinding`'s generated type.
 *
 * S1259: all view lookups are nullable and every operation degrades to a no-op when the indicator
 * include is absent from the active layout variant - a missed variant must cost the indicator,
 * never a crash mid-recording (layout-w600dp shipped without the include and NPE'd on show()).
 *
 * Root is looked up by the include-tag id first: `<include android:id=..>` OVERRIDES the included
 * layout's root id, so `recordingIndicatorRoot` does not exist at runtime in any activity_main
 * variant - all three carry the id on the include tag. The root-id fallback covers a future host
 * that mounts `view_recording_indicator` without an id override.
 */
class RecordingIndicatorOverlayManager(private val activity: FragmentActivity) {

    private val root: View? by lazy {
        activity.findViewById(R.id.recordingIndicatorContainer)
            ?: activity.findViewById(R.id.recordingIndicatorRoot)
            ?: null.also { Timber.w("Recording indicator missing from layout variant; degrading to no-op") }
    }
    private val timerView: TextView? by lazy { activity.findViewById(R.id.recordingIndicatorTimer) }
    private val pauseResumeButton: MaterialButton? by lazy {
        activity.findViewById(R.id.recordingIndicatorPauseResume)
    }
    private val stopButton: MaterialButton? by lazy { activity.findViewById(R.id.recordingIndicatorStop) }
    private val cancelButton: MaterialButton? by lazy { activity.findViewById(R.id.recordingIndicatorCancel) }

    private var insetsApplied = false

    /**
     * [onCancel]/[cancelCd] are optional - only voice capture offers a discard-without-saving action;
     * screen recording has no equivalent in its own scope, so the button stays hidden for it.
     */
    fun show(
        accessibleLabel: String,
        stopCd: String,
        onPauseResume: () -> Unit,
        onStop: () -> Unit,
        onCancel: (() -> Unit)? = null,
        cancelCd: String? = null,
    ) {
        val rootView = root ?: return
        applySafeBoundsInsetsOnce(rootView)
        rootView.contentDescription = accessibleLabel
        pauseResumeButton?.setOnClickListener { onPauseResume() }
        stopButton?.contentDescription = stopCd
        stopButton?.setOnClickListener { onStop() }
        if (onCancel != null) {
            cancelButton?.contentDescription = cancelCd
            cancelButton?.setOnClickListener { onCancel() }
            cancelButton?.isVisible = true
        } else {
            cancelButton?.isVisible = false
        }
        rootView.isVisible = true
    }

    fun updateTimer(text: String) {
        timerView?.text = text
    }

    /** Non-color state distinction (Rule 17/S0774 §3.2): the icon itself swaps, not just a tint. */
    fun setPaused(paused: Boolean, pauseCd: String, resumeCd: String) {
        pauseResumeButton?.setIconResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        pauseResumeButton?.contentDescription = if (paused) resumeCd else pauseCd
    }

    fun dismiss() {
        root?.isVisible = false
    }

    // Mirrors MainActivity.applyEdgeToEdgeInsets(): the indicator sits at the top of a CoordinatorLayout
    // with a small static margin (XML), so a status-bar/cutout overlap is added on top of that margin
    // rather than replacing it - keeps the pill inside systemBars/cutout safe bounds in both orientations.
    private fun applySafeBoundsInsetsOnce(rootView: View) {
        if (insetsApplied) return
        insetsApplied = true
        val baseTopMargin = (rootView.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val safeTop = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = baseTopMargin + safeTop
                view.layoutParams = lp
            }
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }
}
