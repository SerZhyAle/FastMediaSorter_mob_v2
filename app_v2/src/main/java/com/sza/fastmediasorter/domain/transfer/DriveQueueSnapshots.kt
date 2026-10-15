package com.sza.fastmediasorter.domain.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** S4231: why a surface wants the queue polled (contract DEVICE-EXCHANGE item CQ). */
enum class PollDemand {
    /** A screen waits for a reply, a send runs, or the queue screen is open: every 10 seconds. */
    ACTIVE,

    /** The program runs in the foreground with nothing waiting: every 60 seconds. */
    FOREGROUND
}

/**
 * S4231: the one poll of the Drive queue every consumer shares. A consumer holds a demand while it
 * needs fresh listings and closes it when it stops; the poll interval follows the strongest demand.
 */
interface DriveQueueSnapshots {

    /** The latest listing; replays the last one to a new collector. */
    val snapshots: SharedFlow<CrossDeviceQueueSnapshot>

    /** What the poll is doing, for the surface that explains a wait or a stop. */
    val pollState: StateFlow<DrivePollState>

    fun acquire(demand: PollDemand): AutoCloseable
}

/** S4231: the shared poll as a surface describes it (contract items CQ, CS, CT). */
sealed interface DrivePollState {
    /** The channel is off, or nothing needs the queue right now. */
    data object Off : DrivePollState

    data class Polling(val intervalMs: Long) : DrivePollState

    /** Drive asked to slow down or did not answer; the next listing is at [untilMs] (device clock). */
    data class BackingOff(val untilMs: Long) : DrivePollState

    /** The token was revoked or expired; nothing was deleted and polling waits for a new sign-in. */
    data object SignInRequired : DrivePollState

    /** Drives daily upload limit; listings continue, uploads are not retried in a loop. */
    data object DailyLimit : DrivePollState
}
