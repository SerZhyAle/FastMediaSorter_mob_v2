package com.sza.fastmediasorter.wear.ui.player.common

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.wear.ui.player.helpers.StreamPlaybackSessionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class PlayerTransportTest {

    private val player = mockk<ExoPlayer>(relaxed = true)
    private val session = mockk<StreamPlaybackSessionManager>(relaxed = true)

    @Test
    fun `jumpToLive re-prepares the current item and plays`() {
        val item = mockk<MediaItem>()
        every { player.currentMediaItem } returns item
        every { session.canStartCurrentStream() } returns true

        session.jumpToLive(player)

        verifyOrder {
            player.stop()
            player.setMediaItem(item)
            player.prepare()
            player.play()
        }
    }

    @Test
    fun `jumpToLive without a current item leaves the player alone`() {
        every { player.currentMediaItem } returns null
        every { session.canStartCurrentStream() } returns true

        session.jumpToLive(player)

        verify(exactly = 0) { player.stop() }
        verify(exactly = 0) { player.prepare() }
    }

    @Test
    fun `jumpToLive refused by the stream session leaves the player alone`() {
        every { player.currentMediaItem } returns mockk<MediaItem>()
        every { session.canStartCurrentStream() } returns false

        session.jumpToLive(player)

        verify(exactly = 0) { player.stop() }
        verify(exactly = 0) { player.prepare() }
    }
}
