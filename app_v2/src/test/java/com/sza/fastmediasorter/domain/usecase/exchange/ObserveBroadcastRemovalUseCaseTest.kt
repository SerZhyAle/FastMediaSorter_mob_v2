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

    private fun states(vararg listings: ExchangeDeviceBroadcasts): List<Boolean> = boundStates(null, *listings)

    private fun boundStates(entryKey: String?, vararg listings: ExchangeDeviceBroadcasts): List<Boolean> {
        var states = emptyList<Boolean>()
        runTest {
            val directory = mockk<ObserveAccountBroadcastsUseCase>()
            every { directory() } returns flowOf(*listings)
            states = ObserveBroadcastRemovalUseCase(directory)(ID, entryKey).toList()
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

    @Test
    fun boundWatch_readsItsEntryWhileAnotherIsPending() {
        val listed = listing(ID, entry = ENTRY_A, listed = setOf(ENTRY_A), pending = true)
        val removed = listing(entry = ENTRY_A, listed = setOf(ENTRY_A), pending = true)

        assertEquals(listOf(false, true), boundStates(ENTRY_A, listed, removed))
    }

    @Test
    fun boundWatch_ignoresAListingItsEntryDidNotAnswer() {
        val listed = listing(ID, entry = ENTRY_A, listed = setOf(ENTRY_A))
        val ownEntryLost = listing(entry = ENTRY_B, listed = setOf(ENTRY_B), incomplete = true)

        assertEquals(listOf(false), boundStates(ENTRY_A, listed, ownEntryLost))
    }

    @Test
    fun boundWatch_anotherEntryListingTheSameIdIsNotItsChannel() {
        val listed = listing(ID, entry = ENTRY_A, listed = setOf(ENTRY_A, ENTRY_B))
        val onlyTheOther = listing(ID, entry = ENTRY_B, listed = setOf(ENTRY_A, ENTRY_B))

        assertEquals(listOf(false, true), boundStates(ENTRY_A, listed, onlyTheOther))
    }

    private fun listing(
        vararg ids: String,
        pending: Boolean = false,
        incomplete: Boolean = false,
        entry: String = "",
        listed: Set<String> = emptySet(),
    ) = ExchangeDeviceBroadcasts(
        items = ids.map {
            ExchangeDeviceBroadcast(it, "phone", "title", "VIDEO_AUDIO", true, "{}", entryKey = entry)
        },
        pending = pending,
        incomplete = incomplete,
        listedSources = listed,
    )

    private companion object {
        const val ID = "broadcast-a"
        const val OTHER = "broadcast-b"
        const val ENTRY_A = "exchange-a.example.org:8443|alice"
        const val ENTRY_B = "exchange-b.example.org:8443|alice"
    }
}
