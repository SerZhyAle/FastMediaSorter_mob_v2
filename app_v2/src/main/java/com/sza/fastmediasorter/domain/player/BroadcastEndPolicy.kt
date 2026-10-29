package com.sza.fastmediasorter.domain.player

/** What made a played broadcast stop (LIVE-BROADCAST item T). */
sealed interface BroadcastStopSignal {
    /** The stream reached its end: a clean end of body, or an RTSP session silent past its source timeout. */
    data object StreamEnded : BroadcastStopSignal

    /** The server answered a bad status; [code] is null where the status did not survive the transport. */
    data class HttpStatus(val code: Int?) : BroadcastStopSignal

    /**
     * The exchange directory, read complete, no longer lists the broadcast - read only once the stream stopped
     * delivering (item U, see [BroadcastRemovalHold]).
     */
    data object DirectoryRemoved : BroadcastStopSignal
}

/** The one reconnect attempted after a stop, as its first answer read it. */
enum class AfterEndAnswer {
    /** Refused at once: a closed port, `404`, `410`, or a tunnel refused as `ended` (DEVICE-EXCHANGE 1.10 item DY). */
    ENDED,

    /** Something answered with the stream. */
    ALIVE,

    /** Nothing answered at all. */
    NO_ANSWER,
}

/** What the player does about a stop. */
sealed interface BroadcastEndDecision {
    /** Show the ended state with an explicit reconnect, and try no more. */
    data object Ended : BroadcastEndDecision

    /** The broadcast answers again: play [url], resolved from the top of the row's endpoint list. */
    data class Reconnect(val url: String) : BroadcastEndDecision

    /** A lost connection: the bounded reconnect of section 5.2 item 3. */
    data object LostConnection : BroadcastEndDecision
}

/**
 * LIVE-BROADCAST item T on the consumer: the directory's removal read at the stop (item U) and a relay `410` are an
 * end with no question; a stream end
 * or another status is an end only when one reconnect is refused or answered `404`/`410`; an end followed by no
 * answer at all stays a lost connection.
 */
object BroadcastEndPolicy {

    private const val HTTP_GONE = 410

    fun needsCheck(signal: BroadcastStopSignal): Boolean = when (signal) {
        BroadcastStopSignal.DirectoryRemoved -> false
        is BroadcastStopSignal.HttpStatus -> signal.code != HTTP_GONE
        BroadcastStopSignal.StreamEnded -> true
    }

    /**
     * The decision for [signal] given the [answer] of the one reconnect; null when [answer] is [AfterEndAnswer.ALIVE],
     * which only the caller can turn into an address to play.
     */
    fun decide(signal: BroadcastStopSignal, answer: AfterEndAnswer?): BroadcastEndDecision? = when {
        !needsCheck(signal) -> BroadcastEndDecision.Ended
        answer == AfterEndAnswer.ENDED -> BroadcastEndDecision.Ended
        answer == AfterEndAnswer.ALIVE -> null
        else -> BroadcastEndDecision.LostConnection
    }
}

/**
 * LIVE-BROADCAST 1.6 item U: the directory's removal of a played broadcast, held while its stream still delivers - the
 * producer may have lost only the exchange server and still serve the LAN. The removal is read when the stream stops,
 * and at once when it arrives while the stream is not delivering (the after-end check, the bounded reconnect, the
 * question to the viewer).
 */
class BroadcastRemovalHold {

    private var removed = false
    private var delivering = false

    /** The stream delivers again. */
    fun onDelivering() {
        delivering = true
    }

    /** The directory's current word on the broadcast; true when a removal is to be read now. */
    fun onDirectory(removed: Boolean): Boolean {
        this.removed = removed
        return removed && !delivering
    }

    /** The stream stopped with [signal]; a held removal makes it the end it is. */
    fun atStop(signal: BroadcastStopSignal): BroadcastStopSignal {
        delivering = false
        return if (removed) BroadcastStopSignal.DirectoryRemoved else signal
    }

    fun reset() {
        removed = false
        delivering = false
    }
}

/**
 * The bounded reconnect of a lost connection: [max] consecutive attempts with no playback between them, after which
 * the viewer is asked. A playback that starts again refills it.
 */
class BroadcastReconnectBudget(private val max: Int = DEFAULT_MAX) {

    private var used = 0

    /** True when one more attempt is allowed, and counts it. */
    fun tryConsume(): Boolean = (used < max).also { if (it) used++ }

    /** The delay before the attempt [tryConsume] just counted: 2, 4, then 8 s. */
    fun delayMs(): Long = BASE_DELAY_MS shl (used - 1).coerceIn(0, MAX_SHIFT)

    fun reset() {
        used = 0
    }

    private companion object {
        const val DEFAULT_MAX = 3
        const val BASE_DELAY_MS = 2_000L
        const val MAX_SHIFT = 2
    }
}
