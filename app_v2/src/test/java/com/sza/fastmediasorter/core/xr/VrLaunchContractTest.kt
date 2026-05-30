package com.sza.fastmediasorter.core.xr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VrLaunchContractTest {

    @Test
    fun fromRequest_preserves_video_snapshot() {
        val snapshot = PlayerStateSnapshot(
            videoPositionMs = 1234L,
            videoPlaybackSpeed = 1.25f,
            videoIsPlaying = true,
            videoVolume = 0.5f,
        )
        val request = StartVrPlaybackRequest(
            launchMode = VrLaunchMode.FILE_URI,
            fileUriString = "file:///sdcard/Movies/test.mp4",
            mediaType = VrMediaType.VIDEO,
            source = VrLaunchPoint.PLAYER_BADGE,
            snapshot = snapshot,
        )
        val input = VrLaunchInput.fromRequest(request)

        assertEquals(snapshot, input.snapshot)
        assertEquals(1234L, input.snapshot?.videoPositionMs)
        assertEquals(1.25f, input.snapshot?.videoPlaybackSpeed ?: 0f, 0.001f)
        assertTrue(input.snapshot?.videoIsPlaying == true)
        assertEquals(0.5f, input.snapshot?.videoVolume ?: 0f, 0.001f)
    }
}