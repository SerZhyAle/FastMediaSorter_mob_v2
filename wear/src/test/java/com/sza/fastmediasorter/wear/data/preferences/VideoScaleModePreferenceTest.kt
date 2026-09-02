package com.sza.fastmediasorter.wear.data.preferences

import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * S1948 §3.2 requires a watch that never touched the scale button to keep fitting the frame, and
 * that guarantee lives entirely in the fallback of the name mapping.
 *
 * DataStore needs an Android context and the watch module has no Robolectric harness, so the
 * property is asserted where it actually lives - in the key name and in the mapping's default.
 */
class VideoScaleModePreferenceTest {

    @Test
    fun `an absent or unrecognised stored value keeps the previous fit behaviour`() {
        assertEquals(VideoScaleMode.FIT, VideoScaleMode.fromNameOrDefault(null))
        assertEquals(VideoScaleMode.FIT, VideoScaleMode.fromNameOrDefault("STRETCH"))
        assertEquals(VideoScaleMode.FIT, VideoScaleMode.fromNameOrDefault(""))
    }

    @Test
    fun `every mode round-trips through its stored name`() {
        VideoScaleMode.entries.forEach { mode ->
            assertEquals(mode, VideoScaleMode.fromNameOrDefault(mode.name))
        }
    }

    @Test
    fun `the scale mode does not share a stored key with any other preference`() {
        val scaleKey = WearPreferencesRepositoryImpl.PreferencesKeys.VIDEO_SCALE_MODE.name
        val neighbours = listOf(
            WearPreferencesRepositoryImpl.PreferencesKeys.VIEW_MODE.name,
            WearPreferencesRepositoryImpl.PreferencesKeys.FILE_LIST_VIEW_MODE.name
        )
        assertFalse(scaleKey in neighbours)
    }
}
