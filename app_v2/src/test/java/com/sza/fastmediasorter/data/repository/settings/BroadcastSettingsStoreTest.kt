package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.sza.fastmediasorter.domain.model.AppSettings
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

    @Test
    fun `absent keys resolve to the pre-S2817 hard-coded defaults`() {
        val values = BroadcastSettingsStore.read(mutablePreferencesOf())

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
        val settings = AppSettings(
            broadcastStreamTitle = "My Live Stream",
            broadcastBitRateBps = 256_000,
            broadcastPort = 9000,
            broadcastSampleRateHz = 48_000,
            broadcastChannelCount = 2,
            broadcastAutoOpenShare = false,
        )

        val prefs = mutablePreferencesOf()
        BroadcastSettingsStore.write(prefs, settings)
        val values = BroadcastSettingsStore.read(prefs)

        assertEquals(settings.broadcastStreamTitle, values.streamTitle)
        assertEquals(settings.broadcastBitRateBps, values.bitRateBps)
        assertEquals(settings.broadcastPort, values.port)
        assertEquals(settings.broadcastSampleRateHz, values.sampleRateHz)
        assertEquals(settings.broadcastChannelCount, values.channelCount)
        assertEquals(settings.broadcastAutoOpenShare, values.autoOpenShare)
    }

    @Test
    fun `a stored legacy title resolves like an absent key`() {
        val prefs = mutablePreferencesOf()
        BroadcastSettingsStore.write(
            prefs,
            AppSettings(broadcastStreamTitle = "Phone Audio Stream"),
        )

        val values = BroadcastSettingsStore.read(prefs)

        // S3173: every settings write persisted the old default, so an installation holding it never
        // chose that title - it must not survive the fix as a user value.
        assertEquals("Phone Stream", values.streamTitle)
    }

    @Test
    fun `auto-open share defaults to true when the key is absent`() {
        val prefs = mutablePreferencesOf()
        BroadcastSettingsStore.write(
            prefs,
            AppSettings(broadcastStreamTitle = "Title Only"),
        )

        val values = BroadcastSettingsStore.read(prefs)

        assertEquals("Title Only", values.streamTitle)
        assertTrue(values.autoOpenShare)
    }

    @Test
    fun `disabling auto-open share persists and reads back`() {
        val prefs = mutablePreferencesOf()
        BroadcastSettingsStore.write(prefs, AppSettings(broadcastAutoOpenShare = false))

        assertFalse(BroadcastSettingsStore.read(prefs).autoOpenShare)
    }
}
