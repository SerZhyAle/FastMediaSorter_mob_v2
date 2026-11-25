package com.sza.fastmediasorter.ui.common

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTypeBadgeCatalogTest {

    @Test
    fun `badgeEntries defines all 8 media types in canonical IVAGTPEO order`() {
        assertEquals(
            listOf("I", "V", "A", "G", "T", "P", "E", "O"),
            MediaTypeColorCatalog.badgeEntries.map { it.letter },
        )
        assertEquals(
            listOf(
                MediaType.IMAGE,
                MediaType.VIDEO,
                MediaType.AUDIO,
                MediaType.GIF,
                MediaType.TEXT,
                MediaType.PDF,
                MediaType.EPUB,
                MediaType.OFFICE_DOCUMENT,
            ),
            MediaTypeColorCatalog.badgeEntries.map { it.type },
        )
        assertFalse("D" in MediaTypeColorCatalog.badgeEntries.map { it.letter })
    }

    @Test
    fun `supportedBadgeEntries filters by media capabilities`() {
        val photosCaps = MediaCapabilities(
            supportsVideo = true,
            supportsAudio = false,
            supportsImages = true,
            supportsDocuments = false,
            supportsEpub = false,
            supportsCloud = false,
            supportsLocalNetworkSources = false,
            supportsDefaultPlayer = false,
            supportsCast = false,
            supportsMicRecording = false,
            supportsVrPlayer = false,
            supportsWearCompanion = false,
        )
        assertEquals(
            listOf("I", "V", "G"),
            MediaTypeColorCatalog.supportedBadgeEntries(photosCaps).map { it.letter },
        )
        val fullCaps = photosCaps.copy(supportsAudio = true, supportsDocuments = true, supportsEpub = true)
        assertTrue(MediaTypeColorCatalog.supportedBadgeEntries(fullCaps).size == 8)
    }
}
