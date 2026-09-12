package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.domain.delivery.ArtworkManifestSource
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableDownloadRunner
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetDownloader
import com.sza.fastmediasorter.domain.delivery.DeliverableSourceDescriptor
import com.sza.fastmediasorter.domain.delivery.DeliveryAssetSizeSource
import com.sza.fastmediasorter.domain.delivery.ExtensionItem
import com.sza.fastmediasorter.domain.delivery.PayloadFile
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamCatalogUseCase
import com.sza.fastmediasorter.ui.player.helpers.TesseractModelManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2652: what the Extensions row promises before a download starts.
 *
 * Two defects are pinned here. A payload the build does not pin carries `minSize` only as a
 * truncation guard, and summing it put "1 MB" against a 14 MB tile pack; and a payload republished
 * outside the build has no compiled number that can stay true, so the live source has to win when it
 * answers and the compiled estimate has to stand when it does not.
 */
class DeliverableInventorySizeLabelTest {

    private val capabilityAvailability = mockk<CapabilityAvailability>()
    private val bundled = mockk<BundledDeliverableSets>()
    private val artworkManifest = mockk<ArtworkManifestSource>()
    private val assetSize = mockk<DeliveryAssetSizeSource>()

    private fun inventory(
        descriptors: Map<DeliverableSet, DeliverableSourceDescriptor> = emptyMap()
    ): DeliverableInventoryImpl {
        every { capabilityAvailability.isOcrAvailable(any()) } returns false
        every { capabilityAvailability.isTranslationAvailable(any()) } returns false
        every { capabilityAvailability.isStreamsAvailable() } returns true
        every { bundled.contains(any()) } returns false
        return DeliverableInventoryImpl(
            runner = mockk<DeliverableDownloadRunner>(relaxed = true),
            downloader = mockk<DeliverableSetDownloader>(relaxed = true),
            repository = mockk<DeliverableCapabilityRepository>(relaxed = true),
            tesseractModelManager = mockk<TesseractModelManager>(relaxed = true),
            capabilityAvailability = capabilityAvailability,
            importStreamCatalogUseCase = mockk<ImportStreamCatalogUseCase>(relaxed = true),
            bundled = bundled,
            descriptors = descriptors,
            artworkManifest = artworkManifest,
            assetSize = assetSize,
            appContext = mockk<Context>(relaxed = true)
        )
    }

    private fun unpinnedDescriptor(set: DeliverableSet): DeliverableSourceDescriptor =
        DeliverableSourceDescriptor(
            set = set,
            files = listOf(
                PayloadFile(fileName = "pack.zip", sources = listOf("u"), sha256 = "", minSize = 1_000_000L),
                PayloadFile(fileName = "coords.json", sources = listOf("u"), sha256 = "", minSize = 32_768L)
            )
        )

    @Test
    fun `unpinned descriptor does not put its truncation guard on screen`() {
        val set = DeliverableSet.STREAM_LOGO_ATLAS
        val label = inventory(mapOf(set to unpinnedDescriptor(set)))
            .getExtensions()
            .filterIsInstance<ExtensionItem.Module>()
            .first { it.set == set }
            .sizeLabel
        // The floor sums to 1.03 MB; the fallback the row must use instead is the measured 9.76 MiB.
        assertEquals("10 MB", label)
    }

    @Test
    fun `artwork row re-emits the published size`() = runTest {
        val set = DeliverableSet.STREAM_LOGO_ATLAS
        coEvery { artworkManifest.sizeOf(set) } returns 20_971_520L
        val labels = inventory(mapOf(set to unpinnedDescriptor(set)))
            .getExtensions()
            .filterIsInstance<ExtensionItem.Module>()
            .first { it.set == set }
            .sizeLabelFlow
            .toList()
        assertEquals(listOf("10 MB", "20 MB"), labels)
    }

    @Test
    fun `catalog row keeps its estimate when the asset cannot be measured`() = runTest {
        coEvery { assetSize.streamCatalogBytes() } returns null
        val labels = inventory()
            .getExtensions()
            .filterIsInstance<ExtensionItem.Catalog>()
            .single()
            .sizeLabelFlow
            .toList()
        assertEquals(listOf("7 MB"), labels)
    }

    @Test
    fun `catalog row reports the measured archive size`() = runTest {
        coEvery { assetSize.streamCatalogBytes() } returns 12_582_912L
        val labels = inventory()
            .getExtensions()
            .filterIsInstance<ExtensionItem.Catalog>()
            .single()
            .sizeLabelFlow
            .toList()
        assertEquals(listOf("7 MB", "12 MB"), labels)
    }
}
