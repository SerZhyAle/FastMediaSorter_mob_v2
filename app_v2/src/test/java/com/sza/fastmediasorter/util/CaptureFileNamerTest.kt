package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.util.CaptureFileNamer.CaptureKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

/**
 * CAPTURE-OUTPUT section 4, rung 1: every kind of rule 1 formatted at a fixed instant in a
 * non-Latin-digit locale, and the rule 5 ordinal.
 */
class CaptureFileNamerTest {

    private lateinit var previousLocale: Locale

    @Before
    fun useNonLatinDigitLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `allocates every kind with its contract prefix and ASCII digits`() {
        val namer = CaptureFileNamer()
        val expected = mapOf(
            CaptureKind.PHOTO to "photo_260821_123456.jpg",
            CaptureKind.VIDEO to "video_260821_123456.mp4",
            CaptureKind.SCREENSHOT to "screenshot_260821_123456.png",
            CaptureKind.SCREEN_VIDEO to "screen_video_260821_123456.mp4",
            CaptureKind.AUDIO to "audio_260821_123456.m4a",
            CaptureKind.VIDEO_FRAME to "video_frame_260821_123456.jpg",
            CaptureKind.OCR_TEXT to "ocr_text_260821_123456.txt",
            CaptureKind.TRANSLATION to "translation_260821_123456.txt",
            CaptureKind.BROADCAST to "broadcast_260821_123456.fmsbcast",
        )
        assertEquals(CaptureKind.entries.toSet(), expected.keys)

        expected.forEach { (kind, name) ->
            assertEquals(name, namer.allocate(kind, name.substringAfterLast('.'), timestamp))
        }
    }

    @Test
    fun `gives each broadcast descriptor export its own timestamped name`() {
        val namer = CaptureFileNamer()

        assertEquals(
            "broadcast_260821_123456.fmsbcast",
            namer.allocate(CaptureKind.BROADCAST, ".fmsbcast", timestamp),
        )
        assertEquals(
            "broadcast_260821_123457.fmsbcast",
            namer.allocate(CaptureKind.BROADCAST, ".fmsbcast", timestamp + MILLIS_PER_SECOND),
        )
    }

    @Test
    fun `adds ordered suffixes for allocations in the same second`() {
        val namer = CaptureFileNamer()

        assertEquals("photo_260821_123456.jpg", namer.allocate(CaptureKind.PHOTO, ".jpg", timestamp))
        assertEquals("photo_260821_123456 (2).jpg", namer.allocate(CaptureKind.PHOTO, ".jpg", timestamp))
        assertEquals("photo_260821_123456 (3).jpg", namer.allocate(CaptureKind.PHOTO, ".jpg", timestamp))
    }

    @Test
    fun `nextFreeName keeps a free name`() {
        assertEquals("photo_260821_123456.jpg", CaptureFileNamer.nextFreeName("photo_260821_123456.jpg") { false })
    }

    @Test
    fun `nextFreeName skips names already in the destination`() {
        val taken = setOf("photo_260821_123456.jpg", "photo_260821_123456 (2).jpg")

        assertEquals(
            "photo_260821_123456 (3).jpg",
            CaptureFileNamer.nextFreeName("photo_260821_123456.jpg") { it in taken },
        )
    }

    @Test
    fun `nextFreeName continues an in-process ordinal instead of stacking one`() {
        val taken = setOf("photo_260821_123456.jpg", "photo_260821_123456 (2).jpg")

        assertEquals(
            "photo_260821_123456 (3).jpg",
            CaptureFileNamer.nextFreeName("photo_260821_123456 (2).jpg") { it in taken },
        )
    }

    @Test
    fun `nextFreeName keeps a typed parenthesis that is not an ordinal`() {
        assertEquals(
            "Report (2019) (2).txt",
            CaptureFileNamer.nextFreeName("Report (2019).txt") { it == "Report (2019).txt" },
        )
    }

    @Test
    fun `nextFreeName handles a name without extension`() {
        assertEquals("notes (2)", CaptureFileNamer.nextFreeName("notes") { it == "notes" })
    }

    private companion object {
        const val YEAR = 2026
        const val DAY = 21
        const val HOUR = 12
        const val MINUTE = 34
        const val SECOND = 56
        const val MILLIS_PER_SECOND = 1000L

        val timestamp = GregorianCalendar(YEAR, Calendar.AUGUST, DAY, HOUR, MINUTE, SECOND).timeInMillis
    }
}
