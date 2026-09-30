package com.sza.fastmediasorter.wear.data.wear

import com.sza.fastmediasorter.wear.domain.repository.WearPhoneBatteryRepository
import com.sza.fastmediasorter.wear.domain.usecase.RequestWearComplicationRefreshUseCase
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3764: applies one phone battery report from the phone.
 *
 * Its own class rather than a branch body in the listener service, for [WearClockStyleReceiver]'s
 * reason: the write and the face refresh must outlive that service's callback, which the platform
 * destroys shortly after it returns.
 */
@Singleton
class PhoneBatteryReportReceiver @Inject constructor(
    private val phoneBatteryRepository: WearPhoneBatteryRepository,
    private val requestComplicationRefresh: RequestWearComplicationRefreshUseCase
) {

    /**
     * An unreadable packet is dropped and the stored report kept: losing one bad message must not
     * empty a bar that still holds a usable charge ([WearClockStyleReceiver]'s rule).
     */
    suspend fun handle(payload: ByteArray) {
        val report = PhoneBatteryReportCodec.decodeEnvelope(payload)
        if (report == null) {
            Timber.w("Dropped an unreadable phone battery packet - keeping the stored report")
            return
        }
        try {
            phoneBatteryRepository.save(report)
        } catch (e: IOException) {
            Timber.w(e, "Failed to store the phone battery report - the watch keeps the previous one")
            return
        }
        // The style provider declares no polling, so without this request the face keeps the old
        // band until something else re-renders it - a battery tick has to show on the wrist at once.
        requestComplicationRefresh.refreshWatchFaceStyle()
    }
}
