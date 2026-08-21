package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1836: a remembered source can be deleted on the watch or stop arriving from the phone, and an entry
 * stored by an older build carries no identifier at all. Neither is reachable on a device without
 * deleting a source by hand, so the rule that hides the row is pinned here instead.
 */
class ResolveLastUsedResourceUseCaseTest {

    private val preferences: WearPreferencesRepository = mockk()
    private val sources: NetworkSourceRepository = mockk()

    @Test
    fun `a remembered source that is still listed resolves to itself`() = runTest {
        every { preferences.lastUsedResource } returns flowOf(LastUsedResource(SOURCE_ID, SOURCE_NAME))
        every { sources.observeSources() } returns flowOf(listOf(source(SOURCE_ID), source(OTHER_ID)))

        assertEquals(LastUsedResource(SOURCE_ID, SOURCE_NAME), useCase().first())
    }

    @Test
    fun `a remembered source that is gone resolves to nothing`() = runTest {
        every { preferences.lastUsedResource } returns flowOf(LastUsedResource(SOURCE_ID, SOURCE_NAME))
        every { sources.observeSources() } returns flowOf(listOf(source(OTHER_ID)))

        assertNull(useCase().first())
    }

    @Test
    fun `nothing remembered resolves to nothing`() = runTest {
        every { preferences.lastUsedResource } returns flowOf(null)
        every { sources.observeSources() } returns flowOf(listOf(source(SOURCE_ID)))

        assertNull(useCase().first())
    }

    // A property, not a function: `useCase()` has to reach the use case's own invoke operator.
    private val useCase get() = ResolveLastUsedResourceUseCase(preferences, sources)

    private fun source(id: String) = NetworkSource(
        id = id,
        type = NetworkSourceType.SMB,
        name = "any",
        server = "192.168.0.2",
        username = "user",
        password = "secret"
    )

    private companion object {
        const val SOURCE_ID = "src-7"
        const val OTHER_ID = "src-9"
        const val SOURCE_NAME = "MyNAS"
    }
}
