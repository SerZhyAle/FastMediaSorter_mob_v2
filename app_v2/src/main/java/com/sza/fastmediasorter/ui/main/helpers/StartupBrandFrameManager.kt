package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.view.ViewGroup
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ViewStartupBrandFrameBinding
import timber.log.Timber

/**
 * S2556: the phone's startup brand frame - the logo, the wordmark and the current time with
 * seconds, held over the main screen for one beat right after the platform starting window hands
 * over.
 *
 * It exists because the platform window cannot host it: that window is assembled by the system from
 * theme attributes and its icon slot accepts a picture, never live text, so a clock on the phone's
 * startup can only be drawn by a surface the app owns. Below Android 12 the base theme suppresses
 * the starting window entirely, and this frame is what those devices get instead of nothing.
 *
 * The beat matches the watch's `BrandFrameScreen`, so both devices hold the brand for the same
 * length; keeping it in one named constant here is what lets it later become a setting without
 * touching the composition.
 */
object StartupBrandFrameManager {

    private const val FRAME_DURATION_MS = 700L

    /**
     * Lays the frame over [activity]'s content and schedules its own removal. Safe to call twice -
     * the second call finds the first frame still attached and does nothing rather than stacking a
     * second one whose timer would outlive it.
     */
    fun attach(activity: Activity) {
        // The decor root rather than android.R.id.content: the platform starting window covers the
        // whole window, system bars included, so a frame that continues it has to sit at the same
        // level - anchored inside the content view it would start below the status bar and read as a
        // second screen. It is also the one host reachable without a view-id lookup (S1693).
        val host = activity.window.decorView as? ViewGroup ?: return
        // Direct children only, and by id rather than a tree-wide search: the frame is always a
        // child of the decor root, and this runs on the cold-start path where a full traversal of an
        // already-inflated main screen would be paid on every launch.
        val alreadyUp = (0 until host.childCount).any { host.getChildAt(it).id == R.id.startup_brand_frame }
        if (alreadyUp) return

        val frame = ViewStartupBrandFrameBinding.inflate(activity.layoutInflater, host, false).root
        host.addView(frame)
        Timber.d("S2556: phone startup brand frame attached, holding ${FRAME_DURATION_MS}ms")
        // The parent is read at removal time, not captured now: the activity may be finishing by
        // then, and a detached view must make this a no-op rather than a crash.
        frame.postDelayed({ (frame.parent as? ViewGroup)?.removeView(frame) }, FRAME_DURATION_MS)
    }
}
