package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.util.CaptureFileNamer.CaptureKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class CaptureFileNamerTest {

    @Test
    fun `allocates every source with short date and full time`() {
        val namer = CaptureFileNamer()

        assertEquals("photo_260821_123456.jpg", namer.allocate(CaptureKind.PHOTO, ".jpg", timestamp))
        assertEquals("screenshot_260821_123456.png", namer.allocate(CaptureKind.SCREENSHOT, ".png", timestamp))
        assertEquals("audio_260821_123456.m4a", namer.allocate(CaptureKind.AUDIO, ".m4a", timestamp))
        assertEquals("video_260821_123456.mp4", namer.allocate(CaptureKind.VIDEO, ".mp4", timestamp))
        assertEquals(
            "screen_video_260821_123456.mp4",
            namer.allocate(CaptureKind.SCREEN_VIDEO, ".mp4", timestamp),
        )
        assertEquals(
            "video_frame_260821_123456.png",
            namer.allocate(CaptureKind.VIDEO_FRAME, ".png", timestamp),
        )
    }

    @Test
    fun `adds ordered suffixes for allocations in the same second`() {
        val namer = CaptureFileNamer()

        assertEquals("photo_260821_123456.jpg", namer.allocate(CaptureKind.PHOTO, ".jpg", timestamp))
        assertEquals("photo_260821_123456 (2).jpg", namer.allocate(CaptureKind.PHOTO, ".jpg", timestamp))
        assertEquals("photo_260821_123456 (3).jpg", namer.allocate(CaptureKind.PHOTO, ".jpg", timestamp))
    }

    private companion object {
        const val YEAR = 2026
        const val DAY = 21
        const val HOUR = 12
        const val MINUTE = 34
        const val SECOND = 56

        val timestamp = GregorianCalendar(YEAR, Calendar.AUGUST, DAY, HOUR, MINUTE, SECOND).timeInMillis
    }
}
