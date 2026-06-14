package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.remote.ITunesApiService
import com.sza.fastmediasorter.data.remote.ITunesSearchResponse
import com.sza.fastmediasorter.data.remote.ITunesTrack
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.testing.createAppSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response as RetrofitResponse

/**
 * JVM coverage for [SearchAudioCoverUseCase]: the invoke orchestration (settings gate, m4a skip,
 * iTunes priority) and [SearchAudioCoverUseCase.searchItunes] result mapping. The Deezer/MusicBrainz
 * branches parse with org.json (Android), so they are driven only via non-successful HTTP responses
 * (returns null before any JSON parsing) to keep the suite pure-JVM.
 */
class SearchAudioCoverUseCaseTest {

    private val context = mockk<Context>(relaxed = true)
    private val iTunes = mockk<ITunesApiService>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val okHttpClient = mockk<OkHttpClient>()
    private lateinit var useCase: SearchAudioCoverUseCase

    @Before
    fun setup() {
        // No system service -> isWiFiConnected() returns false.
        every { context.getSystemService(any<String>()) } returns null
        useCase = SearchAudioCoverUseCase(context, iTunes, settingsRepository, okHttpClient)
    }

    private fun settings(online: Boolean = true, wifiOnly: Boolean = false) =
        flowOf(createAppSettings().copy(searchAudioCoversOnline = online, searchAudioCoversOnlyOnWifi = wifiOnly))

    private fun iTunesHit(artist: String = "Artist", track: String = "Song") = ITunesSearchResponse(
        resultCount = 1,
        results = listOf(
            ITunesTrack(
                trackId = 1, trackName = track, artistName = artist, collectionName = "Album",
                releaseDate = "2020-01-15T08:00:00Z", artworkUrl30 = null, artworkUrl60 = null,
                artworkUrl100 = "https://art/100x100bb.jpg", artworkUrl600 = null, artworkUrl1000 = null,
            ),
        ),
    )

    // okhttp Call returning a non-successful response (no body parsing path).
    private fun failingCall(): Call = mockk {
        every { execute() } returns Response.Builder()
            .request(Request.Builder().url("https://example.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("err")
            .body("".toResponseBody(null))
            .build()
    }

    @Test
    fun `returns null when online search disabled`() = runTest {
        every { settingsRepository.getSettings() } returns settings(online = false)

        assertNull(useCase("track.mp3"))
    }

    @Test
    fun `returns null for m4a microphone recordings`() = runTest {
        every { settingsRepository.getSettings() } returns settings(online = true)

        assertNull(useCase("recording.m4a"))
    }

    @Test
    fun `returns null on wifi-only when not on wifi`() = runTest {
        every { settingsRepository.getSettings() } returns settings(online = true, wifiOnly = true)

        assertNull(useCase("track.mp3"))
    }

    @Test
    fun `returns iTunes hit upgrading artwork to 600`() = runTest {
        every { settingsRepository.getSettings() } returns settings(online = true)
        coEvery { iTunes.searchTracks(any(), any(), any(), any()) } returns RetrofitResponse.success(iTunesHit())

        val result = useCase("Artist - Song.mp3")

        assertEquals("Artist", result?.artistName)
        assertEquals("Song", result?.trackName)
        assertEquals("https://art/600x600bb.jpg", result?.coverArtUrl)
        assertEquals("2020", result?.releaseYear)
    }

    @Test
    fun `uses ID3 metadata before filename`() = runTest {
        every { settingsRepository.getSettings() } returns settings(online = true)
        // The hit must match the ID3 query, otherwise the S0347 confidence gate rejects it.
        // The match itself proves the ID3 metadata (not the filename) drove the search.
        coEvery { iTunes.searchTracks(any(), any(), any(), any()) } returns
            RetrofitResponse.success(iTunesHit(artist = "The Beatles", track = "Help"))

        val result = useCase(
            filename = "weird_name.mp3",
            metadataArtist = "The Beatles",
            metadataTitle = "Help",
        )

        assertEquals("The Beatles", result?.artistName)
    }

    @Test
    fun `returns null when all sources fail`() = runTest {
        every { settingsRepository.getSettings() } returns settings(online = true)
        // iTunes: empty result -> falls through to Deezer/MusicBrainz.
        coEvery { iTunes.searchTracks(any(), any(), any(), any()) } returns
            RetrofitResponse.success(ITunesSearchResponse(resultCount = 0, results = emptyList()))
        // Deezer + MusicBrainz HTTP fail before any JSON parsing.
        every { okHttpClient.newCall(any()) } returns failingCall()

        assertNull(useCase("Some Track.mp3"))
    }

    @Test
    fun `iTunes http error falls through to null`() = runTest {
        every { settingsRepository.getSettings() } returns settings(online = true)
        coEvery { iTunes.searchTracks(any(), any(), any(), any()) } returns
            RetrofitResponse.error(404, "".toResponseBody(null))
        every { okHttpClient.newCall(any()) } returns failingCall()

        assertNull(useCase("Track.mp3"))
    }
}
