package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2987: Tests for ToggleFavoriteUseCase verifying record-aware favorite toggling and legacy key parsing.
 */
class ToggleFavoriteUseCaseTest {

    private val repository: WearFavoritesRepository = mockk(relaxed = true)
    private val sendDelta: SendFavoritesDeltaUseCase = mockk(relaxed = true)
    private val refreshTile: RequestWearTileRefreshUseCase = mockk(relaxed = true)

    private val useCase = ToggleFavoriteUseCase(repository, sendDelta, refreshTile)

    @Test
    fun `toggle string-based adds legacy favorite when not favorite`() = runBlocking {
        val result = useCase.toggle("local", "content://media/123", wasFavorite = false)

        assertTrue(result)
        coVerify(exactly = 1) { repository.addFavorite("local", "content://media/123") }
        coVerify(exactly = 1) { sendDelta() }
        coVerify(exactly = 1) { refreshTile(WearTileKind.FAVOURITES) }
    }

    @Test
    fun `toggle string-based removes favorite when was favorite`() = runBlocking {
        val result = useCase.toggle("local", "content://media/123", wasFavorite = true)

        assertFalse(result)
        coVerify(exactly = 1) { repository.removeFavorite("local", "content://media/123") }
        coVerify(exactly = 1) { sendDelta() }
        coVerify(exactly = 1) { refreshTile(WearTileKind.FAVOURITES) }
    }

    @Test
    fun `toggle record-based adds record favorite when not favorite`() = runBlocking {
        val record = WearFavoriteRecord(
            sourceId = "local",
            filePath = "content://media/123",
            displayName = "audio_260904_183818",
            mimeType = "audio/mpeg"
        )
        val result = useCase.toggle(record, wasFavorite = false)

        assertTrue(result)
        coVerify(exactly = 1) { repository.addFavorite(record) }
        coVerify(exactly = 1) { sendDelta() }
        coVerify(exactly = 1) { refreshTile(WearTileKind.FAVOURITES) }
    }

    @Test
    fun `toggle record-based removes record favorite when was favorite`() = runBlocking {
        val record = WearFavoriteRecord(
            sourceId = "local",
            filePath = "content://media/123",
            displayName = "audio_260904_183818",
            mimeType = "audio/mpeg"
        )
        val result = useCase.toggle(record, wasFavorite = true)

        assertFalse(result)
        coVerify(exactly = 1) { repository.removeFavorite("local", "content://media/123") }
        coVerify(exactly = 1) { sendDelta() }
        coVerify(exactly = 1) { refreshTile(WearTileKind.FAVOURITES) }
    }

    @Test
    fun `fromLegacyKey strips extension per S2476`() {
        val record = WearFavoriteRecord.fromLegacyKey("local:/music/track1.mp3")

        assertEquals("local", record?.sourceId)
        assertEquals("/music/track1.mp3", record?.filePath)
        assertEquals("track1", record?.displayName)
    }
}
