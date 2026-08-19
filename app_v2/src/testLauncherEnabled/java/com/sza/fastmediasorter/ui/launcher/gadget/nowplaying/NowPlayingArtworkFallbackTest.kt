package com.sza.fastmediasorter.ui.launcher.gadget.nowplaying

import android.graphics.Bitmap
import android.media.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1177 phase 01: the artwork fallback order, asserted without a live media session.
 *
 * The order is the part that has to survive editing: which key a given player fills is measured per
 * application rather than documented, so a later edit that reduced the list to one key would leave the
 * card blank exactly on the players that publish the other keys.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NowPlayingArtworkFallbackTest {

    private val albumArt: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val art: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val displayIcon: Bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)

    @Test
    fun `album art wins when every key is published`() {
        val picked = ActiveSessionNowPlayingSource.selectArtwork { key ->
            when (key) {
                MediaMetadata.METADATA_KEY_ALBUM_ART -> albumArt
                MediaMetadata.METADATA_KEY_ART -> art
                MediaMetadata.METADATA_KEY_DISPLAY_ICON -> displayIcon
                else -> null
            }
        }

        assertEquals(albumArt, picked)
    }

    @Test
    fun `the generic art key is taken when album art is absent`() {
        val picked = ActiveSessionNowPlayingSource.selectArtwork { key ->
            if (key == MediaMetadata.METADATA_KEY_ART) art else null
        }

        assertEquals(art, picked)
    }

    @Test
    fun `the display icon is the last resort before giving up`() {
        val picked = ActiveSessionNowPlayingSource.selectArtwork { key ->
            if (key == MediaMetadata.METADATA_KEY_DISPLAY_ICON) displayIcon else null
        }

        assertEquals(displayIcon, picked)
    }

    /**
     * Null rather than a blank bitmap: the card distinguishes "no artwork" from "empty artwork" and
     * falls back to the playing application's icon, which it cannot do if it is handed a blank image.
     */
    @Test
    fun `no artwork at all yields null`() {
        assertNull(ActiveSessionNowPlayingSource.selectArtwork { null })
    }

    @Test
    fun `the order is album art, then art, then display icon`() {
        assertEquals(
            listOf(
                MediaMetadata.METADATA_KEY_ALBUM_ART,
                MediaMetadata.METADATA_KEY_ART,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON,
            ),
            ActiveSessionNowPlayingSource.ARTWORK_KEYS,
        )
    }

    /**
     * The lookup stops at the first hit rather than probing every key: a session may build a large bitmap
     * on demand, and asking for one already answered is paid work on the home screen.
     */
    @Test
    fun `lookup stops at the first published key`() {
        val asked = mutableListOf<String>()

        ActiveSessionNowPlayingSource.selectArtwork { key ->
            asked += key
            if (key == MediaMetadata.METADATA_KEY_ALBUM_ART) albumArt else null
        }

        assertTrue(asked == listOf(MediaMetadata.METADATA_KEY_ALBUM_ART))
    }
}
