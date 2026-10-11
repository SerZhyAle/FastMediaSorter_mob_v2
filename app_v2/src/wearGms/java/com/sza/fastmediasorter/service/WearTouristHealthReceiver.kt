package com.sza.fastmediasorter.service

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.sza.fastmediasorter.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.domain.model.WearTouristHealthPayload
import com.sza.fastmediasorter.domain.model.tourist.WatchHealthReading
import com.sza.fastmediasorter.domain.repository.WatchHealthRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * S4150: turns the watch Tourist health Data Item into the latest reading the dashboard shows.
 *
 * A collaborator rather than a branch body in [PhoneWearListenerService], which has no room for another
 * function. The phone's arrival time is stored, not the watch's `sentAt`, so freshness is judged on one
 * clock. Kept in every companion flavor: whether the tiles are offered is decided by the dashboard.
 */
class WearTouristHealthReceiver @Inject constructor(
    private val gson: Gson,
    private val repository: WatchHealthRepository,
) {
    private val envelopeCodec = WearEventEnvelopeCodec()

    fun handle(data: ByteArray, receivedAtEpochMillis: Long) {
        try {
            val envelope = envelopeCodec.decode(data)
            val payload = gson.fromJson(envelope.data.decodeToString(), WearTouristHealthPayload::class.java)
                ?: return
            repository.publish(
                WatchHealthReading(payload.heartRateBpm, payload.bodyTemperatureCelsius, receivedAtEpochMillis),
            )
            Timber.d("S4150: health received hr=%s t=%s", payload.heartRateBpm, payload.bodyTemperatureCelsius)
        } catch (e: JsonParseException) {
            Timber.w(e, "Watch Tourist health report not decodable - reading dropped")
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Watch Tourist health envelope malformed - reading dropped")
        } catch (e: IllegalStateException) {
            Timber.w(e, "Watch Tourist health envelope is not an object - reading dropped")
        }
    }
}
