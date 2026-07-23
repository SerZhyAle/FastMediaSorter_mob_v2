package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceDao
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1144: pure-mapping coverage for the per-channel track-preference read/write (no Room, no Robolectric).
 * Pins ADR-4 (null preference -> follow global default) and the read-modify-write invariant (writing one
 * field preserves the others).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamTrackPreferenceUseCaseTest {

    private val dao = mockk<StreamSourceDao>()
    private val useCase = StreamTrackPreferenceUseCase(dao)

    private val url = "https://example.com/stream.m3u8"

    private fun row(
        audio: String? = null,
        subtitle: String? = null,
        subtitlesEnabled: Boolean? = null
    ) = StreamSourceEntity(
        id = "id-1",
        url = url,
        title = "Channel",
        mediaKind = "VIDEO",
        sourceOrigin = "MANUAL",
        sortIndex = 0,
        addedAt = 0L,
        preferredAudioLang = audio,
        preferredSubtitleLang = subtitle,
        subtitlesEnabled = subtitlesEnabled
    )

    @Test
    fun `read returns null when row is absent`() = runTest {
        coEvery { dao.getByUrl(url) } returns null
        assertNull(useCase.read(url))
    }

    @Test
    fun `read returns null when all preference columns are null`() = runTest {
        coEvery { dao.getByUrl(url) } returns row()
        assertNull(useCase.read(url))
    }

    @Test
    fun `read returns populated preference when any field is set`() = runTest {
        coEvery { dao.getByUrl(url) } returns row(audio = "ru")
        val pref = useCase.read(url)
        assertEquals("ru", pref?.audioLang)
        assertNull(pref?.subtitleLang)
        assertNull(pref?.subtitlesEnabled)
    }

    @Test
    fun `writeAudio preserves the stored subtitle fields`() = runTest {
        coEvery { dao.getByUrl(url) } returns row(subtitle = "en", subtitlesEnabled = true)
        coEvery { dao.updateTrackPreferences(any(), any(), any(), any()) } just Runs

        useCase.writeAudio(url, "ru")

        coVerify { dao.updateTrackPreferences(url, "ru", "en", true) }
    }

    @Test
    fun `writeSubtitle preserves the stored audio field`() = runTest {
        coEvery { dao.getByUrl(url) } returns row(audio = "ru")
        coEvery { dao.updateTrackPreferences(any(), any(), any(), any()) } just Runs

        useCase.writeSubtitle(url, "uk", enabled = false)

        coVerify { dao.updateTrackPreferences(url, "ru", "uk", false) }
    }
}
