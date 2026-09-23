package com.sza.fastmediasorter.wear.domain.model

/**
 * S2550 ADR-2: where the watch is serving its microphone right now.
 *
 * The watch answers the phone's start command with this, so no discovery is built. The phone already
 * has an open control channel to a device that knows its own address, and mDNS or NSD would solve
 * "find a stranger" - a problem this feature does not have.
 *
 * Ephemeral by construction: it lives for one listening session and is never stored.
 */
data class LiveAudioEndpoint(val host: String, val port: Int) {

    /** ADR-4: an ordinary HTTP address, which is what lets the phone open it as it opens radio. */
    val url: String
        get() = "http://$host:$port$LISTEN_PATH"

    /**
     * LIVE-BROADCAST producer rule 1: a loopback host is never handed to anyone. The server reports
     * one only when no LAN interface was up at bind time, which makes it a refusal, not an address.
     */
    val isLoopback: Boolean
        get() = host.startsWith(LOOPBACK_PREFIX)

    companion object {
        private const val LOOPBACK_PREFIX = "127."

        /** The server answers this one path and does not look at it - see `LiveAudioLanServer`. */
        const val LISTEN_PATH = "/listen"
    }
}
