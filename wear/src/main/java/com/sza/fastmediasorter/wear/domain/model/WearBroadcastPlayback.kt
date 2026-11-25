package com.sza.fastmediasorter.wear.domain.model

import com.sza.fastmediasorter.wear.data.exchange.WearDirectoryBroadcast

/** What a tap on a listed broadcast leads to: the player to open, or the reason nothing plays. */
sealed interface WearBroadcastOpening {

    data class Open(val target: WearStreamPlaybackTarget) : WearBroadcastOpening

    data class Unavailable(val reason: WearBroadcastPlayback.Reason) : WearBroadcastOpening
}

/** What a tap on a listed broadcast resolves to: an address a player can open now, or the reason it cannot. */
sealed interface WearBroadcastPlayback {

    /**
     * [url] is a LAN address or a loopback address of the pinning proxy; it never carries a password. [route] is
     * what a player needs to walk on from this endpoint when it fails (S4610).
     */
    data class Playable(
        val url: String,
        val title: String?,
        val isVideo: Boolean,
        val route: WearBroadcastRoute? = null,
    ) : WearBroadcastPlayback

    data class Unavailable(val reason: Reason) : WearBroadcastPlayback

    enum class Reason {
        /** A mode the watch does not decode (contract DEVICE-EXCHANGE item CH). */
        MODE,

        /** The record names no endpoint the watch implements. */
        NO_ENDPOINT,

        /** A protected broadcast without a relay endpoint or without the account's credentials (LIVE-BROADCAST M). */
        PROTECTED,

        /** Every endpoint the watch can use failed to answer, or the broadcast ended before the tap. */
        UNREACHABLE,

        /** A broadcast descriptor with an unsupported schemaVersion (LIVE-BROADCAST 1.1). */
        FORMAT_UNSUPPORTED,
    }
}

/**
 * S4610: the broadcast a player opened and the index of the playable endpoint it plays, so a failure moves on to the
 * next endpoint and a reconnect starts at the top again (LIVE-BROADCAST 12, DEVICE-EXCHANGE 7.7). The record keeps
 * the account's credentials in memory beside the player instead of in the URL (LIVE-BROADCAST item M).
 */
class WearBroadcastRoute(val record: WearDirectoryBroadcast, val endpointIndex: Int) {

    // DEVICE-EXCHANGE 7.9: the broadcast id is a capability, and the record carries the password.
    override fun toString(): String = "WearBroadcastRoute(endpointIndex=$endpointIndex)"
}
