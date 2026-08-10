package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurementKind
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1433: the retention cap is the only thing between a diagnostic screen used in bursts and unbounded
 * database growth, and an off-by-one in the trim statement is invisible without a test - it would leave
 * 199 or 201 rows, either of which still looks like "the cap works".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkMeasurementHistoryRepositoryImplTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val repository by lazy {
        NetworkMeasurementHistoryRepositoryImpl(dbRule.db, dbRule.db.networkMeasurementDao())
    }

    @Test
    fun `the cap keeps the newest rows and drops the oldest`() = runTest {
        repeat(INSERTED_ROWS) { index ->
            repository.record(measurement(FIRST_TIMESTAMP + index))
        }

        val stored = repository.observeHistory().first()

        assertEquals("the cap must hold exactly, not approximately", CAP, stored.size)
        assertEquals(
            "the newest measurement must survive and lead the list",
            FIRST_TIMESTAMP + INSERTED_ROWS - 1,
            stored.first().takenAtMillis
        )
        assertEquals(
            "the oldest survivor is the row right after the five that were dropped",
            FIRST_TIMESTAMP + DROPPED_ROWS,
            stored.last().takenAtMillis
        )
        assertTrue(
            "no row older than the cut-off may survive",
            stored.none { it.takenAtMillis < FIRST_TIMESTAMP + DROPPED_ROWS }
        )
    }

    @Test
    fun `clear leaves nothing behind`() = runTest {
        repeat(FEW_ROWS) { index ->
            repository.record(measurement(FIRST_TIMESTAMP + index))
        }

        repository.clear()

        assertEquals(0, repository.observeHistory().first().size)
    }

    private fun measurement(takenAtMillis: Long): NetworkMeasurement = NetworkMeasurement(
        takenAtMillis = takenAtMillis,
        kind = NetworkMeasurementKind.SPEED_TEST,
        networkLabel = "wifi",
        resultText = "measured",
        succeeded = true
    )

    private companion object {
        const val CAP = 200
        const val INSERTED_ROWS = 205
        const val DROPPED_ROWS = INSERTED_ROWS - CAP
        const val FEW_ROWS = 3
        const val FIRST_TIMESTAMP = 1_000L
    }
}
