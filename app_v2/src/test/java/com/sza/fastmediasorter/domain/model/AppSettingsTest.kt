package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppSettings.getGloballyEnabledMediaTypes] - the only branching logic on the model.
 */
class AppSettingsTest {

    @Test
    fun `allFiles ON returns every known media type regardless of individual flags`() {
        val settings = AppSettings(
            allFiles = true,
            supportImages = false,
            supportVideos = false,
            supportAudio = false,
            supportGifs = false,
            supportText = false,
            supportPdf = false,
            supportEpub = false,
        )

        val result = settings.getGloballyEnabledMediaTypes()

        assertEquals(MediaType.entries.toSet(), result)
    }

    @Test
    fun `only enabled individual types are returned when allFiles is OFF`() {
        val settings = AppSettings(
            allFiles = false,
            supportImages = true,
            supportVideos = true,
            supportAudio = false,
            supportGifs = false,
            supportText = false,
            supportPdf = false,
            supportEpub = false,
        )

        val result = settings.getGloballyEnabledMediaTypes()

        assertEquals(setOf(MediaType.IMAGE, MediaType.VIDEO), result)
    }

    @Test
    fun `any document flag adds OFFICE_DOCUMENT alongside the specific document type`() {
        val settings = AppSettings(
            allFiles = false,
            supportImages = false,
            supportVideos = false,
            supportAudio = false,
            supportGifs = false,
            supportText = false,
            supportPdf = true,
            supportEpub = false,
        )

        val result = settings.getGloballyEnabledMediaTypes()

        assertEquals(setOf(MediaType.PDF, MediaType.OFFICE_DOCUMENT), result)
    }

    @Test
    fun `no document flags means OFFICE_DOCUMENT is absent`() {
        val settings = AppSettings(
            allFiles = false,
            supportImages = true,
            supportVideos = false,
            supportAudio = false,
            supportGifs = false,
            supportText = false,
            supportPdf = false,
            supportEpub = false,
        )

        val result = settings.getGloballyEnabledMediaTypes()

        assertFalse(MediaType.OFFICE_DOCUMENT in result)
    }

    @Test
    fun `all individual media flags enabled yields the full enabled set including documents`() {
        val settings = AppSettings(
            allFiles = false,
            supportImages = true,
            supportVideos = true,
            supportAudio = true,
            supportGifs = true,
            supportText = true,
            supportPdf = true,
            supportEpub = true,
        )

        val result = settings.getGloballyEnabledMediaTypes()

        assertTrue(
            result.containsAll(
                setOf(
                    MediaType.IMAGE,
                    MediaType.VIDEO,
                    MediaType.AUDIO,
                    MediaType.GIF,
                    MediaType.TEXT,
                    MediaType.PDF,
                    MediaType.EPUB,
                    MediaType.OFFICE_DOCUMENT,
                ),
            ),
        )
        // Binary types are never auto-enabled through individual flags.
        assertFalse(MediaType.BINARY_ARCHIVE in result)
    }
}
