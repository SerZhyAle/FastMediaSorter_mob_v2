package com.sza.fastmediasorter.widget

import android.content.Context
import com.sza.fastmediasorter.data.SyncStorageCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S3239: the write left the caller's thread, so what a reader sees no longer depends on the disk
 * having caught up. The stored key format is pinned alongside it - it is already written on every
 * device that has the now-playing widget placed, and a drifted key would render the widget empty
 * without failing anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioNowPlayingSnapshotStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun dropCache() = AudioNowPlayingSnapshotStore.dropCacheForTest()

    @After
    fun clearStore() {
        prefs().edit().clear().apply()
        AudioNowPlayingSnapshotStore.dropCacheForTest()
    }

    private fun prefs() = SyncStorageCompat.getSyncPreferences(context, "audio_now_playing_widget")

    private fun snapshot() = AudioNowPlayingSnapshotStore.Snapshot(
        active = true,
        title = "Track",
        artist = "Artist",
        artworkUri = "content://art/1",
        isPlaying = true,
        mediaUri = "/sdcard/Music/a.mp3",
        resourceId = 42L,
        size = 1024L,
        dateModified = 99L,
        isFavorite = true,
    )

    @Test
    fun `a written snapshot is readable before the disk write lands`() {
        AudioNowPlayingSnapshotStore.write(context, snapshot())

        assertEquals(snapshot(), AudioNowPlayingSnapshotStore.read(context))
    }

    @Test
    fun `persisting writes the exact key format the widget was shipped with`() {
        AudioNowPlayingSnapshotStore.persist(context, snapshot())

        // The literal strings are the point of this test - do not replace them with the constants,
        // or a rename of both at once would pass while every placed widget rendered as idle.
        assertTrue(prefs().getBoolean("active", false))
        assertEquals("Track", prefs().getString("title", ""))
        assertEquals("Artist", prefs().getString("artist", ""))
        assertEquals("content://art/1", prefs().getString("artwork_uri", ""))
        assertTrue(prefs().getBoolean("is_playing", false))
        assertEquals("/sdcard/Music/a.mp3", prefs().getString("media_uri", ""))
        assertEquals(42L, prefs().getLong("resource_id", -1L))
        assertEquals(1024L, prefs().getLong("size", 0L))
        assertEquals(99L, prefs().getLong("date_modified", 0L))
        assertTrue(prefs().getBoolean("is_favorite", false))
    }

    @Test
    fun `a cold reader picks the persisted snapshot up`() {
        AudioNowPlayingSnapshotStore.persist(context, snapshot())
        AudioNowPlayingSnapshotStore.dropCacheForTest()

        assertEquals(snapshot(), AudioNowPlayingSnapshotStore.read(context))
    }

    @Test
    fun `an untouched store reads as inactive`() {
        assertFalse(AudioNowPlayingSnapshotStore.read(context).active)
    }

    @Test
    fun `the favourite toggle keeps the rest of the snapshot`() {
        AudioNowPlayingSnapshotStore.write(context, snapshot())

        AudioNowPlayingSnapshotStore.updateFavoriteState(context, isFavorite = false)

        val updated = AudioNowPlayingSnapshotStore.read(context)
        assertFalse(updated.isFavorite)
        assertEquals("Track", updated.title)
        assertTrue(updated.canToggleFavorite)
    }
}
