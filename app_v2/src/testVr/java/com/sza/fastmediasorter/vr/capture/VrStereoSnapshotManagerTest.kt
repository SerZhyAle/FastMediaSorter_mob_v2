package com.sza.fastmediasorter.vr.capture

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class VrStereoSnapshotManagerTest {

    @Test
    fun `build generates sanitized stereo snapshot filename`() {
        val mediaFile = MediaFile(
            name = "My Clip (Final).mp4",
            path = "/storage/emulated/0/Movies/My Clip (Final).mp4",
            type = MediaType.VIDEO,
            size = 0L,
            createdDate = 0L,
        )

        val fileName = VrStereoSnapshotFileNameBuilder.build(
            mediaFile = mediaFile,
            capturedAt = LocalDateTime.of(2026, 4, 19, 20, 30, 45),
        )

        assertEquals("My_Clip__Final_20260419_203045_SBS.png", fileName)
    }

    @Test
    fun `build falls back when media file is missing`() {
        val fileName = VrStereoSnapshotFileNameBuilder.build(
            mediaFile = null,
            capturedAt = LocalDateTime.of(2026, 4, 19, 20, 30, 45),
        )

        assertEquals("vr_snapshot_20260419_203045_SBS.png", fileName)
    }
}