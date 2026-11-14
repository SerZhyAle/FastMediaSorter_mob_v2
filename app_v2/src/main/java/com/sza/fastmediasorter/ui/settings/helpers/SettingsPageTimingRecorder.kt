package com.sza.fastmediasorter.ui.settings.helpers

import android.os.SystemClock
import android.view.View
import androidx.core.view.doOnPreDraw
import timber.log.Timber

/**
 * S4621: activation-to-first-frame time of each settings page visit (SETTINGS-NAVIGATION 2.1
 * section 5 rule 4), split into a page's first visit in this screen and its warm visits.
 */
class SettingsPageTimingRecorder {

    private val visited = mutableSetOf<String>()
    private var generation = 0

    /** Call when [id] is activated; [pageView] resolves the page's view, null while it does not exist yet. */
    fun onActivated(id: String, host: View, pageView: () -> View?) {
        val started = SystemClock.uptimeMillis()
        val token = ++generation
        val visit = if (visited.add(id)) VISIT_FIRST else VISIT_WARM
        awaitFrame(Pending(id, visit, started, token), host, pageView)
    }

    private fun awaitFrame(pending: Pending, host: View, pageView: () -> View?) {
        if (pending.token != generation) return
        val view = pageView()
        if (view == null) {
            host.post { awaitFrame(pending, host, pageView) }
            return
        }
        view.doOnPreDraw {
            if (pending.token == generation) {
                val ms = SystemClock.uptimeMillis() - pending.started
                Timber.i("SettingsNav: page=${pending.id} visit=${pending.visit} ms=$ms")
            }
        }
        view.invalidate()
    }

    private class Pending(val id: String, val visit: String, val started: Long, val token: Int)

    private companion object {
        const val VISIT_FIRST = "first"
        const val VISIT_WARM = "warm"
    }
}
