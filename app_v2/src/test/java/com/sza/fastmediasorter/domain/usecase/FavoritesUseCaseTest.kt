package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.testing.createMediaFile
import com.sza.fastmediasorter.testing.fakes.FakeFavoritesRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FavoritesUseCaseTest {

    private lateinit var repo: FakeFavoritesRepository
    private lateinit var useCase: FavoritesUseCase

    private fun fav(uri: String) = FavoritesEntity(
        uri = uri,
        resourceId = 1L,
        displayName = uri.substringAfterLast('/'),
        mediaType = 0,
        size = 0L,
        lastKnownPath = uri,
        dateModified = 0L,
    )

    @Before
    fun setup() {
        repo = FakeFavoritesRepository()
        useCase = FavoritesUseCase(repo, mockk(relaxed = true))
    }

    @Test
    fun `toggleFavorite adds entity when not currently favorite`() = runTest {
        val file = createMediaFile(name = "a.jpg", path = "/x/a.jpg")

        useCase.toggleFavorite(file, resourceId = 7L)

        assertEquals(1, repo.addedFavorites.size)
        val added = repo.addedFavorites.first()
        assertEquals("/x/a.jpg", added.uri)
        assertEquals(7L, added.resourceId)
        assertEquals("a.jpg", added.displayName)
        assertTrue(repo.isFavoriteSync("/x/a.jpg"))
    }

    @Test
    fun `toggleFavorite removes entity when already favorite`() = runTest {
        repo.setFavorites(listOf(fav("/x/a.jpg")))
        val file = createMediaFile(name = "a.jpg", path = "/x/a.jpg")

        useCase.toggleFavorite(file, resourceId = 7L)

        assertEquals(listOf("/x/a.jpg"), repo.removedUris)
        assertFalse(repo.isFavoriteSync("/x/a.jpg"))
    }

    @Test
    fun `getFavoritesForPaths reports membership per path`() = runTest {
        repo.setFavorites(listOf(fav("/x/a.jpg")))

        val map = useCase.getFavoritesForPaths(listOf("/x/a.jpg", "/x/b.jpg"))

        assertEquals(true, map["/x/a.jpg"])
        assertEquals(false, map["/x/b.jpg"])
    }

    @Test
    fun `isFavorite flow reflects current state`() = runTest {
        repo.setFavorites(listOf(fav("/x/a.jpg")))
        assertTrue(useCase.isFavorite("/x/a.jpg").first())
        assertFalse(useCase.isFavorite("/x/missing.jpg").first())
    }
}
