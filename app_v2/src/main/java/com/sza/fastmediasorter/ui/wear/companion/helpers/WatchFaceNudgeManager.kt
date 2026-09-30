package com.sza.fastmediasorter.ui.wear.companion.helpers

import android.content.Context
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.PairedWatchStatus
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * S4009: offers the watch face once, the first time the companion window sees a connected watch.
 *
 * Shaped after MainChromeOsBannerManager and sharing its preference file, but the flag is written as
 * the hint appears rather than on dismissal: the owner asked that it never repeat, and a process death
 * before the dismissal callback would otherwise show it again.
 */
class WatchFaceNudgeManager(
    private val fragment: Fragment,
    private val ioDispatcher: CoroutineDispatcher,
    private val onGetWatchFace: () -> Unit,
) {

    /** Guards the window between the first Connected emission and the flag landing on disk. */
    private var handledThisView = false

    fun bind(watchStatus: Flow<PairedWatchStatus>) {
        fragment.collectOnLifecycle(watchStatus) { status ->
            if (status is PairedWatchStatus.Connected) showOnce()
        }
    }

    private suspend fun showOnce() {
        if (handledThisView) return
        handledThisView = true
        val context = fragment.requireContext().applicationContext
        val firstTime = withContext(ioDispatcher) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val shown = prefs.getBoolean(KEY_SHOWN, false)
            if (!shown) prefs.edit(commit = true) { putBoolean(KEY_SHOWN, true) }
            !shown
        }
        if (!firstTime) return
        Timber.i("Watch face hint shown")
        Snackbar.make(fragment.requireView(), R.string.wear_watchface_hint, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.wear_watchface_hint_action) { onGetWatchFace() }
            .show()
    }

    private companion object {
        const val PREFS_NAME = "fms_prefs"
        const val KEY_SHOWN = "watch_face_hint_shown"
    }
}
