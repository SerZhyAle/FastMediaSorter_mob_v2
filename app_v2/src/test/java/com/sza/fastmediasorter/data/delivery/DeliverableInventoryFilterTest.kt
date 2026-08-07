package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableDownloadRunner
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetDownloader
import com.sza.fastmediasorter.domain.delivery.DeliverableSourceDescriptor
import com.sza.fastmediasorter.domain.delivery.ExtensionItem
import com.sza.fastmediasorter.domain.delivery.PayloadFile
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamCatalogUseCase
import com.sza.fastmediasorter.ui.player.helpers.TesseractModelManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the S0401 flavor filter in [DeliverableInventoryImpl.getExtensions]: a row is emitted only
 * when its set is offered in the running flavor. OCR engine + OCR language rows follow the combined
 * OCR availability (compiled-in AND runnable on this device), translation follows the translation
 * capability, and the data/DTS rows follow the contributed descriptor (or a bundled set). lite/photos
 * offer none of these; a device that cannot run OCR drops the OCR rows even on an OCR-compiled flavor.
 */
class DeliverableInventoryFilterTest {

    private val runner = mockk<DeliverableDownloadRunner>(relaxed = true)
    private val downloader = mockk<DeliverableSetDownloader>(relaxed = true)
    private val repository = mockk<DeliverableCapabilityRepository>(relaxed = true)
    private val tesseractModelManager = mockk<TesseractModelManager>(relaxed = true)
    private val capabilityAvailability = mockk<CapabilityAvailability>()
    private val bundled = mockk<BundledDeliverableSets>()
    private val appContext = mockk<Context>(relaxed = true)
    private val importStreamCatalogUseCase = mockk<ImportStreamCatalogUseCase>(relaxed = true)

    // `ocr` here means "OCR offered on this build AND runnable on this device" - the inventory now
    // gates OCR rows on the combined CapabilityAvailability.isOcrAvailable() axis (compile-time +
    // device-runtime), so a flavor that compiles OCR in but runs on a too-weak device passes ocr=false.
    private fun inventory(
        ocr: Boolean,
        translation: Boolean,
        descriptors: Map<DeliverableSet, DeliverableSourceDescriptor> = emptyMap(),
        bundledSets: Set<DeliverableSet> = emptySet(),
        streams: Boolean = false
    ): DeliverableInventoryImpl {
        every { capabilityAvailability.isOcrAvailable(any()) } returns ocr
        every { capabilityAvailability.isTranslationAvailable() } returns translation
        every { capabilityAvailability.isStreamsAvailable() } returns streams
        every { bundled.contains(any()) } answers { firstArg<DeliverableSet>() in bundledSets }
        return DeliverableInventoryImpl(
            runner = runner,
            downloader = downloader,
            repository = repository,
            tesseractModelManager = tesseractModelManager,
            capabilityAvailability = capabilityAvailability,
            importStreamCatalogUseCase = importStreamCatalogUseCase,
            bundled = bundled,
            descriptors = descriptors,
            artworkManifest = mockk(relaxed = true),
            appContext = appContext
        )
    }

    private fun descriptorFor(set: DeliverableSet): DeliverableSourceDescriptor =
        DeliverableSourceDescriptor(
            set = set,
            files = listOf(PayloadFile(fileName = "f", sources = listOf("u"), sha256 = "", minSize = 1L))
        )

    private fun List<ExtensionItem>.moduleSets(): Set<DeliverableSet> =
        filterIsInstance<ExtensionItem.Module>().map { it.set }.toSet()

    private fun List<ExtensionItem>.languageCodes(): Set<String> =
        filterIsInstance<ExtensionItem.LanguageData>().map { it.languageCode }.toSet()

