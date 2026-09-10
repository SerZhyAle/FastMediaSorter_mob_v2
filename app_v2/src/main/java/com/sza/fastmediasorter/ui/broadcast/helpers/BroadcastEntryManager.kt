package com.sza.fastmediasorter.ui.broadcast.helpers

import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.ui.main.helpers.MainBroadcastManager
import com.sza.fastmediasorter.utils.collectOnLifecycle

/**
 * Drives the broadcast entry confirmation screen - the one screen every S2818 entry surface opens.
 * Session concerns (permission loop, failure snackbars, live indicator, share auto-open) stay in
 * [MainBroadcastManager], handed in pre-built from [com.sza.fastmediasorter.ui.main.helpers.MainHelperFactory]
 * so no domain dependency reaches the host activity.
 */
class BroadcastEntryManager(
    private val broadcastManager: MainBroadcastManager,
    private val controller: BroadcastSourceController,
    private val finish: () -> Unit,
    private val render: (UiState) -> Unit,
) {

    fun bind(owner: LifecycleOwner) {
        if (!controller.isAvailable) {
            render(UiState.Unavailable)
            finish()
            return
        }
        broadcastManager.bind(owner)
        owner.collectOnLifecycle(controller.state) { state ->
            render(mapState(state, isAvailable = true))
        }
    }

    fun onPermissionResult(permission: String, granted: Boolean) {
        broadcastManager.onPermissionResult(permission, granted)
    }

    fun start() {
        broadcastManager.startBroadcast()
    }

    fun stop() {
        broadcastManager.stopBroadcast()
    }

    /** Leaving via cancel must not touch the session - nothing has started yet (strategic criterion 6). */
    fun cancel() {
        finish()
    }

    enum class UiState {
        Unavailable,
        Confirm,
        Live,
    }

    companion object {
        /**
         * Idle and Failed both render the confirm layout: a failed session already reported itself
         * through the snackbars and returns to idle on the next start attempt.
         */
        fun mapState(state: BroadcastState, isAvailable: Boolean): UiState = when {
            !isAvailable -> UiState.Unavailable
            state is BroadcastState.Live -> UiState.Live
            else -> UiState.Confirm
        }
    }
}
