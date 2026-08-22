package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.testing.fakes.FakeFavoritesRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S1842: a favourited channel must keep its star when the catalog re-publishes it under a
 * cosmetically different address, and a second tap must remove the existing row rather than add a
 * duplicate. Every test here fails on the pre-S1842 code, which compared stored URLs byte for byte.
 */
class FavoritesUseCaseStreamIdentityTest {

    private lateinit var repo: FakeFavoritesRepository
    private lateinit var useCase: FavoritesUseCase

    /** The address as the user first favourited it. */
    private val storedUrl = "http://Radio.Example.COM:80/stream/"

    /** The same channel after the catalog re-published it: https, no port, no trailing slash. */
    private val republishedUrl = "https://radio.example.com/stream"

    private fun streamFavorite(uri: String) = FavoritesEntity(
        uri = uri,
        resourceId = 1L,
        displayName = "Radio Example",
        mediaType = 0,
        size = 0L,
        lastKnownPath = uri,
        dateModified = 0L,
        kind = FavoritesEntity.KIND_STREAM,
        streamMediaKind = "AUDIO",
    )

    private fun channel(url: String, identityKey: String = "") = StreamSourceEntity(
        id = "chan-1",
        url = url,
        title = "Radio Example",
        mediaKind = "AUDIO",
        sourceOrigin = "IMPORTED",
        sortIndex = 0,
        addedAt = 0L,
        identityKey = identityKey,
    )

    @Before
    fun setup() {
        repo = FakeFavoritesRepository()
        useCase = FavoritesUseCase(repo, mockk(relaxed = true))
    }

    @Test
    fun `republished channel still matches the stored favourite`() = runTest {
        repo.setFavorites(listOf(streamFavorite(storedUrl)))

        val identities = useCase.observeFavoriteStreamIdentities().first()

        assertTrue(
            "the star must stay lit after a cosmetic address change",
            identities.contains(useCase.channelIdentity(channel(republishedUrl))),
        )
    }

    @Test
    fun `toggling a republished channel removes the stored row instead of adding a second`() = runTest {
        repo.setFavorites(listOf(streamFavorite(storedUrl)))

        useCase.toggleStreamFavorite(channel(republishedUrl))

        assertEquals(
            "removal must target the row as it is stored, not the new address",
            listOf(storedUrl),
            repo.removedUris,
        )
        assertEquals("no duplicate row may be added", 0, repo.addedFavorites.size)
    }

    @Test
    fun `favouriting an unknown channel stores the launchable url, never the identity`() = runTest {
        useCase.toggleStreamFavorite(channel(republishedUrl))

        assertEquals(1, repo.addedFavorites.size)
        val added = repo.addedFavorites.first()
        assertEquals(
            "the stored address is what the favourites screen opens, so it must stay a real url",
            republishedUrl,
            added.uri,
        )
        assertEquals(republishedUrl, added.lastKnownPath)
    }

    @Test
    fun `channelIdentity prefers the stored key and derives one only when it is empty`() {
        val backfilled = channel(republishedUrl, identityKey = "web://from.the.write.path/stream")

        assertEquals("web://from.the.write.path/stream", useCase.channelIdentity(backfilled))
        assertEquals(
            useCase.channelIdentity(channel(storedUrl)),
            useCase.channelIdentity(channel(republishedUrl)),
        )
    }

    @Test
    fun `two genuinely different channels do not collapse onto one identity`() {
        val a = channel("https://radio.example.com/stream")
        val b = channel("https://radio.example.com/other")

        assertTrue(useCase.channelIdentity(a) != useCase.channelIdentity(b))
    }
}
