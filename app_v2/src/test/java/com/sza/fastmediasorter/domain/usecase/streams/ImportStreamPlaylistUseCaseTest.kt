package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.M3uPlaylistParser
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.stats.StatsSink
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportStreamPlaylistUseCaseTest {

    private val repository: StreamSourceRepository = mockk(relaxed = true)

    private fun useCaseServing(body: ByteArray): ImportStreamPlaylistUseCase {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(HTTP_OK)
                    .message("OK")
                    .body(body.toResponseBody("audio/x-mpegurl".toMediaType()))
                    .build()
            }
            .build()
        return ImportStreamPlaylistUseCase(
            okHttpClient = client,
            parser = M3uPlaylistParser(),
            classifier = StreamMediaKindClassifier(),
            repository = repository,
            statsSink = mockk<StatsSink>(relaxed = true)
        )
    }

    @Test
    fun `a body over the cap fails with a named reason and writes nothing`() = runTest {
        val oversized = ByteArray(ImportStreamPlaylistUseCase.MAX_PLAYLIST_BYTES + 1) { 'a'.code.toByte() }

        val result = useCaseServing(oversized)(PLAYLIST_URL)

        assertTrue(result is ImportStreamPlaylistUseCase.ImportResult.Failure)
        val reason = (result as ImportStreamPlaylistUseCase.ImportResult.Failure).reason
        assertTrue(reason, reason.contains("MiB cap"))
        coVerify(exactly = 0) { repository.addAllIgnoringDuplicates(any()) }
    }

    @Test
    fun `a body under the cap is parsed and imported`() = runTest {
        coEvery { repository.addAllIgnoringDuplicates(any()) } answers { firstArg<List<*>>().size }
        val playlist = "#EXTM3U\n#EXTINF:-1,Radio One\nhttp://radio.example/one\n".toByteArray()

        val result = useCaseServing(playlist)(PLAYLIST_URL)

        assertEquals(ImportStreamPlaylistUseCase.ImportResult.Success(1), result)
    }

    @Test
    fun `a JSON body is refused whole and writes nothing`() = runTest {
        val json = "﻿\n  {\n  \"schemaVersion\": 1,\n  \"channels\": [\n    \"http://radio.example/one\"\n  ]\n}\n"

        val result = useCaseServing(json.toByteArray())(PLAYLIST_URL)

        assertEquals(ImportStreamPlaylistUseCase.ImportResult.UnsupportedFormat, result)
        coVerify(exactly = 0) { repository.addAllIgnoringDuplicates(any()) }
    }

    @Test
    fun `a sza-playlist json URL is refused even when the body looks like M3U`() = runTest {
        val playlist = "#EXTM3U\nhttp://radio.example/one\n".toByteArray()

        val result = useCaseServing(playlist)(JSON_PLAYLIST_URL)

        assertEquals(ImportStreamPlaylistUseCase.ImportResult.UnsupportedFormat, result)
        coVerify(exactly = 0) { repository.addAllIgnoringDuplicates(any()) }
    }

    private companion object {
        const val HTTP_OK = 200
        const val PLAYLIST_URL = "http://lists.example/list.m3u"
        const val JSON_PLAYLIST_URL = "http://lists.example/list.sza-playlist.JSON?v=1"
    }
}
