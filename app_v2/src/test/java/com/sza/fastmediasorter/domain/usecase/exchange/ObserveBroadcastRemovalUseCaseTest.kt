package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.domain.model.ExchangeDeviceBroadcast
import com.sza.fastmediasorter.domain.model.ExchangeDeviceBroadcasts
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveBroadcastRemovalUseCaseTest {

    private fun states(vararg listings: ExchangeDeviceBroadcasts): List<Boolean> {
        var states = emptyList<Boolean>()
        runTest {
            val directory = mockk<ObserveAccountBroadcastsUseCase>()
            every { directory() } returns flowOf(*listings)
            states = ObserveBroadcastRemovalUseCase(directory)(ID).toList()
        }
        return states
    }

    @Test
    fun presentThenAbsentInCompleteListings_isARemoval() {
        assertEquals(listOf(false, true), states(listing(ID), listing()))
    }

    @Test
    fun absenceFromAnIncompleteOrPendingListing_isNoRemoval() {
        assertEquals(listOf(false), states(listing(ID), listing(incomplete = true), listing(pending = true)))
    }

    @Test
    fun aBroadcastNeverListed_isNeverRemoved() {
        assertEquals(emptyList<Boolean>(), states(listing(), listing(OTHER), listing()))
    }

    @Test
    fun aPartialAbsence_thenACompleteOne_isOneRemoval() {
        assertEquals(listOf(false, true), states(listing(ID), listing(incomplete = true), listing(OTHER), listing()))
    }

    @Test
    fun aBroadcastAnnouncedAgain_isNoLongerRemoved() {
        assertEquals(listOf(false, true, false), states(listing(ID), listing(), listing(ID, OTHER)))
    }

    private fun listing(vararg ids: String, pending: Boolean = false, incomplete: Boolean = false) =
        ExchangeDeviceBroadcasts(
            items = ids.map { ExchangeDeviceBroadcast(it, "phone", "title", "VIDEO_AUDIO", true, "{}") },
            pending = pending,
            incomplete = incomplete,
        )

    private companion object {
        const val ID = "broadcast-a"
        const val OTHER = "broadcast-b"
    }
}
