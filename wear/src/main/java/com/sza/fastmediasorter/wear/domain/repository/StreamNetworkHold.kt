package com.sza.fastmediasorter.wear.domain.repository

import java.net.InetAddress

/**
 * S2509: a network the watch has asked the platform to keep alive, and the address it can be served on.
 *
 * The address is the reason this is not simply a counted hold. A broadcast is served by a socket the
 * watch itself binds, so knowing that "some network is up" is not enough - the server has to bind to
 * the interface the hold applies to, or the listener is told to connect somewhere the hold does not
 * cover. [release] is owed on every path, including a failed start.
 */
interface BroadcastNetworkLease {

    /** The watch's own address on the held network - what a listener's descriptor points at. */
    val address: InetAddress

    /** Gives the network back. Idempotent, so a stop racing a failure cannot release twice. */
    fun release()
}

/**
 * Holds a wide-band network for exactly as long as [withWideChannel]'s block runs, and - since S2509 -
 * lends one to a broadcast that must outlive any single block.
 *
 * The block form is scoped on purpose: its release lives in the implementation's `finally`, so a
 * caller cannot acquire the channel and forget to give it back. A radio held after playback ends is
 * the main cost of this whole approach, and the shape is what keeps it from being paid by accident.
 */
interface StreamNetworkHold {

    suspend fun <T> withWideChannel(block: suspend () -> T): T

    /**
     * S2509: takes a usable network for a broadcast session, or answers `null` when there is none.
     *
     * Not block-scoped like the form above, because the session it serves is started by one intent and
     * ended by another with the screen free to go dark in between - there is no block to wrap. The
     * caller owns the returned lease and must [BroadcastNetworkLease.release] it on stop and on every
     * failed-start path.
     *
     * `null` is a real answer and not an error: a watch with no Wi-Fi up cannot be reached by a
     * listener at all, and the owner is owed that sentence rather than a broadcast that appears to
     * start and serves an address nobody can open.
     */
    suspend fun acquireBroadcastNetwork(): BroadcastNetworkLease?
}
