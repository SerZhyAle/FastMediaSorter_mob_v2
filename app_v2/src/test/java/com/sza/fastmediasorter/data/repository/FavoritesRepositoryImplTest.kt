package com.sza.fastmediasorter.data.repository

import android.content.Context
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesRepositoryImplTest {

    private lateinit var dao: FavoritesDao
    private lateinit var repo: FavoritesRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        // S2370: the repository took an application Context for the SAF existence probe the favorites
        // remap needs. Nothing exercised here reaches it, so a relaxed mock keeps these cases device-free.
        repo = FavoritesRepositoryImpl(mockk<Context>(relaxed = true), dao)
    }

    @Test
    fun `getFavoritesForPaths returns empty map for empty input without touching dao`() = runTest {
        val result = repo.getFavoritesForPaths(emptyList())
        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { dao.getFavoriteUrisForPaths(any()) }
    }

    @Test
    fun `getFavoritesForPaths marks each path by membership`() = runTest {
        coEvery { dao.getFavoriteUrisForPaths(any()) } returns listOf("/a", "/c")

        val result = repo.getFavoritesForPaths(listOf("/a", "/b", "/c"))

        assertTrue(result["/a"]!!)
        assertFalse(result["/b"]!!)
        assertTrue(result["/c"]!!)
    }

    @Test
    fun `getFavoritesForPaths chunks distinct paths under the SQLite IN limit`() = runTest {
        // 1000 distinct paths -> two chunks (900 + 100) given the 900-element clause limit.
        val paths = (0 until 1000).map { "/p$it" } + "/p0" // duplicate to exercise distinct()
        val chunkSizes = mutableListOf<Int>()
        coEvery { dao.getFavoriteUrisForPaths(any()) } answers {
            chunkSizes.add(firstArg<List<String>>().size)
            emptyList()
        }

        repo.getFavoritesForPaths(paths)

        assertEquals(listOf(900, 100), chunkSizes)
    }

    @Test
    fun `addFavorite delegates insert`() = runTest {
        val entity = FavoritesEntity(
            uri = "/x",
            resourceId = 1L,
            displayName = "x",
            mediaType = 1,
            size = 0L,
            lastKnownPath = "/x",
            dateModified = 0L,
            addedTimestamp = 1L,
        )
        val captured = slot<FavoritesEntity>()
        coEvery { dao.insert(capture(captured)) } returns Unit

        repo.addFavorite(entity)

        assertEquals("/x", captured.captured.uri)
    }

    @Test
    fun `removeFavorite and removeFavoriteById delegate to dao`() = runTest {
        repo.removeFavorite("/uri")
        repo.removeFavoriteById(5L)
        coVerify { dao.deleteByUri("/uri") }
        coVerify { dao.deleteById(5L) }
    }

    @Test
    fun `isFavoriteSync delegates to dao`() = runTest {
        coEvery { dao.isFavoriteSync("/u") } returns true
        assertTrue(repo.isFavoriteSync("/u"))
    }
}
