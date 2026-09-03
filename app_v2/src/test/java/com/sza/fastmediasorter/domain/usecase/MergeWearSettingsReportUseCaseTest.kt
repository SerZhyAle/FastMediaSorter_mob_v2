package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.repository.wear.WearSettingsMirrorStore
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.model.WearSettingsPayloadDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EARLY_EDIT = 1_000_000L
private const val LATE_EDIT = 2_000_000L
private const val CLOCK_SKEW = 5_000_000L
private const val EXCHANGE_AT = 9_000_000L
private const val WATCH_INTERVAL = 17
private const val PHONE_INTERVAL = 42
private const val WATCH_VERSION = "2.60.9021.951"
private const val PHONE_VERSION = "2.61.0000.001"

// S2462: the boolean half of the six fields that predate nullability. slideshowIntervalSeconds is the
// sixth and is exercised separately, its absence being visible as a number rather than a flag.
private val FIRST_WAVE_BOOLEANS = listOf(
    "audioEnabled",
    "videoEnabled",
    "imagesEnabled",
    "slideshowEnabled",
    "downloadAlbumArt"
)

class MergeWearSettingsReportUseCaseTest {

    @Test
    fun `a report with no timestamps is taken whole, as a watch predating the exchange sends it`() {
        val store = FakeWearSettingsMirrorStore().apply {
            settings = phoneSet()
            stamps = mapOf("slideshowIntervalSeconds" to LATE_EDIT)
        }

        val merged = MergeWearSettingsReportUseCase(store)(watchSet(), null, EXCHANGE_AT)

        assertEquals(WATCH_INTERVAL, merged.slideshowIntervalSeconds)
    }

    @Test
    fun `a field the watch edited later replaces the phone value and carries its stamp`() {
        val store = FakeWearSettingsMirrorStore().apply {
            settings = phoneSet()
            stamps = mapOf("slideshowIntervalSeconds" to EARLY_EDIT)
        }

        val merged = MergeWearSettingsReportUseCase(store)(
            watchSet().copy(fieldTimestamps = mapOf("slideshowIntervalSeconds" to LATE_EDIT)),
            EXCHANGE_AT,
            EXCHANGE_AT
        )

        assertEquals(WATCH_INTERVAL, merged.slideshowIntervalSeconds)
        assertEquals(LATE_EDIT, store.stamps["slideshowIntervalSeconds"])
    }

    @Test
    fun `a field the phone edited later survives the report`() {
        val store = FakeWearSettingsMirrorStore().apply {
            settings = phoneSet()
            stamps = mapOf("slideshowIntervalSeconds" to LATE_EDIT)
        }

        val merged = MergeWearSettingsReportUseCase(store)(
            watchSet().copy(fieldTimestamps = mapOf("slideshowIntervalSeconds" to EARLY_EDIT)),
            EXCHANGE_AT,
            EXCHANGE_AT
        )

        assertEquals(PHONE_INTERVAL, merged.slideshowIntervalSeconds)
    }

    @Test
    fun `each side keeps its own later edit when the two changed different fields`() {
        val store = FakeWearSettingsMirrorStore().apply {
            settings = phoneSet().copy(audioEnabled = false)
            stamps = mapOf("audioEnabled" to LATE_EDIT, "slideshowIntervalSeconds" to EARLY_EDIT)
        }

        val merged = MergeWearSettingsReportUseCase(store)(
            watchSet().copy(
                audioEnabled = true,
                fieldTimestamps = mapOf("slideshowIntervalSeconds" to LATE_EDIT)
            ),
            EXCHANGE_AT,
            EXCHANGE_AT
        )

        assertEquals(false, merged.audioEnabled)
        assertEquals(WATCH_INTERVAL, merged.slideshowIntervalSeconds)
    }

    @Test
    fun `a watch whose clock lags still wins with the later edit`() {
        val store = FakeWearSettingsMirrorStore().apply {
            settings = phoneSet()
            stamps = mapOf("slideshowIntervalSeconds" to LATE_EDIT)
        }

        // The watch's clock is a full skew behind, so its genuinely later edit reads as the earlier
        // number until sentAt is compared against the arrival time.
        val merged = MergeWearSettingsReportUseCase(store)(
            watchSet().copy(
                fieldTimestamps = mapOf("slideshowIntervalSeconds" to LATE_EDIT - CLOCK_SKEW + 1)
            ),
            EXCHANGE_AT - CLOCK_SKEW,
            EXCHANGE_AT
        )

        assertEquals(WATCH_INTERVAL, merged.slideshowIntervalSeconds)
    }

    @Test
    fun `the phone keeps its own language even when the watch reports one`() {
        val store = FakeWearSettingsMirrorStore().apply {
            settings = phoneSet().copy(appLanguage = "uk")
            stamps = mapOf("appLanguage" to EARLY_EDIT)
        }

        val merged = MergeWearSettingsReportUseCase(store)(
            watchSet().copy(appLanguage = "de", fieldTimestamps = mapOf("appLanguage" to LATE_EDIT)),
            EXCHANGE_AT,
            EXCHANGE_AT
        )

        assertEquals("uk", merged.appLanguage)
    }

    @Test
    fun `the first report becomes the mirror and marks the sides as agreed`() {
        val store = FakeWearSettingsMirrorStore()

        val merged = MergeWearSettingsReportUseCase(store)(watchSet(), EXCHANGE_AT, EXCHANGE_AT)

        assertEquals(WATCH_INTERVAL, merged.slideshowIntervalSeconds)
        assertEquals(merged, store.settings)
        assertEquals(EXCHANGE_AT, store.lastSync)
        assertTrue(store.writtenSettingsCount == 1)
    }

