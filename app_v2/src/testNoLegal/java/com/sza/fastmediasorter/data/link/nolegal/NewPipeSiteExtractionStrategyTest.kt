package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.domain.usecase.link.BlockedReason
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException

/**
 * S0300 Phase 07: JVM unit tests for [NewPipeSiteExtractionStrategy] - service resolution
 * (allowed-id gate), link-type routing, and the extraction-failure → OpenResult mapping
 * matrix. NewPipe statics and the downloader are mocked; no real network or extractor init.
 */
class NewPipeSiteExtractionStrategyTest {

    private lateinit var downloader: NewPipeOkHttpDownloader
    private lateinit var direct: DirectFileExtractionStrategy
    private lateinit var strategy: NewPipeSiteExtractionStrategy
    private val noProgress: (Long, Long?) -> Unit = { _, _ -> }

    @Before
    fun setUp() {
        downloader = mockk()
        direct = mockk()
        justRun { downloader.ensureInitialized() }
        strategy = NewPipeSiteExtractionStrategy(downloader, direct)
        mockkStatic(NewPipe::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun youTubeService(linkType: StreamingService.LinkType?): StreamingService {
        val service = mockk<StreamingService>()
        every { service.serviceId } returns ServiceList.YouTube.serviceId
        if (linkType == null) {
            every { service.getLinkTypeByUrl(any()) } throws ParsingException("bad")
        } else {
            every { service.getLinkTypeByUrl(any()) } returns linkType
        }
        return service
    }

    @Test
    fun probe_streamLinkType_applicable() = runTest {
        every { NewPipe.getServiceByUrl(any()) } returns youTubeService(StreamingService.LinkType.STREAM)
        assertTrue(strategy.probe("https://youtube.com/watch?v=1") is ProbeResult.Applicable)
    }

    @Test
    fun probe_playlistLinkType_applicable() = runTest {
        every { NewPipe.getServiceByUrl(any()) } returns youTubeService(StreamingService.LinkType.PLAYLIST)
        assertTrue(strategy.probe("https://youtube.com/playlist?list=1") is ProbeResult.Applicable)
    }

    @Test
    fun probe_channelLinkType_notApplicable() = runTest {
        every { NewPipe.getServiceByUrl(any()) } returns youTubeService(StreamingService.LinkType.CHANNEL)
        assertEquals(ProbeResult.NotApplicable, strategy.probe("https://youtube.com/@chan"))
    }

    @Test
    fun probe_noService_notApplicable() = runTest {
        every { NewPipe.getServiceByUrl(any()) } throws ExtractionThrow()
        assertEquals(ProbeResult.NotApplicable, strategy.probe("https://unknown.example/x"))
    }

    @Test
    fun probe_serviceNotAllowed_notApplicable() = runTest {
        // A service whose id is outside ALLOWED_SERVICE_IDS resolves to null → NotApplicable.
        val foreign = mockk<StreamingService>()
        every { foreign.serviceId } returns 9999
        every { NewPipe.getServiceByUrl(any()) } returns foreign
        assertEquals(ProbeResult.NotApplicable, strategy.probe("https://x.example/y"))
    }

    @Test
    fun open_noService_notFound() = runTest {
        every { NewPipe.getServiceByUrl(any()) } throws ExtractionThrow()
        val result = strategy.open("https://unknown.example/x", noProgress)
        assertEquals("site_service_not_supported", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_linkTypeParseFailure_notFound() = runTest {
        every { NewPipe.getServiceByUrl(any()) } returns youTubeService(linkType = null)
        val result = strategy.open("https://youtube.com/watch?v=1", noProgress)
        assertEquals("site_link_type_failed", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_unsupportedLinkType_notFound() = runTest {
        every { NewPipe.getServiceByUrl(any()) } returns youTubeService(StreamingService.LinkType.CHANNEL)
        val result = strategy.open("https://youtube.com/@chan", noProgress)
        assertEquals("site_link_type_unsupported", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_streamPrivateContent_blockedAuthRequired() = runTest {
        val result = openStreamWith(mockk<PrivateContentException>(relaxed = true))
        assertEquals(BlockedReason.AuthRequired, (result as OpenResult.Blocked).reason)
    }

    @Test
    fun open_streamPaidContent_blockedAuthRequired() = runTest {
        val result = openStreamWith(mockk<PaidContentException>(relaxed = true))
        assertEquals(BlockedReason.AuthRequired, (result as OpenResult.Blocked).reason)
    }

    @Test
    fun open_streamAgeRestricted_blockedAuthRequired() = runTest {
        val result = openStreamWith(mockk<AgeRestrictedContentException>(relaxed = true))
        assertEquals(BlockedReason.AuthRequired, (result as OpenResult.Blocked).reason)
    }

    @Test
    fun open_streamReCaptcha_blockedAuthRequired() = runTest {
        val result = openStreamWith(mockk<ReCaptchaException>(relaxed = true))
        assertEquals(BlockedReason.AuthRequired, (result as OpenResult.Blocked).reason)
    }

    @Test
    fun open_streamContentNotAvailable_notFoundUnavailable() = runTest {
        val result = openStreamWith(mockk<ContentNotAvailableException>(relaxed = true))
        assertEquals("site_content_unavailable", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_streamGeographicRestriction_notFoundUnavailable() = runTest {
        val result = openStreamWith(mockk<GeographicRestrictionException>(relaxed = true))
        assertEquals("site_content_unavailable", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_streamParsingException_notFoundExtractionFailed() = runTest {
        val result = openStreamWith(ParsingException("bad"))
        assertEquals("site_extraction_failed", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_streamIoException_returnsError() = runTest {
        val io = IOException("net")
        val result = openStreamWith(io)
        assertEquals(io, (result as OpenResult.Error).cause)
    }

    @Test
    fun open_streamGenericThrowable_wrapsInIoError() = runTest {
        val result = openStreamWith(IllegalStateException("weird"))
        assertTrue((result as OpenResult.Error).cause is IOException)
    }

    /** Drives the STREAM branch and forces [StreamInfo.getInfo] to throw [error]. */
    private suspend fun openStreamWith(error: Throwable): OpenResult {
        every { NewPipe.getServiceByUrl(any()) } returns youTubeService(StreamingService.LinkType.STREAM)
        mockkStatic(StreamInfo::class)
        every { StreamInfo.getInfo(any<StreamingService>(), any<String>()) } throws error
        return strategy.open("https://youtube.com/watch?v=1", noProgress)
    }

    /** Stand-in throwable for "no service" - getServiceByUrl throws an opaque ExtractionException. */
    private class ExtractionThrow : org.schabi.newpipe.extractor.exceptions.ExtractionException("no service")
}
