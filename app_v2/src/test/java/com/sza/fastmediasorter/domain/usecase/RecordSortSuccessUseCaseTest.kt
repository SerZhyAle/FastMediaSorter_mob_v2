package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.preferences.ReviewEligibilityDataStore
import com.sza.fastmediasorter.data.local.preferences.ReviewEligibilitySnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordSortSuccessUseCaseTest {

    private val store = mockk<ReviewEligibilityDataStore>(relaxed = true)
    private val useCase = RecordSortSuccessUseCase(store)

    private fun stubSnapshot(ops: Long, sessions: Long, reviewShownEpoch: Long) {
        coEvery { store.incrementSortOps(any()) } returns ops
        coEvery { store.snapshot() } returns ReviewEligibilitySnapshot(
            sortOpsCount = ops,
            sessionsWithSort = sessions,
            reviewShownEpoch = reviewShownEpoch,
        )
    }

    @Test
    fun `eligible when all thresholds met and never shown`() = runTest {
        stubSnapshot(
            ops = RecordSortSuccessUseCase.OPS_THRESHOLD,
            sessions = RecordSortSuccessUseCase.SESSIONS_THRESHOLD,
            reviewShownEpoch = 0L,
        )
        assertTrue(useCase.record())
    }

    @Test
    fun `not eligible when ops below threshold`() = runTest {
        stubSnapshot(
            ops = RecordSortSuccessUseCase.OPS_THRESHOLD - 1,
            sessions = RecordSortSuccessUseCase.SESSIONS_THRESHOLD,
            reviewShownEpoch = 0L,
        )
        assertFalse(useCase.record())
    }

    @Test
    fun `not eligible when sessions below threshold`() = runTest {
        stubSnapshot(
            ops = RecordSortSuccessUseCase.OPS_THRESHOLD,
            sessions = RecordSortSuccessUseCase.SESSIONS_THRESHOLD - 1,
            reviewShownEpoch = 0L,
        )
        assertFalse(useCase.record())
    }

    @Test
    fun `not eligible while cooldown active`() = runTest {
        stubSnapshot(
            ops = RecordSortSuccessUseCase.OPS_THRESHOLD,
            sessions = RecordSortSuccessUseCase.SESSIONS_THRESHOLD,
            reviewShownEpoch = System.currentTimeMillis(), // shown just now
        )
        assertFalse(useCase.record())
    }

    @Test
    fun `eligible again after cooldown expired`() = runTest {
        stubSnapshot(
            ops = RecordSortSuccessUseCase.OPS_THRESHOLD,
            sessions = RecordSortSuccessUseCase.SESSIONS_THRESHOLD,
            reviewShownEpoch = System.currentTimeMillis() - RecordSortSuccessUseCase.COOLDOWN_MS - 1,
        )
        assertTrue(useCase.record())
    }

    @Test
    fun `record forwards count delta to store`() = runTest {
        stubSnapshot(ops = 1, sessions = 0, reviewShownEpoch = 0L)
        useCase.record(count = 5)
        coVerify { store.incrementSortOps(5L) }
    }
}
