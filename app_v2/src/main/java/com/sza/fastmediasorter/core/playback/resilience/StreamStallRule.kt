package com.sza.fastmediasorter.core.playback.resilience

/**
 * One sample of the player, reduced to values a JVM test can produce (S1513).
 *
 * Handing the rule a Media3 `Player` instead would let a later edit reach for a field no test can
 * fake, which is the exact reason the stall detector was unverifiable off-device until now.
 */
internal data class StreamStallObservation(
    /** Trusted as progress only where no frame counter exists - see [hasVideo] and S1467. */
    val positionMs: Long,
    /** Null when the engine reported no counter for this leg, which is its own answer. */
    val renderedFrames: Int?,
    /** A video track is selected, so frames rather than position carry the progress signal. */
    val hasVideo: Boolean,
    /** False when the host cannot show frames, so frame progress is not a stream-health signal. */
    val isVideoOutputExpected: Boolean,
    val bufferedDurationMs: Long,
    val isLoading: Boolean,
    /** Carried for the archive, not the decision - a no-evidence line has to name a live one (S1467). */
    val isLive: Boolean,
)

/**
 * Why the rule declared a stall.
 *
 * [diagnostic] is the wording the recovery line has carried since S1467 - it is what separates a
 * frozen renderer from a frozen position in an archived log, so the strings move verbatim.
 */
internal enum class StreamStallReason(val diagnostic: String) {
    RENDERED_FRAMES_FROZEN("rendered frames frozen"),
    POSITION_FROZEN("position frozen"),
    BUFFERING_TIMEOUT("buffering timeout"),
}

/**
 * The three answers a stall check can give.
 *
 * The third one exists because an engine that reports no frame counter must not be able to silently
 * disarm the watchdog: a rule that could not run has to sound different in the log from a rule that
 * ran and declined to fire (S1513 ADR-4).
 */
internal sealed interface StreamStallOutcome {

    /** The player moved, or it did not and the empty-poll budget is not spent yet: keep watching. */
    object Progressing : StreamStallOutcome

    /** The caller should run its recovery, quoting [reason] in the diagnostic. */
    data class Stalled(val reason: StreamStallReason) : StreamStallOutcome

    /** The host cannot render video now, so retain the current budget and wait for output to return. */
    object Suspended : StreamStallOutcome

    /**
     * The rule had nothing to judge. [blindForMs] is how long that has been true, so a log line can
     * distinguish one absent counter from an engine that never produced any; it is zero when the
     * rule was simply not armed.
     */
    data class NoEvidence(val blindForMs: Long, val isLive: Boolean) : StreamStallOutcome
}

/**
 * The silent-freeze decision of `StreamStallWatchdog`, lifted out of the player (S1513).
 *
 * Behaviour moved verbatim from the watchdog: a poll every [POLL_INTERVAL_MS], [MAX_EMPTY_POLLS]
 * consecutive polls without progress declare a stall, video progress is a rising rendered-frame
 * count, audio-only progress is [MIN_POSITION_PROGRESS_MS] of position, and a buffering leg that is
 * still filling never times out. Thresholds, budgets and wording are untouched - S1513 forbids
 * changing observable behaviour while relocating it.
 *
 * Time is a call parameter, never read inside: the class cannot reach a clock, and a test can play
 * a quarter of an hour in three lines (ADR-2). The two entry points that only seed or clear state
 * take no time because they measure none.
 */
