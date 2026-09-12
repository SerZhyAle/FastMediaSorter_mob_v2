package com.sza.fastmediasorter.wear.domain.listen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * S2939: how long a phone-requested listening session may go with nobody accepting its audio.
 *
 * Well above the few seconds the phone needs to connect once the watch has answered with its address,
 * and far below anything that shows on the battery: an unheard session held the microphone, the
 * encoder and a CPU that never slept for six hours, two nights running.
 */
const val LISTEN_ABANDON_AFTER_MS = 90_000L

/** Coarse on purpose: the verdict is about minutes, and each check wakes the service's dispatcher. */
const val LISTEN_AUDIENCE_CHECK_INTERVAL_MS = 10_000L

/**
 * S2939: ends a listening session whose audio nobody is taking.
 *
 * Judged by bytes accepted rather than by connected sockets: a paused or frozen phone player keeps its
 * connection open while it stops reading, so a socket count would call that session heard. Polled
 * rather than re-armed per frame, because a frame arrives every few tens of milliseconds and a timer
 * restarted on each of them would cost more than the check it replaces.
 */
class ListenAudienceWatchdog(
    private val scope: CoroutineScope,
    private val abandonAfterMs: Long,
    private val checkIntervalMs: Long,
    private val now: () -> Long,
    private val lastAudienceProgressAt: () -> Long,
    private val onAbandoned: () -> Unit
) {

    private var job: Job? = null

    /** A second start while one runs changes nothing: the deadline is read off the audience, not set here. */
    fun start() {
        if (job?.isActive == true) {
            return
        }
        job = scope.launch {
            while (!isAbandoned()) {
                delay(checkIntervalMs)
            }
            onAbandoned()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private fun isAbandoned(): Boolean = now() - lastAudienceProgressAt() >= abandonAfterMs
}
