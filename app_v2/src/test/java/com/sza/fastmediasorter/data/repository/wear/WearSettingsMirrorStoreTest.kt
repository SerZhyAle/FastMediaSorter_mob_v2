package com.sza.fastmediasorter.data.repository.wear

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sza.fastmediasorter.core.serialization.InstantTypeAdapter
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * S2050: pins the exact on-disk shape (`wear_sync_prefs`, `watch_settings_payload`,
 * `last_sync_timestamp`) the store inherited from `WearSyncViewModel`/`PhoneWearListenerService`, so a
 * future edit here cannot silently break what is already written on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class WearSettingsMirrorStoreTest {

    // RuntimeEnvironment, not ApplicationProvider: androidx.test:core is an androidTest dependency
    // here, so the instrumentation helper does not exist on the unit-test classpath.
    private val context: Context = RuntimeEnvironment.getApplication()

    // Mirrors RepositoryModule.provideGson() exactly (S1668) - a bare Gson() would still round-trip
    // this payload today since it has no java.time.Instant field, but this test claims to pin the
    // on-disk shape, and that claim only holds if the encoder matches production.
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .create()
    private val store = SharedPreferencesWearSettingsMirrorStore(context, gson)

    private val payload = WearSettingsPayload(
        audioEnabled = true,
        videoEnabled = false,
        imagesEnabled = true,
        slideshowEnabled = true,
        slideshowIntervalSeconds = 7,
        downloadAlbumArt = true,
        appLanguage = "ru"
    )

    @Test
    fun `written settings read back equal`() {
        store.writeSettings(payload)

        assertEquals(payload, store.readSettings())
    }

    @Test
    fun `malformed stored json reads back as null instead of throwing`() {
        context.getSharedPreferences("wear_sync_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("watch_settings_payload", "{not json")
            .apply()

        assertNull(store.readSettings())
    }

    @Test
    fun `marked sync timestamp reads back unchanged`() {
        store.markSynced(1_700_000_000_000L)

        assertEquals(1_700_000_000_000L, store.readLastSyncTimestamp())
    }

    @Test
    fun `last sync timestamp defaults to zero when nothing was ever written`() {
        assertEquals(0L, store.readLastSyncTimestamp())
    }
}
