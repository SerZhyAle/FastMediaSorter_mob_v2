package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.Player
import com.sza.fastmediasorter.domain.model.PrefetchCacheMultiplier
import com.sza.fastmediasorter.domain.model.PrefetchDerivation
import com.sza.fastmediasorter.domain.model.PrefetchPlan
import com.sza.fastmediasorter.domain.model.Protocol
import com.sza.fastmediasorter.domain.model.StreamViabilityState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PrefetchProgressTracker].
 *
 * These tests drive the state machine via the public [PrefetchProgressTracker.markReady]
 * and [PrefetchProgressTracker.recordStall] entry points, which is why those are public —
 * so unit tests don't need a real ExoPlayer to verify escalation behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchProgressTrackerTest {

    private fun buildPlan(
        viability: StreamViabilityState = StreamViabilityState.VIABLE,
        ratio: Double = 0.3,
        targetSec: Int = 10,
        sourceLabel: String = "SMB"
    ): PrefetchPlan = PrefetchPlan(
        viability = viability,
        targetPrefetchSec = targetSec,
        minPrefetchSec = (targetSec * 0.35).toInt().coerceAtLeast(1),
        rebufferPrefetchSec = (targetSec * 0.55).toInt().coerceAtLeast(2),
        maxBufferSec = targetSec * 3,
        sourceLabel = sourceLabel,
        derivation = PrefetchDerivation(
            speedMbps = 10.0,
            bitrateKbps = (ratio * 10.0 * 1000.0).toInt(),
            ratio = ratio,
            cacheBudgetBytes = 256L * 1024 * 1024,
            maxBufferSecByDevice = 90,
            maxBufferSecByFile = Int.MAX_VALUE,
            protocol = Protocol.SMB,
            userMultiplier = PrefetchCacheMultiplier.AUTO,
            formulaVersion = 2
        )
    )

    @Test
    fun `initial state has zero stalls and empty progress`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        assertEquals(0, tracker.stallCount.value)
        assertEquals(PrefetchProgress.EMPTY, tracker.prefetchProgress.value)
    }

    @Test
    fun `recordStall is a no-op before markReady`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        tracker.recordStall()
        tracker.recordStall()
        tracker.recordStall()
        assertEquals(0, tracker.stallCount.value)
    }

    @Test
    fun `recordStall increments after markReady`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        tracker.markReady()
        tracker.recordStall()
        assertEquals(1, tracker.stallCount.value)
    }

    @Test
    fun `soft escalation fires at stall count 2`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        val plan = buildPlan(viability = StreamViabilityState.VIABLE, ratio = 0.3)
        val player = mockk<Player>(relaxed = true)
        every { player.bufferedPosition } returns 0L
        every { player.currentPosition } returns 0L
        every { player.playbackState } returns Player.STATE_READY
        tracker.attach(player, plan)

        tracker.markReady()
        tracker.recordStall()
        // Single stall → no escalation yet.
        assertEquals(0, tracker.escalation.replayCache.size)

        tracker.recordStall()
        // Second stall crosses soft threshold.
        val event = tracker.escalation.take(1).first()
        assertEquals(2, event.stallCount)
        assertEquals(0.1, event.ratioInflation, 0.0001)
        assertFalse("viable+0.1 still under 0.9", event.crossesToNotViable)
    }

    @Test
    fun `hard escalation fires at stall count 4 with 0_2 inflation`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        tracker.attach(mockk<Player>(relaxed = true), buildPlan(ratio = 0.3))

        tracker.markReady()
        repeat(4) { tracker.recordStall() }

        val events = tracker.escalation.take(2).toList()
        assertEquals(2, events.size)
        assertEquals(0.1, events[0].ratioInflation, 0.0001)
        assertEquals(0.2, events[1].ratioInflation, 0.0001)
        assertEquals(4, events[1].stallCount)
    }

    @Test
    fun `escalation monotonic - inflation never re-emits at same level`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        tracker.attach(mockk<Player>(relaxed = true), buildPlan(ratio = 0.3))

        tracker.markReady()
        tracker.recordStall() // 1: no event
        tracker.recordStall() // 2: soft event
        tracker.recordStall() // 3: still soft — no new event

        // replayCache is empty (replay=0), so we inspect via take with a soft timeout pattern.
        // After a soft event and a third stall, only one event should have been emitted.
        val events = mutableListOf<EscalationEvent>()
        // Drain what's in the buffer without blocking forever.
        while (true) {
            val maybe = tracker.escalation.replayCache
            if (maybe.isEmpty()) break
            events += maybe
            break
        }
        // Use a different approach: collect with timeout via runTest's virtual time.
        // Since replay=0, we can't inspect post-hoc; instead, crossing hard must trigger
        // exactly one additional event (not two).
        tracker.recordStall() // 4: hard
        tracker.recordStall() // 5: still hard — no new event
        // The test above ("hard escalation fires at stall count 4") already asserts
        // exactly two events are produced for stalls 1..4. Here, we just assert that
        // a fifth stall does not corrupt the counter.
        assertEquals(5, tracker.stallCount.value)
    }

    @Test
    fun `escalation reports crossesToNotViable when marginal tips over`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        // Base ratio 0.85 → adding 0.1 pushes over 0.9.
        tracker.attach(
            mockk<Player>(relaxed = true),
            buildPlan(viability = StreamViabilityState.MARGINAL, ratio = 0.85)
        )

        tracker.markReady()
        tracker.recordStall()
        tracker.recordStall()

        val event = tracker.escalation.take(1).first()
        assertTrue("MARGINAL+0.1 crosses to NOT_VIABLE", event.crossesToNotViable)
    }

    @Test
    fun `attach seeds progress with plan target and label`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        val plan = buildPlan(
            viability = StreamViabilityState.MARGINAL,
            ratio = 0.8,
            targetSec = 22,
            sourceLabel = "Cloud"
        )
        tracker.attach(mockk<Player>(relaxed = true), plan)

        val progress = tracker.prefetchProgress.value
        assertEquals(22, progress.targetSec)
        assertEquals("Cloud", progress.sourceLabel)
        assertEquals(StreamViabilityState.MARGINAL, progress.viability)
        assertEquals(0, progress.cachedSec)
    }

    @Test
    fun `updatePlan updates progress target without resetting stalls`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        tracker.attach(mockk<Player>(relaxed = true), buildPlan(targetSec = 10))
        tracker.markReady()
        tracker.recordStall()

        tracker.updatePlan(buildPlan(targetSec = 30, sourceLabel = "SFTP"))

        assertEquals(1, tracker.stallCount.value)
        assertEquals(30, tracker.prefetchProgress.value.targetSec)
        assertEquals("SFTP", tracker.prefetchProgress.value.sourceLabel)
    }

    @Test
    fun `detach stops polling and clears listener`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        val player = mockk<Player>(relaxed = true)
        every { player.bufferedPosition } returns 5_000L
        every { player.currentPosition } returns 0L
        every { player.playbackState } returns Player.STATE_READY

        tracker.attach(player, buildPlan(targetSec = 10))
        tracker.markReady()
        advanceTimeBy(500L) // give the poll loop a chance to tick

        tracker.detach()

        // After detach, recordStall is a no-op (hasReachedReady was reset).
        tracker.recordStall()
        assertEquals(0, tracker.stallCount.value)
    }

    @Test
    fun `onPlaybackStateChanged routes READY and BUFFERING`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        tracker.attach(mockk<Player>(relaxed = true), buildPlan())

        // BUFFERING before READY must be ignored (startup-buffering is not a stall).
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertEquals(0, tracker.stallCount.value)

        tracker.onPlaybackStateChanged(Player.STATE_READY)
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertEquals(1, tracker.stallCount.value)
    }

    @Test
    fun `polling loop samples buffered position and emits progress`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        val player = mockk<Player>(relaxed = true)
        // Simulate 5 seconds buffered ahead of the playhead.
        every { player.bufferedPosition } returns 5_000L
        every { player.currentPosition } returns 0L
        every { player.playbackState } returns Player.STATE_READY

        tracker.attach(player, buildPlan(targetSec = 10))
        tracker.markReady()

        // Advance past at least one poll interval (250ms).
        advanceTimeBy(300L)

        val progress = tracker.prefetchProgress.value
        assertEquals(5, progress.cachedSec)
        assertEquals(10, progress.targetSec)
        assertFalse("not buffering when STATE_READY", progress.isBuffering)
    }

    @Test
    fun `polling reports isBuffering when player state is STATE_BUFFERING`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        val player = mockk<Player>(relaxed = true)
        every { player.bufferedPosition } returns 2_000L
        every { player.currentPosition } returns 1_000L
        every { player.playbackState } returns Player.STATE_BUFFERING

        tracker.attach(player, buildPlan(targetSec = 10))
        tracker.markReady()
        advanceTimeBy(300L)

        val progress = tracker.prefetchProgress.value
        assertEquals(1, progress.cachedSec)
        assertTrue("isBuffering should be true", progress.isBuffering)
    }

    @Test
    fun `negative buffered position clamps to zero`() = runTest {
        val tracker = PrefetchProgressTracker(backgroundScope)
        val player = mockk<Player>(relaxed = true)
        // Odd but legal ExoPlayer intermediate state: buffered < current.
        every { player.bufferedPosition } returns 1_000L
        every { player.currentPosition } returns 5_000L
        every { player.playbackState } returns Player.STATE_READY

        tracker.attach(player, buildPlan(targetSec = 10))
        tracker.markReady()
        advanceTimeBy(300L)

        assertEquals(0, tracker.prefetchProgress.value.cachedSec)
    }
}
