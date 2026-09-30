package com.sza.fastmediasorter.wear.domain.model

/**
 * S3764: the paired phone's battery report - the charge the watch face's phone bar renders.
 *
 * [timestampMs] is the freshness mark (strategic §5.1 pillar 3): the face provider composes the
 * stale sentinel instead of the percent when the report is older than
 * [com.sza.fastmediasorter.wear.domain.repository.WearPhoneBatteryRepository.STALE_AFTER_MS], so a
 * dead link cannot keep showing a charge the phone may have lost hours ago.
 */
data class PhoneBatteryReport(
    val percent: Int,
    val isCharging: Boolean,
    val timestampMs: Long
)
