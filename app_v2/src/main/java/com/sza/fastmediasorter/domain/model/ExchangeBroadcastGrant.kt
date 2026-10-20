package com.sza.fastmediasorter.domain.model

/**
 * The live broadcast given to one `guest` account of the same server (contract DEVICE-EXCHANGE item BD). It lives
 * and ends with the broadcast; its credential pair and relay group key stay in memory with it.
 */
data class ExchangeBroadcastGrant(
    val grantId: String,
    val guest: String,
    val broadcastId: String,
    val state: State = State.PENDING,
) {

    /** What the exchange server answered: nothing yet, `granted`, or `refused`. */
    enum class State { PENDING, GRANTED, REFUSED }

    // The grant id and the broadcast id are capabilities, so neither reaches a log line.
    override fun toString(): String = "ExchangeBroadcastGrant(guest=$guest, state=$state)"
}
