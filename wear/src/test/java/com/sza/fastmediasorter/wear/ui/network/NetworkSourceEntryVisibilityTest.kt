package com.sza.fastmediasorter.wear.ui.network

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1707 phase 01: the store build must offer no way to type a username or a password on the watch
 * (Wear review item WO-P6), while everything already stored keeps working.
 */
class NetworkSourceEntryVisibilityTest {

    @Test
    fun `entry is offered in a debug build`() {
        assertTrue(NetworkSourceEntry.isOffered(isDebugBuild = true))
    }

    @Test
    fun `entry is withheld in a store build`() {
        assertFalse(NetworkSourceEntry.isOffered(isDebugBuild = false))
    }

    /**
     * The half a user would notice if the gate were implemented by deleting rather than hiding: a source
     * saved earlier, or transferred from the phone, is still readable in a build that offers no way to add
     * one. Asserted against the flag both ways so a later edit cannot quietly make reading conditional.
     */
    @Test
    fun `a stored source stays readable whichever way the gate is set`() = runTest {
        val repository = StoredSourceRepository(SAVED_SOURCE)

        for (isDebugBuild in listOf(true, false)) {
            NetworkSourceEntry.isOffered(isDebugBuild)

            assertEquals(listOf(SAVED_SOURCE), repository.getAllSources())
            assertNotNull(repository.getSourceById(SAVED_SOURCE.id))
        }
    }

    /** Reads back exactly what it was constructed with; writing is not what this test is about. */
    private class StoredSourceRepository(private vararg val stored: NetworkSource) : NetworkSourceRepository {

        override suspend fun getAllSources(): List<NetworkSource> = stored.toList()

        override fun observeSources(): Flow<List<NetworkSource>> = flowOf(stored.toList())

        override suspend fun getSourceById(id: String): NetworkSource? = stored.firstOrNull { it.id == id }

        override suspend fun addSource(source: NetworkSource) = Unit

        override suspend fun updateSource(source: NetworkSource) = Unit

        override suspend fun upsertSource(source: NetworkSource) = Unit

        override suspend fun deleteSource(id: String) = Unit

        override suspend fun testConnection(source: NetworkSource): Result<Boolean> = Result.success(true)
    }

    private companion object {
        val SAVED_SOURCE = NetworkSource(
            id = "saved-before-the-gate",
            type = NetworkSourceType.SMB,
            name = "Home share",
            server = "192.168.1.10",
            username = "user",
            password = "secret",
            shareName = "media",
        )
    }
}
