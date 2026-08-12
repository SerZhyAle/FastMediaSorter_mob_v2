package com.sza.fastmediasorter.core.playback.resilience

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization of the stall decision as `StreamStallWatchdog` made it before S1513 moved it.
 * Every threshold below was read out of that file, so a behaviour change during the move fails here
 * rather than on a car head unit.
 */
class StreamStallRuleTest {

    @Test
    fun `the four watchdog thresholds survive the move unchanged`() {
        assertEquals(POLL_INTERVAL_MS, StreamStallRule.POLL_INTERVAL_MS)
        assertEquals(MAX_EMPTY_POLLS, StreamStallRule.MAX_EMPTY_POLLS)
        assertEquals(MIN_POSITION_PROGRESS_MS, StreamStallRule.MIN_POSITION_PROGRESS_MS)
        assertEquals(BUFFERING_TIMEOUT_MS, StreamStallRule.BUFFERING_TIMEOUT_MS)
    }

    @Test
    fun `three consecutive polls without a new frame declare a video stall`() {
        val rule = StreamStallRule()
        rule.start(videoObservation())

        assertEquals(StreamStallOutcome.Progressing, rule.onPoll(videoObservation(), FIRST_POLL_MS))
        assertEquals(StreamStallOutcome.Progressing, rule.onPoll(videoObservation(), SECOND_POLL_MS))
        val third = rule.onPoll(videoObservation(), THIRD_POLL_MS)

        assertEquals(StreamStallOutcome.Stalled(StreamStallReason.RENDERED_FRAMES_FROZEN), third)
    }

    @Test
    fun `one rendered frame between polls clears the empty budget`() {
        val rule = StreamStallRule()
        rule.start(videoObservation())

        rule.onPoll(videoObservation(), FIRST_POLL_MS)
        rule.onPoll(videoObservation(), SECOND_POLL_MS)
        val advanced = rule.onPoll(videoObservation(renderedFrames = SECOND_FRAME_COUNT), THIRD_POLL_MS)
        val afterAdvance = rule.onPoll(videoObservation(renderedFrames = SECOND_FRAME_COUNT), FOURTH_POLL_MS)

        assertEquals(StreamStallOutcome.Progressing, advanced)
        assertEquals(StreamStallOutcome.Progressing, afterAdvance)
    }

    @Test
    fun `an absent frame counter answers no evidence and spends no budget`() {
        val rule = StreamStallRule()
        rule.start(videoObservation(renderedFrames = null))

        val firstBlind = rule.onPoll(videoObservation(renderedFrames = null), FIRST_POLL_MS)
        val secondBlind = rule.onPoll(videoObservation(renderedFrames = null), SECOND_POLL_MS)
        // The counter returns, but the first poll holding it still has nothing to compare against;
        // only the three frozen polls after that spend the budget the blind ones never touched.
        val counterBack = rule.onPoll(videoObservation(), THIRD_POLL_MS)

        assertEquals(StreamStallOutcome.NoEvidence(0L, isLive = false), firstBlind)
        assertEquals(StreamStallOutcome.NoEvidence(POLL_INTERVAL_MS, isLive = false), secondBlind)
        assertEquals(StreamStallOutcome.NoEvidence(2 * POLL_INTERVAL_MS, isLive = false), counterBack)
        assertEquals(StreamStallOutcome.Progressing, rule.onPoll(videoObservation(), FOURTH_POLL_MS))
        assertEquals(StreamStallOutcome.Progressing, rule.onPoll(videoObservation(), FIFTH_POLL_MS))
        val stalled = rule.onPoll(videoObservation(), SIXTH_POLL_MS)

        assertEquals(StreamStallOutcome.Stalled(StreamStallReason.RENDERED_FRAMES_FROZEN), stalled)
    }

