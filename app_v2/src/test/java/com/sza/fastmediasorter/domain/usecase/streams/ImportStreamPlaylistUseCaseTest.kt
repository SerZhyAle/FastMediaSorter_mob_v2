package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.M3uPlaylistParser
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.stats.StatsSink
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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

    private fun useCaseServing(
        body: ByteArray,
        contentType: String = "audio/x-mpegurl",
        headers: Map<String, String> = emptyMap(),
    ): ImportStreamPlaylistUseCase {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val response = Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(HTTP_OK)
                    .message("OK")
                    .body(body.toResponseBody(contentType.toMediaType()))
                headers.forEach { (name, value) -> response.header(name, value) }
                response.build()
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
    fun `the playlist bound is the reference reader's 32 MiB`() {
        assertEquals(PLAYLIST_BOUND_MIB * BYTES_PER_MIB, ImportStreamPlaylistUseCase.MAX_PLAYLIST_BYTES)
    }

    @Test
    fun `invalid scheme and host-less lines are dropped and two spellings import once`() = runTest {
        val written = slot<List<StreamSourceEntity>>()
        coEvery { repository.addAllIgnoringDuplicates(capture(written)) } answers { firstArg<List<*>>().size }
        val playlist = listOf(
            "#EXTM3U",
            "http://radio.example/one",
            "ftp://radio.example/archive",
            "http:///no-host",
            "not a url at all",
            "HTTP://Radio.Example/one",
            "http://radio.example/two",
        ).joinToString("\n").toByteArray()

        val result = useCaseServing(playlist)(PLAYLIST_URL)

        assertEquals(ImportStreamPlaylistUseCase.ImportResult.Success(2, duplicate = 0, skipped = 4), result)
        assertEquals(
            listOf("http://radio.example/one", "http://radio.example/two"),
            written.captured.map { it.url }
        )
    }

    @Test
    fun `the normalized spelling is stored and rows the catalog already held are counted as duplicates`() = runTest {
        val written = slot<List<StreamSourceEntity>>()
        coEvery { repository.addAllIgnoringDuplicates(capture(written)) } returns 1
        val playlist = """
            #EXTM3U
            #EXTINF:-1,First title
            HTTP://Radio.Example:80/live
            #EXTINF:-1,Second title
            http://radio.example/live
            ftp://radio.example/live
            https://radio.example/Live
        """.trimIndent().toByteArray()

        val result = useCaseServing(playlist)(PLAYLIST_URL)

        assertEquals(ImportStreamPlaylistUseCase.ImportResult.Success(1, duplicate = 1, skipped = 2), result)
        assertEquals(
            "scheme, host and default port are folded, the case of the path is kept",
            listOf("http://radio.example/live", "https://radio.example/Live"),
            written.captured.map { it.url }
        )
        assertEquals("First title", written.captured.first().title)
    }

    @Test
    fun `an HLS manifest is refused whole and writes nothing`() = runTest {
        val manifest = "#EXTM3U\n#EXT-X-TARGETDURATION:10\nhttps://radio.example/segment.ts\n".toByteArray()

        val result = useCaseServing(manifest)(PLAYLIST_URL)

        assertEquals(ImportStreamPlaylistUseCase.ImportResult.HlsManifest, result)
        coVerify(exactly = 0) { repository.addAllIgnoringDuplicates(any()) }
    }

    @Test
    fun `an entry URL ending m3u8 inside a playlist is an entry, not a manifest`() = runTest {
        coEvery { repository.addAllIgnoringDuplicates(any()) } answers { firstArg<List<*>>().size }
        val playlist = "#EXTM3U\n#EXTINF:-1,News #EXT-X-VERSION\nhttps://radio.example/news.m3u8\n".toByteArray()

        val result = useCaseServing(playlist)(PLAYLIST_URL)

        assertEquals(ImportStreamPlaylistUseCase.ImportResult.Success(1), result)
    }

    @Test
    fun `a list of only invalid lines is empty and writes nothing`() = runTest {
        val playlist = "#EXTM3U\nftp://radio.example/archive\nhttp:///no-host\n".toByteArray()

        val result = useCaseServing(playlist)(PLAYLIST_URL)

        assertEquals(ImportStreamPlaylistUseCase.ImportResult.Empty, result)
        coVerify(exactly = 0) { repository.addAllIgnoringDuplicates(any()) }
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

    @Test
    fun `an icy station response is refused before its body and writes nothing`() = runTest {
        val playlist = "#EXTM3U\nhttp://radio.example/one\n".toByteArray()

        val result = useCaseServing(playlist, headers = mapOf("icy-name" to "Radio One"))(PLAYLIST_URL)

        assertMediaRefusal(result, "a live stream")
    }

    @Test
    fun `an audio media type is refused before its body and writes nothing`() = runTest {
        val result = useCaseServing(ByteArray(MEDIA_BYTES), contentType = "audio/mpeg")(PLAYLIST_URL)

        assertMediaRefusal(result, "audio/mpeg")
    }

    @Test
    fun `playlist types of the media families and generic types are still read`() = runTest {
        coEvery { repository.addAllIgnoringDuplicates(any()) } answers { firstArg<List<*>>().size }
        val playlist = "#EXTM3U\n#EXTINF:-1,Radio One\nhttp://radio.example/one\n".toByteArray()

        listOf("application/vnd.apple.mpegurl", "audio/x-scpls", "text/plain", "application/octet-stream")
            .forEach { type ->
                val result = useCaseServing(playlist, type)(PLAYLIST_URL)
                assertEquals(type, ImportStreamPlaylistUseCase.ImportResult.Success(1), result)
            }
    }

    private fun assertMediaRefusal(result: ImportStreamPlaylistUseCase.ImportResult, named: String) {
        assertTrue(result is ImportStreamPlaylistUseCase.ImportResult.Failure)
        val reason = (result as ImportStreamPlaylistUseCase.ImportResult.Failure).reason
        assertTrue(reason, reason.contains("not a playlist") && reason.contains(named))
        coVerify(exactly = 0) { repository.addAllIgnoringDuplicates(any()) }
    }

    private companion object {
        const val MEDIA_BYTES = 4096
        const val PLAYLIST_BOUND_MIB = 32
        const val BYTES_PER_MIB = 1024 * 1024
        const val HTTP_OK = 200
        const val PLAYLIST_URL = "http://lists.example/list.m3u"
        const val JSON_PLAYLIST_URL = "http://lists.example/list.sza-playlist.JSON?v=1"
    }
}
