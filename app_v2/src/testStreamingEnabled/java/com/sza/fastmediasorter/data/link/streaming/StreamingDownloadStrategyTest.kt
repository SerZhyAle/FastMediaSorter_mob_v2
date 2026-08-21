package com.sza.fastmediasorter.data.link.streaming

import android.content.Context
import com.sza.fastmediasorter.domain.model.link.MediaQualityPreference
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import com.sza.fastmediasorter.domain.usecase.link.streaming.PipelineOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [StreamingDownloadStrategy.fetchAndRemux] outcome projection: DRM pre-flight,
 * insufficient-cache short-circuit, success/mux-failed mapping, error→NetworkError + cleanup, and
 * cancellation propagation + cleanup. context.cacheDir is backed by TemporaryFolder; all streaming
 * collaborators are mocked (no Media3, no network).
 */
class StreamingDownloadStrategyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private val drmDetector = mockk<ManifestDrmDetector>()
    private val segmentDownloader = mockk<Media3SegmentDownloader>()
    private val remuxer = mockk<MediaMuxerRemuxer>()
    private val cacheCleaner = mockk<StreamingCacheCleaner>(relaxed = true)

    private lateinit var strategy: StreamingDownloadStrategy
    private lateinit var cacheDir: File

    private val manifest = StreamingManifest.Hls("https://cdn/master.m3u8")
    private val quality = MediaQualityPreference(maxResolutionPx = 1080, audioOnly = false)

    @Before
    fun setUp() {
        cacheDir = tempFolder.root
        every { context.cacheDir } returns cacheDir
        every { cacheCleaner.newSessionId() } returns "sess0001"
        every { cacheCleaner.sessionDir(any(), any()) } returns File(cacheDir, "url-stream/sess0001").apply { mkdirs() }
        strategy = StreamingDownloadStrategy(context, drmDetector, segmentDownloader, remuxer, cacheCleaner)
    }

    private fun bundle() = SegmentBundle(
        manifestFile = File(cacheDir, "m.m3u8"),
        segmentFiles = listOf(File(cacheDir, "s0.ts")),
        videoMime = "video/avc",
        audioMime = "audio/mp4a-latm"
    )

    @Test
    fun `returns DrmBlocked when manifest is DRM protected`() = runBlocking {
        coEvery { drmDetector.isDrmProtected(any()) } returns true

        val outcome = strategy.fetchAndRemux(manifest, "out.mp4", quality, null) { _, _ -> }

        assertEquals(PipelineOutcome.DrmBlocked, outcome)
    }

    @Test
    fun `returns NetworkError when preflight space check fails`() = runBlocking {
        coEvery { drmDetector.isDrmProtected(any()) } returns false
        every { cacheCleaner.preflightCheck(any(), any()) } returns false

        val outcome = strategy.fetchAndRemux(manifest, "out.mp4", quality, null) { _, _ -> }

        assertTrue(outcome is PipelineOutcome.NetworkError)
    }

    @Test
    fun `returns Success when download and remux succeed`() = runBlocking {
        coEvery { drmDetector.isDrmProtected(any()) } returns false
        every { cacheCleaner.preflightCheck(any(), any()) } returns true
        coEvery { segmentDownloader.downloadVariant(any(), any(), any(), any(), any()) } returns bundle()
        val outFile = File(cacheDir, "result.mp4")
        every { remuxer.remux(any(), any()) } returns RemuxResult.Success(outFile)

        val outcome = strategy.fetchAndRemux(manifest, "out.mp4", quality, null) { _, _ -> }

        assertTrue(outcome is PipelineOutcome.Success)
        assertEquals("video/mp4", (outcome as PipelineOutcome.Success).mime)
        assertSame(outFile, outcome.file)
    }

    @Test
    fun `maps RemuxResult MuxFailed to PipelineOutcome MuxFailed`() = runBlocking {
        coEvery { drmDetector.isDrmProtected(any()) } returns false
        every { cacheCleaner.preflightCheck(any(), any()) } returns true
        coEvery { segmentDownloader.downloadVariant(any(), any(), any(), any(), any()) } returns bundle()
        every { remuxer.remux(any(), any()) } returns RemuxResult.MuxFailed("vp9")

        val outcome = strategy.fetchAndRemux(manifest, "out.mp4", quality, null) { _, _ -> }

        assertTrue(outcome is PipelineOutcome.MuxFailed)
        assertEquals("vp9", (outcome as PipelineOutcome.MuxFailed).codec)
    }

    @Test
    fun `download error becomes NetworkError and triggers cleanup`() = runBlocking {
        coEvery { drmDetector.isDrmProtected(any()) } returns false
        every { cacheCleaner.preflightCheck(any(), any()) } returns true
        coEvery { segmentDownloader.downloadVariant(any(), any(), any(), any(), any()) } throws
            StreamingDownloadException("segment 404")

        val outcome = strategy.fetchAndRemux(manifest, "out.mp4", quality, null) { _, _ -> }

        assertTrue(outcome is PipelineOutcome.NetworkError)
        coVerify { cacheCleaner.cleanupSession(cacheDir, "sess0001") }
    }

    @Test
    fun `cancellation propagates and triggers cleanup`() {
        coEvery { drmDetector.isDrmProtected(any()) } returns false
        every { cacheCleaner.preflightCheck(any(), any()) } returns true
        coEvery { segmentDownloader.downloadVariant(any(), any(), any(), any(), any()) } throws
            CancellationException("cancelled")

        val ex = runCatching {
            runBlocking { strategy.fetchAndRemux(manifest, "out.mp4", quality, null) { _, _ -> } }
        }.exceptionOrNull()

        assertTrue(ex is CancellationException)
        coVerify { cacheCleaner.cleanupSession(cacheDir, "sess0001") }
    }
}
