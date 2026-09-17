package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BroadcastSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2817: regression coverage for [BroadcastSettingsStore]. The store methods are pure over
 * `Preferences`, so an in-memory `mutablePreferencesOf()` exercises the same read/write path
 * used on device without Robolectric.
 */
class BroadcastSettingsStoreTest {

    private fun settingsOf(broadcast: BroadcastSettings) = AppSettings(broadcast = broadcast)

    @Test
    fun `absent keys resolve to the pre-S2817 hard-coded defaults`() {
        val values = BroadcastSettingsStore.read(mutablePreferencesOf()).broadcast

        // Pinned as literals - reading the constant the store itself reads would compare it with
        // itself and pin nothing.
        assertEquals("Phone Stream", values.streamTitle)
        assertEquals(128_000, values.bitRateBps)
        assertEquals(8768, values.port)
        assertEquals(44_100, values.sampleRateHz)
        assertEquals(1, values.channelCount)
        assertTrue(values.autoOpenShare)
    }

    @Test
    fun `every persisted broadcast field round-trips through write then read`() {
        val broadcast = BroadcastSettings(
            streamTitle = "My Live Stream",
            bitRateBps = 256_000,
            port = 9000,
            sampleRateHz = 48_000,
            channelCount = 2,
            autoOpenShare = false,
        )

        val prefs = mutablePreferencesOf()
        BroadcastSettingsStore.write(prefs, settingsOf(broadcast))
        val values = BroadcastSettingsStore.read(prefs).broadcast

        assertEquals(broadcast, values)
    }

    @Test
    fun `the video and microphone fields round-trip too`() {
        // S3222: they are persisted by `write` and, since the fold, restored by `read` into the same
        // group the capture services read - before it, six of them were written and never read back.
        val broadcast = BroadcastSettings(
            cameraEnabled = true,
            microphoneEnabled = false,
            videoWidth = 1920,
            videoHeight = 1080,
            videoFps = 60,
            videoBitrateBps = 6_000_000,
            micGainPercent = 250,
        )

        val prefs = mutablePreferencesOf()
        BroadcastSettingsStore.write(prefs, settingsOf(broadcast))

        assertEquals(broadcast, BroadcastSettingsStore.read(prefs).broadcast)
    }

    @Test
    fun `a stored legacy title resolves like an absent key`() {
        val prefs = mutablePreferencesOf()
        BroadcastSettingsStore.write(
            prefs,
            settingsOf(BroadcastSettings(streamTitle = "Phone Audio Stream")),
        )

        val values = BroadcastSettingsStore.read(prefs).broadcast

        // S3173: every settings write persisted the old default, so an installation holding it never
        // chose that title - it must not survive the fix as a user value.
        assertEquals("Phone Stream", values.streamTitle)
    }

    @Test
    fun `auto-open share defaults to true when the key is absent`() {
        val prefs = mutablePreferencesOf()
        BroadcastSettingsStore.write(
            prefs,
            settingsOf(BroadcastSettings(streamTitle = "Title Only")),
        )

        val values = BroadcastSettingsStore.read(prefs).broadcast

        assertEquals("Title Only", values.streamTitle)
        assertTrue(values.autoOpenShare)
    }

    @Test
    fun `disabling auto-open share persists and reads back`() {
        val prefs = mutablePreferencesOf()
        BroadcastSettingsStore.write(prefs, settingsOf(BroadcastSettings(autoOpenShare = false)))

        assertFalse(BroadcastSettingsStore.read(prefs).broadcast.autoOpenShare)
    }
}
