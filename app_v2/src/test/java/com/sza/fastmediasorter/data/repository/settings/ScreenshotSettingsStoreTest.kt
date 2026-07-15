package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1038 Phase 02: round-trip coverage for the generic per-slot payload persisted by
 * [ScreenshotSettingsStore]. The store methods are pure over `Preferences`, so an in-memory
 * `mutablePreferencesOf()` exercises the same read/write path used on device without Robolectric.
 */
class ScreenshotSettingsStoreTest {

    @Test
    fun `payload defaults to empty for every slot`() {
        val values = ScreenshotSettingsStore.read(mutablePreferencesOf())

        assertEquals("", values.payloadLeftTopDown)
        assertEquals("", values.payloadLeftTopRight)
        assertEquals("", values.payloadLeftTopUp)
        assertEquals("", values.payloadLeftBottomDown)
        assertEquals("", values.payloadLeftBottomRight)
        assertEquals("", values.payloadLeftBottomUp)
        assertEquals("", values.payloadRightTopDown)
        assertEquals("", values.payloadRightTopRight)
        assertEquals("", values.payloadRightTopUp)
        assertEquals("", values.payloadRightBottomDown)
        assertEquals("", values.payloadRightBottomRight)
        assertEquals("", values.payloadRightBottomUp)
    }

    @Test
    fun `saved payload for one slot round-trips and leaves other slots empty`() {
        val url = "https://example.com/open"
        val settings = AppSettings(screenshotGesturePayloadRightBottomUp = url)

        val prefs = mutablePreferencesOf()
        ScreenshotSettingsStore.write(prefs, settings)
        val values = ScreenshotSettingsStore.read(prefs)

        assertEquals(url, values.payloadRightBottomUp)
        // The written value re-reads through the AppSettings accessor for the same slot.
        val loaded = AppSettings(screenshotGesturePayloadRightBottomUp = values.payloadRightBottomUp)
        assertEquals(
            url,
            loaded.screenshotGesturePayload(
                ScreenshotGestureZone.RIGHT_BOTTOM,
                ScreenshotGestureDirection.UP,
            ),
        )
        // A neighbouring slot stays at the empty default.
        assertEquals("", values.payloadLeftTopDown)
    }
}
