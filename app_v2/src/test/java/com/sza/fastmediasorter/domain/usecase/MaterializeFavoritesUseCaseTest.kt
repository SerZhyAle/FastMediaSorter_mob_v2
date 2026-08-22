package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1851: a STREAM favourite re-resolves its display name from the live catalog. Keyed by raw URL the
 * lookup missed whenever the catalog re-published the channel under a cosmetically different address,
 * and the favourites list silently showed the name frozen at the moment the star was tapped.
 */
class MaterializeFavoritesUseCaseTest {

    private val storedUrl = "http://Radio.Example.COM:80/stream/"
    private val republishedUrl = "https://radio.example.com/stream"

    private fun streamFavorite(uri: String, storedName: String) = FavoritesEntity(
        uri = uri,
        resourceId = 1L,
        displayName = storedName,
        mediaType = 0,
        size = 0L,
        lastKnownPath = uri,
        dateModified = 0L,
        kind = FavoritesEntity.KIND_STREAM,
        streamMediaKind = "AUDIO",
    )

    private fun catalogRow(url: String, title: String, identityKey: String = "") = StreamSourceEntity(
        id = "chan-1",
        url = url,
        title = title,
        mediaKind = "AUDIO",
        sourceOrigin = "IMPORTED",
        sortIndex = 0,
        addedAt = 0L,
        identityKey = identityKey,
    )

    private fun useCaseWith(rows: List<StreamSourceEntity>): MaterializeFavoritesUseCase {
        val observe = mockk<ObserveStreamSourcesUseCase>()
        every { observe() } returns flowOf(rows)
        return MaterializeFavoritesUseCase(observe)
    }

    @Test
    fun `a republished channel still resolves its current catalog title`() = runTest {
        val useCase = useCaseWith(listOf(catalogRow(republishedUrl, "Radio Example FM")))

        val files = useCase.toMediaFiles(listOf(streamFavorite(storedUrl, "stale name")))

        assertEquals("Radio Example FM", files.single().name)
    }

    @Test
    fun `the stored snapshot is used when the channel left the catalog`() = runTest {
        val useCase = useCaseWith(emptyList())

        val files = useCase.toMediaFiles(listOf(streamFavorite(storedUrl, "stale name")))

        assertEquals("stale name", files.single().name)
    }

    @Test
    fun `the catalog row is matched by its stored identity key when it has one`() = runTest {
        val rows = listOf(
            catalogRow("https://elsewhere.example/x", "Keyed Title", identityKey = "web://radio.example.com/stream"),
        )
        val useCase = useCaseWith(rows)

        val files = useCase.toMediaFiles(listOf(streamFavorite(storedUrl, "stale name")))

        assertEquals("Keyed Title", files.single().name)
    }

    @Test
    fun `a file favourite keeps its own display name`() = runTest {
        val useCase = useCaseWith(emptyList())
        val fileFav = FavoritesEntity(
            uri = "/x/a.jpg",
            resourceId = 7L,
            displayName = "a.jpg",
            mediaType = 0,
            size = 0L,
            lastKnownPath = "/x/a.jpg",
            dateModified = 0L,
        )

        val files = useCase.toMediaFiles(listOf(fileFav))

        assertEquals("a.jpg", files.single().name)
        assertEquals("/x/a.jpg", files.single().path)
    }
}