    @Test
    fun `store-style flavor offering everything shows all six rows`() {
        val items = inventory(
            ocr = true,
            translation = true,
            descriptors = mapOf(
                DeliverableSet.AUDIO_VISUALIZATIONS to descriptorFor(DeliverableSet.AUDIO_VISUALIZATIONS),
                DeliverableSet.OCR_ENGINES to descriptorFor(DeliverableSet.OCR_ENGINES),
                DeliverableSet.FFMPEG_DTS to descriptorFor(DeliverableSet.FFMPEG_DTS)
            )
        ).getExtensions()

        assertEquals(
            setOf(
                DeliverableSet.OCR_ENGINES,
                DeliverableSet.TRANSLATION,
                DeliverableSet.AUDIO_VISUALIZATIONS,
                DeliverableSet.FFMPEG_DTS
            ),
            items.moduleSets()
        )
        assertEquals(setOf("rus", "ukr"), items.languageCodes())
    }

    @Test
    fun `lite or photos flavor offering nothing shows no rows`() {
        // No OCR/translation capability and no contributed descriptors: every row is dropped.
        val items = inventory(ocr = false, translation = false).getExtensions()

        assertTrue("expected empty inventory, got $items", items.isEmpty())
    }

    @Test
    fun `OCR language rows are dropped when OCR engine is not offered`() {
        val items = inventory(ocr = false, translation = true).getExtensions()

        assertFalse(items.moduleSets().contains(DeliverableSet.OCR_ENGINES))
        assertTrue("OCR language rows must not appear without OCR", items.languageCodes().isEmpty())
        // Translation still shows because its capability is on.
        assertTrue(items.moduleSets().contains(DeliverableSet.TRANSLATION))
    }

    @Test
    fun `OCR rows are dropped on a device that cannot run OCR even when translation is available`() {
        // OCR compiled into the flavor but the device fails the runtime check (low RAM / old API):
        // isOcrAvailable() is false, so the engine + language rows must not be offered.
        val items = inventory(ocr = false, translation = true).getExtensions()

        assertFalse(items.moduleSets().contains(DeliverableSet.OCR_ENGINES))
        assertTrue("OCR language rows must not appear on a non-OCR device", items.languageCodes().isEmpty())
        assertTrue(items.moduleSets().contains(DeliverableSet.TRANSLATION))
    }

    @Test
    fun `translation row is dropped when translation is not available`() {
        val items = inventory(ocr = true, translation = false).getExtensions()

        assertFalse(items.moduleSets().contains(DeliverableSet.TRANSLATION))
        // OCR engine + its languages still show.
        assertTrue(items.moduleSets().contains(DeliverableSet.OCR_ENGINES))
        assertEquals(setOf("rus", "ukr"), items.languageCodes())
    }

    @Test
    fun `media-playback rows follow the contributed descriptor`() {
        val items = inventory(
            ocr = false,
            translation = false,
            descriptors = mapOf(DeliverableSet.FFMPEG_DTS to descriptorFor(DeliverableSet.FFMPEG_DTS))
        ).getExtensions()

        // Only the set with a descriptor shows; audio-viz (no descriptor) stays hidden.
        assertEquals(setOf(DeliverableSet.FFMPEG_DTS), items.moduleSets())
    }

    @Test
    fun `channel preview atlas row shows only when streams are available`() {
        val withStreams = inventory(ocr = false, translation = false, streams = true).getExtensions()
        assertTrue(
            "atlas module must appear when streams are available",
            withStreams.moduleSets().contains(DeliverableSet.CHANNEL_PREVIEW_ATLAS)
        )

        val withoutStreams = inventory(ocr = false, translation = false, streams = false).getExtensions()
        assertFalse(
            "atlas module must be dropped when streams are unavailable",
            withoutStreams.moduleSets().contains(DeliverableSet.CHANNEL_PREVIEW_ATLAS)
        )
    }

    @Test
    fun `a bundled set shows even without a contributed descriptor`() {
        val items = inventory(
            ocr = false,
            translation = false,
            bundledSets = setOf(DeliverableSet.AUDIO_VISUALIZATIONS)
        ).getExtensions()

        assertEquals(setOf(DeliverableSet.AUDIO_VISUALIZATIONS), items.moduleSets())
    }
}
