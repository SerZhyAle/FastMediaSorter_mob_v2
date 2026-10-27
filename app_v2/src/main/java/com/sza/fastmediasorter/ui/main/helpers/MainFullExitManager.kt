package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.broadcast.AnnounceBroadcastEndUseCase
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * The app's full exit with a live broadcast (contract LIVE-BROADCAST item T: closing the app is a deliberate end).
 * The process used to be killed with the broadcast on air, so its viewers saw a lost connection; now the broadcast
 * is stopped through its ordered end first, and the teardown waits for it, never longer than the bound. With no live
 * broadcast the teardown runs at once, exactly as before.
 */
class MainFullExitManager(
    private val controller: BroadcastSourceController,
    private val scope: CoroutineScope,
) {

    private var exiting = false

    /** Runs [teardown] once the live broadcast, if any, has ended; a second call while waiting is ignored. */
    fun exit(teardown: () -> Unit) {
        if (exiting) return
        if (controller.state.value !is BroadcastState.Live) {
            teardown()
        } else {
            Timber.d("S4442: full exit waits for the broadcast end")
            exiting = true
            controller.stop()
            scope.launch {
                withTimeoutOrNull(AnnounceBroadcastEndUseCase.EXIT_WAIT_TIMEOUT_MS) {
                    controller.state.first { it !is BroadcastState.Live }
                }
                teardown()
            }
        }
    }
}
