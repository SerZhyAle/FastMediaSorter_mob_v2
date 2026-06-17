package com.sza.fastmediasorter.data.stats

import com.sza.fastmediasorter.data.local.preferences.StatsAggregateDataStore
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.CaptureKind
import com.sza.fastmediasorter.domain.stats.FileOpAction
import com.sza.fastmediasorter.domain.stats.StatsAggregateDelta
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsKey
import com.sza.fastmediasorter.domain.stats.StatsMediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S0473: proves the two sink invariants statically -
 * ADR-5 (no-op when the gate is off) and ADR-6 (a batch of events folds into one [apply]).
 *
 * The sink's settings collector and debounced flush both run on the injected scope, so the test
 * scope's [StandardTestDispatcher] drives them. The settings flow is a [MutableStateFlow] (never
 * completes), so the collector is advanced with [runCurrent]; the debounce is crossed with
 * [advanceTimeBy] - never `advanceUntilIdle`, which would hang on the open collector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsSinkImplTest {

    private val flushDebounceMs = 2_500L

    private fun settingsRepo(enabled: Boolean): SettingsRepository {
        val flow = MutableStateFlow(AppSettings(enableStatistics = enabled))
        val repo = mockk<SettingsRepository>(relaxed = true)
        every { repo.getSettings() } returns flow
        return repo
    }

    // backgroundScope hosts the sink's never-completing settings collector; runTest cancels it
    // automatically at test end, so it does not trip UncompletedCoroutinesError. It shares the
    // test scheduler, so advanceTimeBy/runCurrent still drive the debounced flush.
    private fun TestScope.sinkWith(
        settings: SettingsRepository,
        aggregate: StatsAggregateDataStore
    ): StatsSinkImpl = StatsSinkImpl(
        settingsRepository = settings,
        aggregate = aggregate,
        scope = backgroundScope,
        io = StandardTestDispatcher(testScheduler)
    )

    @Test
    fun `gate OFF - record never writes to the aggregate store`() = runTest {
        val settings = settingsRepo(enabled = false)
        val aggregate = mockk<StatsAggregateDataStore>(relaxed = true)
        val sink = sinkWith(settings, aggregate)
        runCurrent() // let the settings collector publish enabled=false

        sink.record(StatsEvent.Capture(CaptureKind.PHOTO))
        sink.record(StatsEvent.FileOp(FileOpAction.COPY, StatsMediaType.IMAGE, 3L, 100L))
        advanceTimeBy(flushDebounceMs + 1)
        runCurrent()

        coVerify(exactly = 0) { aggregate.apply(any()) }
    }

    @Test
    fun `gate ON - a batch of events folds into a single apply after debounce`() = runTest {
        val settings = settingsRepo(enabled = true)
        val aggregate = mockk<StatsAggregateDataStore>(relaxed = true)
        val sink = sinkWith(settings, aggregate)
        runCurrent() // let the settings collector publish enabled=true

        val applied = slot<StatsAggregateDelta>()
        coEvery { aggregate.apply(capture(applied)) } returns Unit

        // Three events inside one debounce window must coalesce into a single disk write.
        sink.record(StatsEvent.Capture(CaptureKind.PHOTO))
        sink.record(StatsEvent.Capture(CaptureKind.PHOTO))
        sink.record(StatsEvent.FileOp(FileOpAction.COPY, StatsMediaType.IMAGE, 2L, 50L))
        advanceTimeBy(flushDebounceMs + 1)
        runCurrent()

        coVerify(exactly = 1) { aggregate.apply(any()) }
        // The two photo captures fold to PHOTOS_CAPTURED=2; the copy contributes its counters.
        assertEquals(2L, applied.captured.counters[StatsKey.PHOTOS_CAPTURED])
        assertEquals(2L, applied.captured.counters[StatsKey.FILES_COPIED])
        assertEquals(50L, applied.captured.counters[StatsKey.BYTES_COPIED])
    }

    @Test
    fun `gate ON - flushNow drains the buffer and the sink never wipes the baseline`() = runTest {
        val settings = settingsRepo(enabled = true)
        val aggregate = mockk<StatsAggregateDataStore>(relaxed = true)
        val sink = sinkWith(settings, aggregate)
        runCurrent()

        sink.record(StatsEvent.Capture(CaptureKind.SCREENSHOT))
        sink.flushNow()
        runCurrent()

        coVerify(exactly = 1) { aggregate.apply(any()) }
        // ADR-2: the detailed sink must never erase the always-on baseline; wipeDetailed is a
        // settings-toggle concern, not a write-path one, so the sink must not invoke it.
        coVerify(exactly = 0) { aggregate.wipeDetailed() }
    }
}
