package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.ui.launcher.grid.LauncherScrollThumbView
import timber.log.Timber

/**
 * S1430: keeps the launcher desktop's scroll thumb and its scroll container in step.
 *
 * The thumb owns no position of its own: a scroll feeds it the container's geometry, and a drag on it sets
 * the container's position. Visibility is derived from the content being taller than the viewport, so a
 * desktop that fits on one screen shows nothing.
 */
class LauncherScrollThumbManager(
    private val lifecycleOwner: LifecycleOwner,
    private val scrollView: NestedScrollView,
    private val thumb: LauncherScrollThumbView,
) : DefaultLifecycleObserver {

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncThumb() }

    /** Wire the two together and keep them wired until the host is destroyed. */
    fun attach() {
        Timber.d("S1430: launcher desktop scroll thumb attached")
        scrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> syncThumb() }
        )
        // Shortcuts are added and removed at run time, so the content height changes without a scroll.
        scrollView.addOnLayoutChangeListener(layoutListener)
        contentView()?.addOnLayoutChangeListener(layoutListener)
        thumb.onScrollRequested = { offset -> scrollView.scrollTo(0, offset) }
        lifecycleOwner.lifecycle.addObserver(this)
        syncThumb()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        scrollView.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
        scrollView.removeOnLayoutChangeListener(layoutListener)
        contentView()?.removeOnLayoutChangeListener(layoutListener)
        thumb.onScrollRequested = null
        owner.lifecycle.removeObserver(this)
    }

    private fun contentView(): View? = scrollView.getChildAt(0)

    private fun syncThumb() {
        val content = contentView() ?: return
        thumb.onScrollPositionChanged(
            scrollY = scrollView.scrollY,
            contentHeight = content.height,
            viewportHeight = scrollView.height,
        )
        thumb.isVisible = thumb.isScrollable()
    }
}
