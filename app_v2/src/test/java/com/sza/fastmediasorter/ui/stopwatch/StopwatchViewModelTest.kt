package com.sza.fastmediasorter.ui.stopwatch

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.util.ElapsedClock
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.stopwatch.ObserveStopwatchSettingsUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * S1411 phase 01 - the ViewModel drives the engine with the clock and never with the repaint ticker.
 *
 * Every command below is synchronous, so these cases need no coroutine harness. Running them under
 * `runTest` is not merely redundant, it hangs: the repaint ticker is an unbounded `delay` loop, and a
 * virtual-time scheduler skips that delay, so the test never reaches an idle scheduler and spins a core
 * until the build is killed (S2584).
 */
class StopwatchViewModelTest {

    /**
     * Phase 06 gave the ViewModel an `init` block that follows the settings store on `viewModelScope`,
     * and constructing one without a Main dispatcher throws before any assertion is reached.
     *
     * The rule's default [kotlinx.coroutines.test.StandardTestDispatcher] queues that collector without
     * running it, which is what these cases want twice over: nothing advances virtual time, so the
     * S2584 hang above stays impossible, and the collector never writes a participant count over the
     * one each case sets for itself.
     */
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeClock(var value: Long = 0L) : ElapsedClock {
        override fun nowMillis(): Long = value
    }

    private val clock = FakeClock()

    /** The measurement never asks about audio, so an all-capable record keeps these cases on topic. */
    private val mediaCapabilities = MediaCapabilities(
        supportsVideo = true,
        supportsAudio = true,
        supportsImages = true,
        supportsDocuments = true,
        supportsEpub = true,
        supportsCloud = true,
        supportsLocalNetworkSources = true,
        supportsDefaultPlayer = true,
        supportsCast = true,
        supportsMicRecording = true,
        supportsVrPlayer = false,
        supportsWearCompanion = true,
    )

    /**
     * A store that never emits. The measurement cases below are about the engine, not about settings,
     * and an empty flow keeps the ViewModel's `init` collector from writing a participant count over
     * the one each case sets for itself.
     */
    private val settingsRepository = mockk<SettingsRepository>().also {
        every { it.getSettings() } returns emptyFlow()
    }

    /**
     * The real use case over the faked repository rather than a mock of it: the projection it performs
     * is the thing the ViewModel actually consumes, so faking it would leave that mapping untested here
     * and everywhere else.
     */
    private val observeStopwatchSettings = ObserveStopwatchSettingsUseCase(settingsRepository)

    private fun viewModel() = StopwatchViewModel(clock, observeStopwatchSettings, mediaCapabilities)

    @Test
    fun `startOrLap starts a stopped participant`() {
        val model = viewModel()
        clock.value = 1_000L

        model.startOrLap(participantId = 0)

        assertTrue(model.state.value.participants[0].running)
        assertTrue(model.state.value.participants[0].laps.isEmpty())
    }

    @Test
    fun `startOrLap records a split on a running participant`() {
        val model = viewModel()
        clock.value = 1_000L
        model.startOrLap(participantId = 0)

        clock.value = 4_000L
        model.startOrLap(participantId = 0)

        val laps = model.state.value.participants[0].laps
        assertEquals(1, laps.size)
        assertEquals(3_000L, laps.first().atElapsedMillis)
        assertTrue(model.state.value.participants[0].running)
    }

    @Test
    fun `elapsed follows the clock without any repaint`() {
        val model = viewModel()
        clock.value = 0L
        model.startOrLap(participantId = 0)

        clock.value = 3_600_000L

        assertEquals(3_600_000L, model.elapsedOf(participantId = 0))
    }

    @Test
    fun `stopping one participant leaves another running`() {
        val model = viewModel()
        clock.value = 0L
        model.startOrLap(participantId = 0)
        model.startOrLap(participantId = 1)

        clock.value = 2_000L
        model.stop(participantId = 0)

        assertFalse(model.state.value.participants[0].running)
        assertTrue(model.state.value.participants[1].running)
        assertEquals(2_000L, model.state.value.participants[0].accumulatedMillis)
    }

    @Test
    fun `resetAll clears every participant`() {
        val model = viewModel()
        clock.value = 0L
        model.startOrLap(participantId = 0)
        clock.value = 5_000L
        model.stop(participantId = 0)

        model.resetAll()

        assertTrue(model.state.value.participants.none { it.hasProgress })
    }

    @Test
    fun `participant count is coerced to a supported value`() {
        val model = viewModel()

        model.setParticipantCount(3)

        assertEquals(2, model.state.value.participantCount)
    }
}
