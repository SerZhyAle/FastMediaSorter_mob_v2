package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.PhoneBatteryReport
import kotlinx.coroutines.flow.Flow

/**
 * S3764: the last battery report the phone published, which the watch face's style provider reads
 * to compose the phone band of its multiplexed code. Null until the phone sends one.
 *
 * [STALE_AFTER_MS] is the single knob deciding "stale" for the provider band and for any owner
 * review of the empty-track default (research 03). It is deliberately generous - a resting phone
 * publishes nothing until its charge or charging state changes - so a live link at a steady charge
 * keeps its last percent on the face instead of emptying every half hour.
 */
interface WearPhoneBatteryRepository {
    val report: Flow<PhoneBatteryReport?>

    suspend fun save(report: PhoneBatteryReport)

    companion object {
        const val STALE_AFTER_MS = 60L * 60L * 1000L
    }
}
