package com.sza.fastmediasorter.wear.domain.listen

import java.util.concurrent.atomic.AtomicReference

/** S2550: the phone that asked, and the request its answer belongs to. */
data class ListenRequester(val nodeId: String, val requestId: String)

/**
 * The single application-scoped record of who is waiting for an answer.
 *
 * Application-scoped for the same reason as [ListenSessionStateHolder], and for one more: the answer
 * is put on the wire by the capture service, minutes after the screen that took the tap has gone
 * dark, and a stop command may arrive later still and needs the same node to answer. A registry
 * scoped to the confirmation screen would leave both of those with nowhere to send.
 *
 * It holds one requester because only one session exists at a time. The reference is atomic and
 * [take] is the only way to end an exchange: the expiry timer and the owner's decline can reach this
 * within the same instant from two threads, and two answers to one request tell the phone both that
 * it was refused and that nobody ever looked.
 */
class ListenRequestRegistry {

    private val current = AtomicReference<ListenRequester?>(null)

    fun remember(requester: ListenRequester) {
        current.set(requester)
    }

    /** Who to answer, leaving the exchange open - for the address, which a stop still follows. */
    fun peek(): ListenRequester? = current.get()

    /** Who to answer, ending the exchange. Null means someone else already answered. */
    fun take(): ListenRequester? = current.getAndSet(null)

    fun clear() {
        current.set(null)
    }
}