    /**
     * S1467 §2: `currentPosition` is not a progress signal for a live stream. Media3 measures it
     * inside a sliding window that can move at playback speed while frames render normally, so the
     * rule has to answer progressing for a live video whose position never advances - the shape the
     * ticket blames for 34 forced re-anchors in a single session.
     *
     * What this test cannot settle for S1467: whether those 34 re-anchors on the car head unit came
     * from this false signal or from a link that really was failing. §2 states the attached log
     * cannot separate the two and names three release-log fields that would (rendition count,
     * decoder name and hardware flag, dropped frames beside each re-anchor); none of them is an
     * input to this rule, so that half stays a field question. The other half of §2 - a recovery
     * budget that reset on every `STATE_READY` - is not this rule's state either; it lives in
     * `StreamStallRecoveryWindow` and its own test already covers it.
     */
    @Test
    fun `live video with a frozen position keeps progressing while frames advance`() {
        val rule = StreamStallRule()
        rule.start(liveObservation(renderedFrames = FIRST_FRAME_COUNT))

        var frames = FIRST_FRAME_COUNT
        var nowMs = FIRST_POLL_MS
        repeat(POLLS_ACROSS_A_MINUTE) {
            frames += FRAMES_PER_POLL
            val outcome = rule.onPoll(liveObservation(renderedFrames = frames), nowMs)

            assertEquals(StreamStallOutcome.Progressing, outcome)
            nowMs += POLL_INTERVAL_MS
        }
    }

    @Test
    fun `audio only progress is position and the floor is inclusive`() {
        val rule = StreamStallRule()
        rule.start(audioObservation(START_POSITION_MS))

        assertEquals(
            StreamStallOutcome.Progressing,
            rule.onPoll(audioObservation(START_POSITION_MS + MIN_POSITION_PROGRESS_MS), FIRST_POLL_MS),
        )
    }

    @Test
    fun `three polls below the position floor declare an audio stall`() {
        val rule = StreamStallRule()
        rule.start(audioObservation(START_POSITION_MS))

        var positionMs = START_POSITION_MS
        var nowMs = FIRST_POLL_MS
        repeat(MAX_EMPTY_POLLS - 1) {
            positionMs += MIN_POSITION_PROGRESS_MS - ONE_MILLISECOND
            assertEquals(StreamStallOutcome.Progressing, rule.onPoll(audioObservation(positionMs), nowMs))
            nowMs += POLL_INTERVAL_MS
        }

        assertEquals(
            StreamStallOutcome.Stalled(StreamStallReason.POSITION_FROZEN),
            rule.onPoll(audioObservation(positionMs), nowMs),
        )
    }

    @Test
    fun `a buffering leg that is still filling does not time out`() {
        val rule = StreamStallRule()
        rule.onBufferingArmed(bufferingObservation(BUFFERED_AT_ARM_MS, isLoading = true), ARM_MS)

        val outcome = rule.onBufferingDeadline(
            bufferingObservation(BUFFERED_AT_ARM_MS + BUFFER_GROWTH_MS, isLoading = true),
            ARM_MS + BUFFERING_TIMEOUT_MS,
        )

        assertEquals(StreamStallOutcome.Progressing, outcome)
    }

    @Test
    fun `a buffering leg that stopped filling stalls at the timeout`() {
        val rule = StreamStallRule()
        rule.onBufferingArmed(bufferingObservation(BUFFERED_AT_ARM_MS, isLoading = true), ARM_MS)

        val stillLoading = rule.onBufferingDeadline(
            bufferingObservation(BUFFERED_AT_ARM_MS, isLoading = true),
            ARM_MS + BUFFERING_TIMEOUT_MS,
        )
        val noLongerLoading = rule.onBufferingDeadline(
            bufferingObservation(BUFFERED_AT_ARM_MS + BUFFER_GROWTH_MS, isLoading = false),
            ARM_MS + BUFFERING_TIMEOUT_MS,
        )

        assertEquals(StreamStallOutcome.Stalled(StreamStallReason.BUFFERING_TIMEOUT), stillLoading)
        assertEquals(StreamStallOutcome.Stalled(StreamStallReason.BUFFERING_TIMEOUT), noLongerLoading)
    }

