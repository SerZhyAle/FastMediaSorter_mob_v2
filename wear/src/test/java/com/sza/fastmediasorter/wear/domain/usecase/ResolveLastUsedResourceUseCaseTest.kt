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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1836: a remembered source can be deleted on the watch or stop arriving from the phone, and an entry
 * stored by an older build carries no identifier at all. Neither is reachable on a device without
 * deleting a source by hand, so the rule that hides the cell is pinned here instead.
 *
 * S2129: the same lookup now also carries the source's icon onto the entry. Without a case asserting
 * it, reverting the enrichment to the plain filter it replaced would leave every test green while the
 * home screen went back to one shared history glyph.
 */
class ResolveLastUsedResourceUseCaseTest {

    private val preferences: WearPreferencesRepository = mockk()
    private val sources: NetworkSourceRepository = mockk()

    @Test
    fun `a remembered source that is still listed resolves to itself`() {
        runTest {
            every { preferences.lastUsedResources } returns flowOf(listOf(LastUsedResource(SOURCE_ID, SOURCE_NAME)))
            every { sources.observeSources() } returns flowOf(listOf(source(SOURCE_ID), source(OTHER_ID)))

            assertEquals(listOf(LastUsedResource(SOURCE_ID, SOURCE_NAME)), useCase().first())
        }
    }

    @Test
    fun `a gone source is dropped while the rest of the history stays`() {
        runTest {
            val remembered = listOf(LastUsedResource(OTHER_ID, OTHER_NAME), LastUsedResource(SOURCE_ID, SOURCE_NAME))
            every { preferences.lastUsedResources } returns flowOf(remembered)
            every { sources.observeSources() } returns flowOf(listOf(source(SOURCE_ID)))

            assertEquals(listOf(LastUsedResource(SOURCE_ID, SOURCE_NAME)), useCase().first())
        }
    }

    @Test
    fun `the stored order survives the filter`() {
        runTest {
            val remembered = listOf(LastUsedResource(OTHER_ID, OTHER_NAME), LastUsedResource(SOURCE_ID, SOURCE_NAME))
            every { preferences.lastUsedResources } returns flowOf(remembered)
            every { sources.observeSources() } returns flowOf(listOf(source(SOURCE_ID), source(OTHER_ID)))

            assertEquals(remembered, useCase().first())
        }
    }

    @Test
    fun `nothing remembered resolves to nothing`() {
        runTest {
            every { preferences.lastUsedResources } returns flowOf(emptyList())
            every { sources.observeSources() } returns flowOf(listOf(source(SOURCE_ID)))

            assertTrue(useCase().first().isEmpty())
        }
    }

    @Test
    fun `a resolved entry carries the icon of the source it matched`() {
        runTest {
            every { preferences.lastUsedResources } returns flowOf(listOf(LastUsedResource(SOURCE_ID, SOURCE_NAME)))
            every { sources.observeSources() } returns flowOf(listOf(source(SOURCE_ID, ICON_ID)))

            assertEquals(ICON_ID, useCase().first().single().iconId)
        }
    }

    @Test
    fun `the live source wins over an icon the entry arrived with`() {
        runTest {
            val stale = LastUsedResource(SOURCE_ID, SOURCE_NAME, iconId = OTHER_ICON_ID)
            every { preferences.lastUsedResources } returns flowOf(listOf(stale))
            every { sources.observeSources() } returns flowOf(listOf(source(SOURCE_ID, ICON_ID)))

            assertEquals(ICON_ID, useCase().first().single().iconId)
        }
    }

    // A property, not a function: `useCase()` has to reach the use case's own invoke operator.
    private val useCase get() = ResolveLastUsedResourceUseCase(preferences, sources)

    private fun source(id: String, iconId: String? = null) = NetworkSource(
        id = id,
        type = NetworkSourceType.SMB,
        name = "any",
        server = "192.168.0.2",
        username = "user",
        password = "secret",
        iconId = iconId
    )

    private companion object {
        const val SOURCE_ID = "src-7"
        const val OTHER_ID = "src-9"
        const val SOURCE_NAME = "MyNAS"
        const val OTHER_NAME = "Studio"
        const val ICON_ID = "ico-02-007"
        const val OTHER_ICON_ID = "ico-04-011"
    }
}
