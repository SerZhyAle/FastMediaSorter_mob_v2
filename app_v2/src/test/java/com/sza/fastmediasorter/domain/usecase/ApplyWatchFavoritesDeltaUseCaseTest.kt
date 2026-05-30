package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.model.WearFavoriteDeltaItem
import com.sza.fastmediasorter.domain.model.WearFavoritesDeltaPayload
import com.sza.fastmediasorter.testing.fakes.FakeFavoritesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApplyWatchFavoritesDeltaUseCaseTest {

    private lateinit var repo: FakeFavoritesRepository
    private lateinit var useCase: ApplyWatchFavoritesDeltaUseCase

    @Before
    fun setup() {
        repo = FakeFavoritesRepository()
        useCase = ApplyWatchFavoritesDeltaUseCase(repo)
    }

    private fun item(path: String, isFav: Boolean) =
        WearFavoriteDeltaItem(sourceId = "s", filePath = path, isFavorite = isFav, changedAt = 123L)

    @Test
    fun `favorite item is added with derived display name`() = runTest {
        useCase(WearFavoritesDeltaPayload(listOf(item("/x/song.mp3", isFav = true))))

        assertEquals(1, repo.addedFavorites.size)
        val added = repo.addedFavorites.first()
        assertEquals("/x/song.mp3", added.uri)
        assertEquals("song.mp3", added.displayName)
        assertEquals(123L, added.dateModified)
    }

    @Test
    fun `unfavorite item is removed`() = runTest {
        repo.setFavorites(
            listOf(
                FavoritesEntity(
                    uri = "/x/song.mp3", resourceId = 0L, displayName = "song.mp3",
                    mediaType = 0, size = 0L, lastKnownPath = "/x/song.mp3", dateModified = 0L
                )
            )
        )

        useCase(WearFavoritesDeltaPayload(listOf(item("/x/song.mp3", isFav = false))))

        assertEquals(listOf("/x/song.mp3"), repo.removedUris)
    }

    @Test
    fun `mixed batch adds and removes per item flag`() = runTest {
        useCase(
            WearFavoritesDeltaPayload(
                listOf(item("/a.mp3", isFav = true), item("/b.mp3", isFav = false))
            )
        )

        assertTrue(repo.addedFavorites.any { it.uri == "/a.mp3" })
        assertEquals(listOf("/b.mp3"), repo.removedUris)
    }

    @Test
    fun `empty payload performs no operations`() = runTest {
        useCase(WearFavoritesDeltaPayload(emptyList()))
        assertTrue(repo.addedFavorites.isEmpty())
        assertFalse(repo.removedUris.any())
    }
}
