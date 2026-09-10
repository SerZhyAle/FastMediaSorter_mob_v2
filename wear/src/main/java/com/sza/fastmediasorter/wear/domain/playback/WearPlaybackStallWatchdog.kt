package com.sza.fastmediasorter.wear.domain.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * S2848: ends a session that wants to play and makes no sound.
 *
 * S2849 moved it beside the rule it applies. It was written for the background service and the two
 * player screens need the same timer, so a home under `service/` would have made a ViewModel import
 * from the service package to reach it.
 *
 * Armed while [BackgroundPlaybackActivity.Stalled] holds and disarmed the moment it does not, so a
 * stream that recovers on its own costs nothing. Re-arming is deliberately a no-op while a timer is
 * already running: repeated stall reports are one stall, and restarting the countdown on each of them
 * is how a session that reports every retry would postpone its own deadline forever.
 */
class WearPlaybackStallWatchdog(
    private val scope: CoroutineScope,
    private val stallTimeoutMs: Long,
    private val onStalled: () -> Unit
) {

    private var timer: Job? = null

    fun onActivityChanged(activity: BackgroundPlaybackActivity) {
        if (activity == BackgroundPlaybackActivity.Stalled) {
            arm()
        } else {
            cancel()
        }
    }

    fun cancel() {
        timer?.cancel()
        timer = null
    }

    private fun arm() {
        if (timer?.isActive == true) {
            return
        }
        timer = scope.launch {
            delay(stallTimeoutMs)
            onStalled()
        }
    }
}