internal class StreamStallRule(
    private val maxEmptyPolls: Int = MAX_EMPTY_POLLS,
    private val minPositionProgressMs: Long = MIN_POSITION_PROGRESS_MS,
    private val bufferingTimeoutMs: Long = BUFFERING_TIMEOUT_MS,
) {

    private var lastPositionMs = 0L
    private var lastRenderedFrames: Int? = null
    private var emptyPolls = 0
    private var blindSinceMs = NOT_STAMPED
    private var bufferingArmedAtMs = NOT_STAMPED
    private var bufferedAtArmMs = 0L

    /** Seed the baselines a first poll will be compared against, when playback reaches ready. */
    fun start(observation: StreamStallObservation) {
        lastPositionMs = observation.positionMs
        lastRenderedFrames = observation.renderedFrames
        emptyPolls = 0
        blindSinceMs = NOT_STAMPED
    }

    /** Forget everything, including the buffering deadline, when the watchdog is cancelled. */
    fun reset() {
        lastPositionMs = 0L
        lastRenderedFrames = null
        emptyPolls = 0
        blindSinceMs = NOT_STAMPED
        bufferingArmedAtMs = NOT_STAMPED
        bufferedAtArmMs = 0L
    }

    /**
     * Judge one progress poll of a playing, ready stream.
     *
     * The position baseline advances on every poll including a no-evidence one, matching the
     * watchdog: an absent frame counter must not leave the audio fallback comparing against a
     * position sampled minutes ago.
     */
    fun onPoll(observation: StreamStallObservation, nowMs: Long): StreamStallOutcome {
        val previousPositionMs = lastPositionMs
        lastPositionMs = observation.positionMs
        return if (observation.hasVideo && !observation.isVideoOutputExpected) {
            StreamStallOutcome.Suspended
        } else if (observation.hasVideo) {
            pollByRenderedFrames(observation, nowMs)
        } else {
            judgeProgress(
                progressed = observation.positionMs - previousPositionMs >= minPositionProgressMs,
                reason = StreamStallReason.POSITION_FROZEN,
            )
        }
    }

    /** Record the buffering leg the deadline will be judged against. */
    fun onBufferingArmed(observation: StreamStallObservation, nowMs: Long) {
        bufferingArmedAtMs = nowMs
        bufferedAtArmMs = observation.bufferedDurationMs
    }

    /**
     * Judge a buffering leg once its deadline is due.
     *
     * A stream still downloading on a weak link is legitimate, so a growing buffer with the engine
     * loading keeps the leg alive. The elapsed-time check is not the scheduler's job done twice: it
     * is what keeps the deadline on the caller's monotonic clock, and it is unreachable in
     * production because the caller only asks after its own delay has fired.
     */
    fun onBufferingDeadline(observation: StreamStallObservation, nowMs: Long): StreamStallOutcome {
        val armedAtMs = bufferingArmedAtMs
        if (armedAtMs == NOT_STAMPED) {
            return StreamStallOutcome.NoEvidence(blindForMs = 0L, isLive = observation.isLive)
        }
        val stillFilling = observation.isLoading && observation.bufferedDurationMs > bufferedAtArmMs
        val deadlineReached = nowMs - armedAtMs >= bufferingTimeoutMs
        return if (stillFilling || !deadlineReached) {
            StreamStallOutcome.Progressing
        } else {
            StreamStallOutcome.Stalled(StreamStallReason.BUFFERING_TIMEOUT)
        }
    }

    /**
     * A missing counter on either side of the comparison is the no-evidence case: with nothing to
     * subtract, falling back to position would answer "frozen" for a live window that is playing
     * perfectly, which is the defect S1467 describes.
     */
    private fun pollByRenderedFrames(
        observation: StreamStallObservation,
        nowMs: Long,
    ): StreamStallOutcome {
        val previousFrames = lastRenderedFrames
        val frames = observation.renderedFrames
        lastRenderedFrames = frames
        if (previousFrames == null || frames == null) {
            if (blindSinceMs == NOT_STAMPED) {
                blindSinceMs = nowMs
            }
            return StreamStallOutcome.NoEvidence(
                blindForMs = nowMs - blindSinceMs,
                isLive = observation.isLive,
            )
        }
        return judgeProgress(frames > previousFrames, StreamStallReason.RENDERED_FRAMES_FROZEN)
    }

    /** Counts empty polls and spends the budget; the counter restarts after it fires. */
    private fun judgeProgress(progressed: Boolean, reason: StreamStallReason): StreamStallOutcome {
        blindSinceMs = NOT_STAMPED
        emptyPolls = if (progressed) 0 else emptyPolls + 1
        return if (emptyPolls >= maxEmptyPolls) {
            emptyPolls = 0
            StreamStallOutcome.Stalled(reason)
        } else {
            StreamStallOutcome.Progressing
        }
    }

    companion object {

        /** Poll cadence the caller schedules with; the rule counts polls, not milliseconds. */
        const val POLL_INTERVAL_MS = 3_000L

        const val MAX_EMPTY_POLLS = 3

        /** Audio-only progress floor: less than this in one poll counts as no movement. */
        const val MIN_POSITION_PROGRESS_MS = 500L

        const val BUFFERING_TIMEOUT_MS = 15_000L

        private const val NOT_STAMPED = 0L
    }
}
