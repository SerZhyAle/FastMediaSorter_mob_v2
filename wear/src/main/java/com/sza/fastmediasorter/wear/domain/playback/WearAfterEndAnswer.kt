package com.sza.fastmediasorter.wear.domain.playback

/**
 * The first answer of the one reconnect asked after a played broadcast's endpoint walk ran out (DEVICE-EXCHANGE item
 * DY, LIVE-BROADCAST item T).
 */
enum class WearAfterEndAnswer {
    /** Refused at once: a closed LAN port, or `404`/`410` from the LAN or the relay. */
    ENDED,

    /** Something answered with a status: the broadcast id is still known there. */
    ALIVE,

    /** Nothing answered at all - the watch lost its link, not the producer its broadcast. */
    NO_ANSWER,
}
