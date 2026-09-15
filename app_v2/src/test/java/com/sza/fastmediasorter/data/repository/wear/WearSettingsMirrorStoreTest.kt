package com.sza.fastmediasorter.data.repository.wear

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sza.fastmediasorter.core.serialization.InstantTypeAdapter
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import kotlinx.coroutines.test.runTest
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
    fun `written settings read back equal`() = runTest {
        store.writeSettings(payload)

        assertEquals(payload, store.readSettings())
    }

    @Test
    fun `malformed stored json reads back as null instead of throwing`() = runTest {
        context.getSharedPreferences("wear_sync_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("watch_settings_payload", "{not json")
            .apply()

        assertNull(store.readSettings())
    }

    @Test
    fun `marked sync timestamp reads back unchanged`() = runTest {
        store.markSynced(1_700_000_000_000L, null)

        assertEquals(1_700_000_000_000L, store.readLastSyncTimestamp())
    }

    @Test
    fun `last sync timestamp defaults to zero when nothing was ever written`() = runTest {
        assertEquals(0L, store.readLastSyncTimestamp())
    }

    // S3068 replaced the anonymous TypeToken subclass behind the stamp map with
    // TypeToken.getParameterized(..), because the anonymous form reads a `Signature` attribute R8
    // strips and crashed the shipped build. The JSON is identical either way, so what needs pinning
    // is that the replacement still types the value as Long - a bare Map::class.java would hand back
    // Double and lose the epoch-millis.
    @Test
    fun `field timestamps read back as Long, not as Gson's default Double`() = runTest {
        context.getSharedPreferences("wear_sync_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("watch_settings_field_timestamps", """{"audioEnabled":1700000000123}""")
            .apply()

        val erased = store.readFieldTimestamps() as Map<String, Any?>

        assertEquals(java.lang.Long::class.java, erased["audioEnabled"]?.javaClass)
        assertEquals(1_700_000_000_123L, erased["audioEnabled"])
    }

    @Test
    fun `written field timestamps read back equal`() = runTest {
        val stamps = mapOf("audioEnabled" to 1_700_000_000_123L, "appLanguage" to 1_700_000_000_456L)

        store.writeFieldTimestamps(stamps)

        assertEquals(stamps, store.readFieldTimestamps())
    }

    @Test
    fun `malformed stored field timestamps read back empty instead of throwing`() = runTest {
        context.getSharedPreferences("wear_sync_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("watch_settings_field_timestamps", "{not json")
            .apply()

        assertEquals(emptyMap<String, Long>(), store.readFieldTimestamps())
    }
}
