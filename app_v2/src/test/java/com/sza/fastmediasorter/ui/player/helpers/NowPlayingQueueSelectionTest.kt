package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingQueueSelectionTest {

    private fun file(name: String, type: MediaType) =
        MediaFile(name = name, path = "/music/$name", type = type, size = 1L, createdDate = 0L)

    private val mixed = listOf(
        file("a.jpg", MediaType.IMAGE),
        file("b.mp3", MediaType.AUDIO),
        file("c.mp4", MediaType.VIDEO),
        file("d.flac", MediaType.AUDIO),
        file("e.gif", MediaType.GIF)
    )

    @Test
    fun `images and videos are dropped from the queue`() {
        val queue = NowPlayingManager.audioQueueFor(mixed, "/music/b.mp3")
        assertEquals(listOf("b.mp3", "d.flac"), queue.map { it.name })
    }

    @Test
    fun `queue keeps the original order`() {
        val queue = NowPlayingManager.audioQueueFor(mixed.reversed(), "/music/d.flac")
        assertEquals(listOf("d.flac", "b.mp3"), queue.map { it.name })
    }

    @Test
    fun `the current file is kept even when it is not typed as audio`() {
        val queue = NowPlayingManager.audioQueueFor(mixed, "/music/c.mp4")
        assertEquals(listOf("b.mp3", "c.mp4", "d.flac"), queue.map { it.name })
    }

    @Test
    fun `a null current path returns the audio files only`() {
        val queue = NowPlayingManager.audioQueueFor(mixed, null)
        assertEquals(listOf("b.mp3", "d.flac"), queue.map { it.name })
    }

    @Test
    fun `a folder without audio yields an empty queue`() {
        val images = listOf(file("a.jpg", MediaType.IMAGE), file("b.jpg", MediaType.IMAGE))
        assertEquals(emptyList<MediaFile>(), NowPlayingManager.audioQueueFor(images, null))
    }
}
