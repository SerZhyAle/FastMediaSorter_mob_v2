package com.sza.fastmediasorter.domain.model

/** Where a stream row imported from a link to a broadcast that has not started yet stands (S4247). */
sealed interface BroadcastStartState {
    data class Waiting(val untilMs: Long) : BroadcastStartState

    data object Started : BroadcastStartState

    /** The link's `until` passed, or the row has nothing to wait on. */
    data object Expired : BroadcastStartState
}
