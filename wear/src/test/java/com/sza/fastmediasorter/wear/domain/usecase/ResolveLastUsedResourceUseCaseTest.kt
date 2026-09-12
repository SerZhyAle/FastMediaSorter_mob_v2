package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.LastUsedKind
import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
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

    // S2499: empty by default, so every case written before the channel store existed still describes
    // a watch with no channels rather than a watch whose channel store was never asked.
    private val channels: WearStreamChannelRepository = mockk {
        every { observeChannels() } returns flowOf(emptyList())
    }

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

    @Test
    fun `a remembered channel that is still catalogued resolves to itself`() {
        runTest {
            every { preferences.lastUsedResources } returns flowOf(listOf(rememberedChannel()))
            every { sources.observeSources() } returns flowOf(emptyList())
            every { channels.observeChannels() } returns flowOf(listOf(channel(CHANNEL_URL, CHANNEL_NAME)))

            assertEquals(listOf(rememberedChannel()), useCase().first())
        }
    }

    @Test
    fun `a channel takes the caption the catalog carries now`() {
        runTest {
            every { preferences.lastUsedResources } returns flowOf(listOf(rememberedChannel(name = "old name")))
            every { sources.observeSources() } returns flowOf(emptyList())
            every { channels.observeChannels() } returns flowOf(listOf(channel(CHANNEL_URL, CHANNEL_NAME)))

            assertEquals(CHANNEL_NAME, useCase().first().single().name)
        }
    }

    @Test
    fun `a resolved channel carries the favicon index of the channel it matched`() {
        runTest {
            every { preferences.lastUsedResources } returns flowOf(listOf(rememberedChannel()))
            every { sources.observeSources() } returns flowOf(emptyList())
            every { channels.observeChannels() } returns flowOf(listOf(channel(CHANNEL_URL, CHANNEL_NAME, faviconIndex = 42)))

            assertEquals(42, useCase().first().single().faviconIndex)
        }
    }

    @Test
    fun `a channel the catalog no longer lists is dropped`() {
        runTest {
            every { preferences.lastUsedResources } returns flowOf(listOf(rememberedChannel()))
            every { sources.observeSources() } returns flowOf(listOf(source(SOURCE_ID)))
            every { channels.observeChannels() } returns flowOf(listOf(channel(OTHER_URL, OTHER_CHANNEL_NAME)))

            assertTrue(useCase().first().isEmpty())
        }
    }

    @Test
    fun `a channel and a resource resolve together in the stored order`() {
        runTest {
            val remembered = listOf(rememberedChannel(), LastUsedResource(SOURCE_ID, SOURCE_NAME))
            every { preferences.lastUsedResources } returns flowOf(remembered)
            every { sources.observeSources() } returns flowOf(listOf(source(SOURCE_ID)))
            every { channels.observeChannels() } returns flowOf(listOf(channel(CHANNEL_URL, CHANNEL_NAME)))

            assertEquals(remembered, useCase().first())
        }
    }

    // A property, not a function: `useCase()` has to reach the use case's own invoke operator.
    private val useCase get() = ResolveLastUsedResourceUseCase(preferences, sources, channels)

    private fun rememberedChannel(name: String = CHANNEL_NAME) =
        LastUsedResource(CHANNEL_URL, name, LastUsedKind.STREAM)

    private fun channel(url: String, name: String, faviconIndex: Int? = null) = WearStreamChannel(
        id = "ch-1",
        name = name,
        url = url,
        mediaKind = "AUDIO",
        faviconIndex = faviconIndex
    )

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

        // Already in the spelling `normalizeWearStreamUrl` produces, which is what the store holds.
        const val CHANNEL_URL = "https://radio.example/stream"
        const val OTHER_URL = "https://other.example/stream"
        const val CHANNEL_NAME = "Jazz FM"
        const val OTHER_CHANNEL_NAME = "Talk FM"
    }
}
