package com.sza.fastmediasorter.data.repository

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AudioMetadataCacheRepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: AudioMetadataCacheRepository

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        repo = AudioMetadataCacheRepository(context)
        repo.clearCache() // clean state
    }

    @Test
    fun `readMetadata returns null for missing file`() {
        assertNull(repo.readMetadata("nonexistent.mp3"))
    }

    @Test
    fun `saveMetadata and readMetadata round-trip`() {
        val data = AudioMetadataSaveData(
            trackName = "Test Track",
            artistName = "Test Artist",
            albumName = "Test Album",
            releaseYear = "2024",
            coverArtUrl = "https://example.com/cover.jpg",
            coverExtension = null
        )
        repo.saveMetadata("song.mp3", data)
        val cached = repo.readMetadata("song.mp3")
        assertNotNull(cached)
        assertEquals("Test Track", cached!!.trackName)
        assertEquals("Test Artist", cached.artistName)
        assertEquals("https://example.com/cover.jpg", cached.coverArtUrl)
        assertNull(cached.coverFile) // no extension set
    }

    @Test
    fun `saveCover creates file with correct extension`() {
        repo.saveMetadata("song.mp3", AudioMetadataSaveData(null, null, null, null, null, "jpg"))
        repo.saveCover("song.mp3", byteArrayOf(1, 2, 3), "jpg")
        val cached = repo.readMetadata("song.mp3")
        assertNotNull(cached?.coverFile)
        assertTrue(cached!!.coverFile!!.name.endsWith(".jpg"))
    }

    @Test
    fun `clearCache removes all files`() {
        repo.saveMetadata("a.mp3", AudioMetadataSaveData(null, null, null, null, null, null))
        repo.saveMetadata("b.mp3", AudioMetadataSaveData(null, null, null, null, null, null))
        repo.clearCache()
        assertNull(repo.readMetadata("a.mp3"))
        assertNull(repo.readMetadata("b.mp3"))
    }

    @Test
    fun `getCacheSize returns correct sum`() {
        assertEquals(0L, repo.getCacheSize())
        repo.saveCover("track.mp3", ByteArray(1024), "jpg")
        assertTrue(repo.getCacheSize() > 0L)
    }

    @Test
    fun `trimIfNeeded returns false when under limit`() {
        repo.saveCover("track.mp3", ByteArray(100), "jpg")
        assertFalse(repo.trimIfNeeded())
    }
}
