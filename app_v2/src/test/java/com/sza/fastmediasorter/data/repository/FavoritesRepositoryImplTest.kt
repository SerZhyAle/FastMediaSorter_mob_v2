package com.sza.fastmediasorter.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.model.FavoritesRemapOutcome
import com.sza.fastmediasorter.utils.SafHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
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

    @Test
    fun `remap lists each tree directory once however many favorites it holds`() = runTest {
        val x = document("x.jpg", "content://tree/x")
        val y = document("y.jpg", "content://tree/y")
        val album = document("album", "content://tree/album", children = arrayOf(x, y))
        val root = document("root", "content://tree/root", children = arrayOf(album))
        mockkObject(SafHelper)
        try {
            every { SafHelper.getTreeRoot(any(), "tree") } returns root
            coEvery { dao.getFavoritesForResource(1L) } returns listOf(
                favorite("/old/album/x.jpg"),
                favorite("/old/album/y.jpg"),
                favorite("/old/album/gone.jpg"),
            )

            val outcome = repo.remapResourceFavoritesToTree(1L, "/old/", "tree")

            assertEquals(FavoritesRemapOutcome(total = 3, remapped = 2, keptMissing = 1, untouched = 0), outcome)
            verify(exactly = 1) { root.listFiles() }
            verify(exactly = 1) { album.listFiles() }
            coVerify {
                dao.updateFavorites(
                    match { rows -> rows.map { it.uri } == listOf("content://tree/x", "content://tree/y") }
                )
            }
        } finally {
            unmockkObject(SafHelper)
        }
    }

    private fun document(
        name: String,
        uri: String,
        children: Array<DocumentFile> = emptyArray(),
    ): DocumentFile {
        val docUri = mockk<Uri> { every { this@mockk.toString() } returns uri }
        return mockk {
            every { this@mockk.name } returns name
            every { this@mockk.uri } returns docUri
            every { listFiles() } returns children
            every { exists() } returns true
        }
    }

    private fun favorite(path: String) = FavoritesEntity(
        uri = path,
        resourceId = 1L,
        displayName = path.substringAfterLast('/'),
        mediaType = 1,
        size = 0L,
        lastKnownPath = path,
        dateModified = 0L,
        addedTimestamp = 1L,
    )
}
