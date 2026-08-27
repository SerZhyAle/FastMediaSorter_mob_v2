package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.repository.wear.WearSettingsMirrorStore
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EARLY_EDIT = 1_000_000L
private const val LATE_EDIT = 2_000_000L
private const val CLOCK_SKEW = 5_000_000L
private const val EXCHANGE_AT = 9_000_000L
private const val WATCH_INTERVAL = 17
private const val PHONE_INTERVAL = 42

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

    override fun readSettings(): WearSettingsPayload? = settings

    override fun writeSettings(settings: WearSettingsPayload) {
        this.settings = settings
        writtenSettingsCount++
    }

    override fun readLastSyncTimestamp(): Long = lastSync

    override fun markSynced(atEpochMillis: Long) {
        lastSync = atEpochMillis
    }

    override fun readFieldTimestamps(): Map<String, Long> = stamps

    override fun writeFieldTimestamps(stamps: Map<String, Long>) {
        this.stamps = stamps
    }
}
