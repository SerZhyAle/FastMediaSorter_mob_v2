package com.sza.fastmediasorter.golden

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.data.repository.SettingsRepositoryImpl
import com.sza.fastmediasorter.data.repository.settings.RawPreferencesStore
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BackupPreference
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S3371 phase 06 step 06.1: golden proof that today's settings reader still reads yesterday's
 * settings store.
 *
 * FIXTURE TRANSPORT. The settings store itself is a DataStore protobuf, which is not a readable
 * fixture. Each fixture is therefore the SAME store content written in the key/type/value vocabulary
 * the app itself uses to move that store - [BackupPreference], the raw settings section of a backup
 * (S3130) - and is loaded through the app's own writer, [RawPreferencesStore.apply]. The fixture is
 * a transcription of the stored entries, not a second format.
 *
 * WHERE EACH FIXTURE'S SHAPE COMES FROM - every key below is read by a line of production code that
 * states why it is still read:
 *
 * - `settings_v1.json` is the store of an install that predates S0847/S1008. It carries the single
 *   `screenshot_gesture_strip_visible` / `screenshot_gesture_action_*` keys that
 *   `ScreenshotSettingsStore` still names "legacy single-strip keys, read-only for LEFT_TOP
 *   migration (no longer written)", and none of the per-zone keys that replaced them. It also
 *   carries `vr_auto_detect_format`, `vr_forced_format` and `vr_remember_file_format`, which
 *   `SettingsRepositoryImpl` records as "legacy keys removed by S0241 / S0251 .. orphan DataStore
 *   entries left over in existing installs are ignored by the new build path", and
 *   `enable_background_audio`, whose comment pins the spelling "for backward compatibility with
 *   existing user settings".
 * - `settings_v2.json` is the store of an install upgraded in place: both generations of the
 *   screenshot-gesture keys are present, because the migration reads the legacy keys and never
 *   deletes them. It also carries a `gesture_payload_*` entry (S1038), the newest of the three
 *   generations.
 *
 * No other format history is asserted here, because none is recorded in the tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.16.1 maxSdkVersion=34; the module's targetSdk needs the pin.
class SettingsGoldenCompatTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // UnconfinedTestDispatcher, not Dispatchers.IO: a scope built on a real dispatcher in a test
    // source set is counted by the test-unjoined-scope rule whether or not @After joins it, because
    // no regex can link the two (S2748). Unconfined runs the DataStore writes eagerly on the calling
    // thread, so the fixtures still land before each assertion.
    private val storeScope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())

    @After
    fun tearDown() {
        // Join rather than cancel: TemporaryFolder deletes the directory once @After returns, and an
        // unfinished DataStore flush would then meet a deleted file (S2748).
        runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
    }

    @Test
    fun `pre-S0847 store still resolves its single-strip gesture bindings onto LEFT_TOP`() = runTest {
        val settings = loadFixture("settings_v1.json", "golden_settings_v1.preferences_pb")

        val gesture = settings.screenshotGesture
        assertTrue(
            "legacy screenshot_gesture_strip_visible must light the LEFT_TOP strip",
            gesture.zoneLeftTopStripVisible
        )
        assertEquals(ScreenshotGestureAction.SILENT_SCREENSHOT, gesture.leftTopDown)
        assertEquals(ScreenshotGestureAction.SHARE, gesture.leftTopRight)
        assertEquals(ScreenshotGestureAction.OCR_TRANSLATE, gesture.leftTopUp)
        assertFalse("no per-zone key was stored, so the other bands stay dark", gesture.zoneLeftBottomStripVisible)
        assertEquals(ScreenshotGestureAction.DO_NOT_USE, gesture.leftBottomDown)
    }

    @Test
    fun `pre-S0847 store loads despite the VR keys S0241 and S0251 retired`() = runTest {
        val settings = loadFixture("settings_v1.json", "golden_settings_v1_orphans.preferences_pb")

        // The orphan VR entries are still physically in the store; the reader must ignore them and
        // return every neighbouring value intact rather than failing the whole snapshot.
        assertFalse(settings.preventSleep)
        assertEquals(1024, settings.cacheSizeMb)
        assertEquals(8, settings.networkParallelism)
        assertEquals(52428800L, settings.textSizeMax)
    }

    @Test
    fun `upgraded store prefers the per-zone keys over the legacy ones they replaced`() = runTest {
        val settings = loadFixture("settings_v2.json", "golden_settings_v2.preferences_pb")

        val gesture = settings.screenshotGesture
        assertFalse("the per-zone key wins over the legacy strip-visible key", gesture.zoneLeftTopStripVisible)
        assertEquals(ScreenshotGestureAction.OPEN_URL, gesture.leftTopDown)
        assertEquals(ScreenshotGestureAction.VOLUME_UP, gesture.leftTopRight)
        assertEquals(ScreenshotGestureAction.DO_NOT_USE, gesture.leftTopUp)
        assertEquals(ScreenshotGestureAction.MEDIA_PLAY_PAUSE, gesture.leftBottomDown)
        assertEquals("https://example.org/report", gesture.payloadLeftTopDown)
        assertTrue(gesture.zoneLeftBottomStripVisible)
    }

    @Test
    fun `a store written from the v1 fixture exports and re-imports to the same settings`() = runTest {
        assertRoundTrips("settings_v1.json", "v1")
    }

    @Test
    fun `a store written from the v2 fixture exports and re-imports to the same settings`() = runTest {
        assertRoundTrips("settings_v2.json", "v2")
    }

    /**
     * The write half of the contract: what the current build exports from a store loaded out of the
     * fixture must load back into the identical snapshot. A field the reader added since the fixture
     * was written resolves to its default on both passes, so it cannot mask a lost entry.
     */
    private suspend fun assertRoundTrips(fixture: String, tag: String) {
        val firstStore = dataStore("golden_settings_${tag}_rt_a.preferences_pb")
        firstStore.edit { RawPreferencesStore.apply(it, readFixture(fixture)) }
        val firstLoad = repositoryOver(firstStore).getSettings().first()

        val exported = RawPreferencesStore.export(firstStore.data.first())
        val secondStore = dataStore("golden_settings_${tag}_rt_b.preferences_pb")
        secondStore.edit { RawPreferencesStore.apply(it, exported) }
        val secondLoad = repositoryOver(secondStore).getSettings().first()

        assertEquals(firstLoad, secondLoad)
    }

    private suspend fun loadFixture(fixture: String, storeFileName: String): AppSettings {
        val store = dataStore(storeFileName)
        store.edit { RawPreferencesStore.apply(it, readFixture(fixture)) }
        return repositoryOver(store).getSettings().first()
    }

    private fun repositoryOver(store: DataStore<Preferences>) =
        SettingsRepositoryImpl(RuntimeEnvironment.getApplication(), store)

    private fun readFixture(fileName: String): List<BackupPreference> {
        val path = "golden/settings/$fileName"
        val json = checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Golden fixture $path is missing from the test resources"
        }.bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<BackupPreference>>() {}.type
        return Gson().fromJson(json, type)
    }

    /**
     * Okio storage, not the default File storage: FileStorage persists via `File.renameTo`, which on
     * Windows cannot replace an existing destination, so every write after the first one failed
     * (S1449).
     */
    private fun dataStore(fileName: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                tempFolder.root.resolve(fileName).toOkioPath()
            },
            scope = storeScope,
        )
}
