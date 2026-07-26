package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.core.capability.InstallSourceProvider
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the S0401 install-source gate in [RealDeliverableSetDownloader.download]: native code sets
 * (OCR_ENGINES, FFMPEG_DTS) take the Play-compliant branch on a Play install and the unchanged
 * source-failover branch otherwise, while data sets (AUDIO_VISUALIZATIONS) are never gated.
 *
 * The two branches are told apart by behaviour, not by network: the Play branch emits a "Google Play"
 * unavailable failure and never resolves a descriptor; the source-failover branch resolves the
 * descriptor first (stubbed to null here) and reports "no descriptor". No real HTTP/filesystem runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealDeliverableSetDownloaderGateTest {

    private val manifest = mockk<DeliveryManifestDataSource>()
    private val verifier = mockk<PayloadIntegrityVerifier>(relaxed = true)
    private val markerStore = mockk<InstalledSetMarkerStore>(relaxed = true)
    private val repository = mockk<DeliverableCapabilityRepository>(relaxed = true)
    private val installSourceProvider = mockk<InstallSourceProvider>()
    private val bundledSets = mockk<BundledDeliverableSets>()
    private val context = mockk<Context>(relaxed = true)

    private fun downloader(playInstall: Boolean, bundled: Boolean = false): RealDeliverableSetDownloader {
        every { installSourceProvider.isPlayInstall() } returns playInstall
        every { bundledSets.contains(any()) } returns bundled
        // Source-failover branch resolves the descriptor first; stub to null so it fails fast with a
        // distinct reason instead of touching staging/HTTP.
        coEvery { manifest.resolve(any()) } returns null
        return RealDeliverableSetDownloader(
            context = context,
            manifest = manifest,
            verifier = verifier,
            markerStore = markerStore,
            repository = repository,
            installSourceProvider = installSourceProvider,
            bundledSets = bundledSets,
            okHttpClient = OkHttpClient()
        )
    }

    @Test
    fun `S0971 bundled native set reports installed immediately without gate or network`() = runTest {
        val progress = downloader(playInstall = true, bundled = true)
            .download(DeliverableSet.OCR_ENGINES).toList()

        assertEquals(DownloadProgress.Queued, progress.first())
        assertEquals(DownloadProgress.Installed, progress.last())
        // A bundled set short-circuits before the Play gate and the descriptor source alike.
        verify(exactly = 0) { installSourceProvider.isPlayInstall() }
        coVerify(exactly = 0) { manifest.resolve(any()) }
    }

    @Test
    fun `OCR engines on Play install take Play-compliant branch without network`() = runTest {
        val progress = downloader(playInstall = true).download(DeliverableSet.OCR_ENGINES).toList()

        assertEquals(DownloadProgress.Queued, progress.first())
        val failed = progress.last() as DownloadProgress.Failed
        assertTrue(failed.reason.contains("Google Play"))
        // Play branch must not consult the GitHub-backed descriptor source.
        coVerify(exactly = 0) { manifest.resolve(any()) }
    }

    @Test
    fun `FFmpeg DTS on Play install take Play-compliant branch without network`() = runTest {
        val progress = downloader(playInstall = true).download(DeliverableSet.FFMPEG_DTS).toList()

        val failed = progress.last() as DownloadProgress.Failed
        assertTrue(failed.reason.contains("Google Play"))
        coVerify(exactly = 0) { manifest.resolve(any()) }
    }

    @Test
    fun `OCR engines on non-Play install take source-failover branch`() = runTest {
        val progress = downloader(playInstall = false).download(DeliverableSet.OCR_ENGINES).toList()

        // Non-Play keeps the existing path: it resolves the descriptor (here null -> no-descriptor).
        val failed = progress.last() as DownloadProgress.Failed
        assertEquals("no descriptor for ${DeliverableSet.OCR_ENGINES}", failed.reason)
        coVerify(exactly = 1) { manifest.resolve(DeliverableSet.OCR_ENGINES) }
    }

    @Test
    fun `FFmpeg DTS on non-Play install take source-failover branch`() = runTest {
        val progress = downloader(playInstall = false).download(DeliverableSet.FFMPEG_DTS).toList()

        val failed = progress.last() as DownloadProgress.Failed
        assertEquals("no descriptor for ${DeliverableSet.FFMPEG_DTS}", failed.reason)
        coVerify(exactly = 1) { manifest.resolve(DeliverableSet.FFMPEG_DTS) }
    }

    @Test
    fun `audio visualizations are never gated even on Play install`() = runTest {
        // Data payload (.mp4): the gate must not divert it, so even a Play install resolves the
        // descriptor on the unchanged path.
        val progress = downloader(playInstall = true).download(DeliverableSet.AUDIO_VISUALIZATIONS).toList()

        val failed = progress.last() as DownloadProgress.Failed
        assertEquals("no descriptor for ${DeliverableSet.AUDIO_VISUALIZATIONS}", failed.reason)
        coVerify(exactly = 1) { manifest.resolve(DeliverableSet.AUDIO_VISUALIZATIONS) }
        // The install source is irrelevant for a data set, so it is never queried (&& short-circuits
        // because AUDIO_VISUALIZATIONS is not a native code set).
        verify(exactly = 0) { installSourceProvider.isPlayInstall() }
    }

    @Test
    fun `channel preview atlas is never gated even on Play install`() = runTest {
        // S1154: the atlas is a data payload (sheet + JSON sidecar), not executable `.so`, so the Play
        // gate must not divert it - a Play install still resolves the descriptor on the unchanged path.
        val progress = downloader(playInstall = true)
            .download(DeliverableSet.CHANNEL_PREVIEW_ATLAS).toList()

        val failed = progress.last() as DownloadProgress.Failed
        assertEquals("no descriptor for ${DeliverableSet.CHANNEL_PREVIEW_ATLAS}", failed.reason)
        coVerify(exactly = 1) { manifest.resolve(DeliverableSet.CHANNEL_PREVIEW_ATLAS) }
        verify(exactly = 0) { installSourceProvider.isPlayInstall() }
    }
}
