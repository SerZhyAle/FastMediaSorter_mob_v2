package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
import javax.inject.Inject

/**
 * The single entry of a deliberate end of the live broadcast (contract LIVE-BROADCAST item T, DEVICE-EXCHANGE item
 * DX): the person's stop, signing out, closing the app. A broadcast service calls it before it closes any stream, so
 * the exchange server hears `broadcast-end` and the relay upload ends cleanly while the streams are still open.
 *
 * Safe on the main thread: every network write runs elsewhere and every wait is bounded, so a dead server delays the
 * stop by at most the announcer's two bounds, and without an exchange entry nothing is waited for. A process killed
 * by the system never gets here, and stays a lost connection for its viewers.
 */
class AnnounceBroadcastEndUseCase @Inject constructor(
    private val announcer: ExchangeBroadcastAnnouncer,
) {

    /** True when `broadcast-end` was written; false with no announcement, no control stream or a failed write. */
    operator fun invoke(): Boolean {
        return announcer.endDeliberately()
    }

    companion object {
        /** How long one stream kind waits for its end signals - the RTCP BYE, the closing chunk - to be written. */
        const val STREAM_END_TIMEOUT_MS = 500L

        /** How long the app's full exit waits for a live broadcast to end in order before the process goes. */
        const val EXIT_WAIT_TIMEOUT_MS = 4_000L
    }
}