    // S2462: the six fields that predate nullability cannot express "the watch did not send this" -
    // Gson fabricates false/0 for an absent key - so absence is carried beside the payload as the set
    // of keys that really arrived. Each field gets a pair: absent keeps the stored value, and a
    // genuinely sent false still applies. Without the second half the fix would trade a silent
    // overwrite for a silently ignored setting.
    @Test
    fun `a field the watch never sent leaves the stored value alone`() {
        FIRST_WAVE_BOOLEANS.forEach { field ->
            val store = FakeWearSettingsMirrorStore().apply { settings = allOnPhoneSet() }

            val merged = MergeWearSettingsReportUseCase(store)(
                allOffWatchSet(),
                null,
                EXCHANGE_AT,
                WearSettingsPayloadDecoder.CONTRACT_FIELDS - field
            )

            assertTrue("$field must survive a report that omitted it", merged.readBoolean(field))
        }
    }

    @Test
    fun `a field the watch really sent as false is still applied`() {
        FIRST_WAVE_BOOLEANS.forEach { field ->
            val store = FakeWearSettingsMirrorStore().apply { settings = allOnPhoneSet() }

            val merged = MergeWearSettingsReportUseCase(store)(
                allOffWatchSet(),
                null,
                EXCHANGE_AT,
                WearSettingsPayloadDecoder.CONTRACT_FIELDS
            )

            assertFalse("$field was sent as false and must be applied", merged.readBoolean(field))
        }
    }

    @Test
    fun `a mistyped interval leaves that field alone and applies the rest of the report`() {
        val store = FakeWearSettingsMirrorStore().apply { settings = allOnPhoneSet() }

        val merged = MergeWearSettingsReportUseCase(store)(
            allOffWatchSet(),
            null,
            EXCHANGE_AT,
            WearSettingsPayloadDecoder.CONTRACT_FIELDS - "slideshowIntervalSeconds"
        )

        assertEquals(PHONE_INTERVAL, merged.slideshowIntervalSeconds)
        assertFalse(merged.audioEnabled)
    }

    private fun WearSettingsPayload.readBoolean(field: String): Boolean = when (field) {
        "audioEnabled" -> audioEnabled
        "videoEnabled" -> videoEnabled
        "imagesEnabled" -> imagesEnabled
        "slideshowEnabled" -> slideshowEnabled
        "downloadAlbumArt" -> downloadAlbumArt
        else -> error("unmapped first-wave field $field")
    }

    private fun allOnPhoneSet() = phoneSet().copy(
        audioEnabled = true,
        videoEnabled = true,
        imagesEnabled = true,
        slideshowEnabled = true,
        downloadAlbumArt = true
    )

    private fun allOffWatchSet() = watchSet().copy(
        audioEnabled = false,
        videoEnabled = false,
        imagesEnabled = false,
        slideshowEnabled = false,
        downloadAlbumArt = false
    )

    @Test
    fun `S2461 the reported version is stored together with the sync time`() {
        val store = FakeWearSettingsMirrorStore().apply { settings = phoneSet() }

        MergeWearSettingsReportUseCase(store)(
            watchSet().copy(appVersionName = WATCH_VERSION),
            null,
            EXCHANGE_AT
        )

        assertEquals(EXCHANGE_AT, store.lastSync)
        assertEquals(WATCH_VERSION, store.watchAppVersion)
    }

    @Test
    fun `S2461 a report with no version clears the stored one instead of leaving it beside a fresh time`() {
        val store = FakeWearSettingsMirrorStore().apply {
            settings = phoneSet()
            watchAppVersion = WATCH_VERSION
        }

        MergeWearSettingsReportUseCase(store)(watchSet(), null, EXCHANGE_AT)

        assertEquals(EXCHANGE_AT, store.lastSync)
        assertNull(store.watchAppVersion)
    }

    @Test
    fun `S2461 the merged set carries the incoming version, never the stored one`() {
        val store = FakeWearSettingsMirrorStore().apply {
            settings = phoneSet().copy(appVersionName = PHONE_VERSION)
        }

        val merged = MergeWearSettingsReportUseCase(store)(
            watchSet().copy(appVersionName = WATCH_VERSION),
            null,
            EXCHANGE_AT
        )

        assertEquals(WATCH_VERSION, merged.appVersionName)
    }

    private fun watchSet() = WearSettingsPayload(
        audioEnabled = true,
        videoEnabled = true,
        imagesEnabled = true,
        slideshowEnabled = false,
        slideshowIntervalSeconds = WATCH_INTERVAL,
        downloadAlbumArt = false
    )

    private fun phoneSet() = WearSettingsPayload(
        audioEnabled = true,
        videoEnabled = true,
        imagesEnabled = true,
        slideshowEnabled = false,
        slideshowIntervalSeconds = PHONE_INTERVAL,
        downloadAlbumArt = false
    )
}

private class FakeWearSettingsMirrorStore : WearSettingsMirrorStore {
    var settings: WearSettingsPayload? = null
    var stamps: Map<String, Long> = emptyMap()
    var lastSync = 0L
    var writtenSettingsCount = 0
    var watchAppVersion: String? = null

    override fun readSettings(): WearSettingsPayload? = settings

    override fun writeSettings(settings: WearSettingsPayload) {
        this.settings = settings
        writtenSettingsCount++
    }

    override fun readLastSyncTimestamp(): Long = lastSync

    override fun readWatchAppVersion(): String? = watchAppVersion

    override fun markSynced(atEpochMillis: Long, watchAppVersionName: String?) {
        lastSync = atEpochMillis
        watchAppVersion = watchAppVersionName
    }

    override fun readFieldTimestamps(): Map<String, Long> = stamps

    override fun writeFieldTimestamps(stamps: Map<String, Long>) {
        this.stamps = stamps
    }
}
