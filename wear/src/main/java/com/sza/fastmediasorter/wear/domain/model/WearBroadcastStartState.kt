package com.sza.fastmediasorter.wear.domain.model

/** S4692: where a handed-off link to a broadcast that has not started yet stands (LIVE-BROADCAST item S). */
sealed interface WearBroadcastStartState {

    data class Waiting(val untilMs: Long) : WearBroadcastStartState

    data object Started : WearBroadcastStartState

    /** The link's `until` passed, or the channel has nothing to wait on. */
    data object Expired : WearBroadcastStartState
}
