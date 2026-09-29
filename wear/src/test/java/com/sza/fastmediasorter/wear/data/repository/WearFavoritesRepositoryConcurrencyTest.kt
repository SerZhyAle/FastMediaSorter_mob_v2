package com.sza.fastmediasorter.wear.data.repository

import com.google.gson.Gson
import com.sza.fastmediasorter.wear.domain.usecase.RequestWearComplicationRefreshUseCase
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** S3797: overlapping favourite edits must all land, in the stores and in the pending delta. */
class WearFavoritesRepositoryConcurrencyTest {

    private val edits = 20

    private fun repository(prefs: InMemorySharedPreferences) = WearFavoritesRepositoryImpl(
        openPrefs = { prefs },
        gson = Gson(),
        requestWearComplicationRefreshUseCase = mockk<RequestWearComplicationRefreshUseCase>(relaxed = true)
    )

    @Test
    fun `overlapping adds keep every favourite and every delta`() = runBlocking {
        val repo = repository(InMemorySharedPreferences())

        (1..edits).map { n ->
            async(Dispatchers.Default) { repo.addFavorite("local", "/music/track-$n.mp3") }
        }.awaitAll()

        assertEquals(edits, repo.getFavorites().size)
        assertEquals(edits, repo.getPendingDelta().size)
    }

    @Test
    fun `overlapping removes leave nothing behind`() = runBlocking {
        val repo = repository(InMemorySharedPreferences())
        (1..edits).forEach { n -> repo.addFavorite("local", "/music/track-$n.mp3") }

        (1..edits).map { n ->
            async(Dispatchers.Default) { repo.removeFavorite("local", "/music/track-$n.mp3") }
        }.awaitAll()

        assertEquals(0, repo.getFavorites().size)
    }
}