    @Test
    fun `the buffering deadline cannot fire before its timeout has elapsed`() {
        val rule = StreamStallRule()
        rule.onBufferingArmed(bufferingObservation(BUFFERED_AT_ARM_MS, isLoading = false), ARM_MS)

        val outcome = rule.onBufferingDeadline(
            bufferingObservation(BUFFERED_AT_ARM_MS, isLoading = false),
            ARM_MS + BUFFERING_TIMEOUT_MS - ONE_MILLISECOND,
        )

        assertEquals(StreamStallOutcome.Progressing, outcome)
    }

    @Test
    fun `an unarmed buffering deadline answers no evidence rather than a stall`() {
        val rule = StreamStallRule()

        val outcome = rule.onBufferingDeadline(
            bufferingObservation(BUFFERED_AT_ARM_MS, isLoading = false),
            ARM_MS + BUFFERING_TIMEOUT_MS,
        )

        assertEquals(StreamStallOutcome.NoEvidence(blindForMs = 0L, isLive = false), outcome)
    }

    @Test
    fun `reset disarms the buffering deadline and the empty poll budget`() {
        val rule = StreamStallRule()
        rule.start(videoObservation())
        rule.onBufferingArmed(bufferingObservation(BUFFERED_AT_ARM_MS, isLoading = false), ARM_MS)
        rule.onPoll(videoObservation(), FIRST_POLL_MS)
        rule.onPoll(videoObservation(), SECOND_POLL_MS)

        rule.reset()
        rule.start(videoObservation())

        assertEquals(
            StreamStallOutcome.NoEvidence(blindForMs = 0L, isLive = false),
            rule.onBufferingDeadline(
                bufferingObservation(BUFFERED_AT_ARM_MS, isLoading = false),
                ARM_MS + BUFFERING_TIMEOUT_MS,
            ),
        )
        assertEquals(StreamStallOutcome.Progressing, rule.onPoll(videoObservation(), THIRD_POLL_MS))
    }

    private fun videoObservation(
        positionMs: Long = START_POSITION_MS,
        renderedFrames: Int? = FIRST_FRAME_COUNT,
        isLive: Boolean = false,
        bufferedDurationMs: Long = 0L,
        isLoading: Boolean = false,
    ) = StreamStallObservation(
        positionMs = positionMs,
        renderedFrames = renderedFrames,
        hasVideo = true,
        bufferedDurationMs = bufferedDurationMs,
        isLoading = isLoading,
        isLive = isLive,
    )

    private fun liveObservation(renderedFrames: Int?) =
        videoObservation(renderedFrames = renderedFrames, isLive = true)

    private fun audioObservation(positionMs: Long) = StreamStallObservation(
        positionMs = positionMs,
        renderedFrames = null,
        hasVideo = false,
        bufferedDurationMs = 0L,
        isLoading = false,
        isLive = false,
    )

    private fun bufferingObservation(bufferedDurationMs: Long, isLoading: Boolean) =
        videoObservation(bufferedDurationMs = bufferedDurationMs, isLoading = isLoading)

    private companion object {
        // StreamStallWatchdog before S1513: STALL_POLL_INTERVAL_MS, STALL_MAX_POLLS,
        // STALL_MIN_PROGRESS_MS, BUFFERING_STALL_TIMEOUT_MS - without digit separators, so a grep
        // for the plain threshold finds this file.
        const val POLL_INTERVAL_MS = 3000L
        const val MAX_EMPTY_POLLS = 3
        const val MIN_POSITION_PROGRESS_MS = 500L
        const val BUFFERING_TIMEOUT_MS = 15000L
        const val ONE_MILLISECOND = 1L
        const val START_POSITION_MS = 10_000L
        const val FIRST_FRAME_COUNT = 120
        const val SECOND_FRAME_COUNT = 121
        const val FRAMES_PER_POLL = 75
        const val POLLS_ACROSS_A_MINUTE = 20
        const val FIRST_POLL_MS = 3000L
        const val SECOND_POLL_MS = 6000L
        const val THIRD_POLL_MS = 9000L
        const val FOURTH_POLL_MS = 12000L
        const val FIFTH_POLL_MS = 15000L
        const val SIXTH_POLL_MS = 18000L
        const val ARM_MS = 50_000L
        const val BUFFERED_AT_ARM_MS = 2000L
        const val BUFFER_GROWTH_MS = 750L
    }
}
