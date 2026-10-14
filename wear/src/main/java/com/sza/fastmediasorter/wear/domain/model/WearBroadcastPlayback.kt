package com.sza.fastmediasorter.wear.domain.model

/** What a tap on a listed broadcast leads to: the player to open, or the reason nothing plays. */
sealed interface WearBroadcastOpening {

    data class Open(val target: WearStreamPlaybackTarget) : WearBroadcastOpening

    data class Unavailable(val reason: WearBroadcastPlayback.Reason) : WearBroadcastOpening
}

/** What a tap on a listed broadcast resolves to: an address a player can open now, or the reason it cannot. */
sealed interface WearBroadcastPlayback {

    /** [url] is a LAN address or a loopback address of the pinning proxy; it never carries a password. */
    data class Playable(val url: String, val title: String?, val isVideo: Boolean) : WearBroadcastPlayback

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
    }
}
