package com.sza.fastmediasorter.ui.common.backgroundop

import android.view.Gravity
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.utils.collectOnLifecycle

/**
 * Lays the hairline background-operation bar over a screen's content root and feeds it.
 *
 * The app has no shared root layout across its Activities, so the bar is added to the content
 * FrameLayout instead of to each screen's XML - which is also what keeps it from shifting any
 * content. It takes no window insets on purpose (S1398 §6.6): it sits at the physical bottom of the
 * window and may pass under a translucent system bar, leaving the per-screen inset arbiter the sole
 * owner of the bottom inset.
 */
class BackgroundOperationBarAttachManager(
    private val activity: AppCompatActivity,
    private val trackManager: BackgroundOperationTrackManager,
) {

    fun attach() {
        val contentRoot = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val barView = BackgroundOperationBarView(activity).apply {
            render(BackgroundOperationBarState.Hidden)
        }
        contentRoot.addView(
            barView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        activity.collectOnLifecycle(trackManager.barState()) { barView.render(it) }
    }
}
