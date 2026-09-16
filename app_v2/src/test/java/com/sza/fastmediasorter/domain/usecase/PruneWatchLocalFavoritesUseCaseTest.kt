package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.port.MediaAddressResolver
import com.sza.fastmediasorter.testing.fakes.FakeFavoritesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S3161: the prune deletes rows, so every case here is about what it must NOT delete.
 */
class PruneWatchLocalFavoritesUseCaseTest {

    private lateinit var repo: FakeFavoritesRepository
    private val resolvable = mutableSetOf<String>()
    private lateinit var useCase: PruneWatchLocalFavoritesUseCase

    @Before
    fun setup() {
        repo = FakeFavoritesRepository()
        resolvable.clear()
        useCase = PruneWatchLocalFavoritesUseCase(
            repo,
            object : MediaAddressResolver {
                override suspend fun exists(uri: String): Boolean = uri in resolvable
            }
        )
    }

    private fun watchWritten(uri: String, id: Long) = FavoritesEntity(
        id = id,
        uri = uri,
        resourceId = 0L,
        displayName = uri.substringAfterLast('/'),
        mediaType = 0,
        size = 0L,
        lastKnownPath = uri,
        dateModified = 0L
    )

    @Test
    fun `unresolvable watch-written row is removed`() = runTest {
        val uri = "content://media/external/images/media/185"
        repo.setFavorites(listOf(watchWritten(uri, id = 1L)))

        assertEquals(1, useCase())
        assertEquals(listOf(1L), repo.removedIds)
        assertTrue(repo.favorites.isEmpty())
    }

    @Test
    fun `row of that shape whose address still resolves is kept`() = runTest {
        val uri = "content://media/external/images/media/185"
        resolvable += uri
        repo.setFavorites(listOf(watchWritten(uri, id = 1L)))

        assertEquals(0, useCase())
        assertTrue(repo.removedIds.isEmpty())
    }

    @Test
    fun `favorite the phone made is kept even when its file is gone`() = runTest {
        val uri = "content://media/external/images/media/185"
        repo.setFavorites(
            listOf(
                watchWritten(uri, id = 1L).copy(
                    displayName = "holiday.png",
                    mediaType = 1,
                    size = 4096L
                )
            )
        )

        assertEquals(0, useCase())
        assertTrue(repo.removedIds.isEmpty())
    }

    @Test
    fun `plain path favorite is never a candidate`() = runTest {
        val uri = "/storage/emulated/0/Music/219"
        repo.setFavorites(listOf(watchWritten(uri, id = 1L)))

        assertEquals(0, useCase())
        assertTrue(repo.removedIds.isEmpty())
    }

    @Test
    fun `stream row is never a candidate`() = runTest {
        val uri = "content://media/external/audio/media/219"
        repo.setFavorites(
            listOf(watchWritten(uri, id = 1L).copy(kind = FavoritesEntity.KIND_STREAM))
        )

        assertEquals(0, useCase())
        assertTrue(repo.removedIds.isEmpty())
    }
}
