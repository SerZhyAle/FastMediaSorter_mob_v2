package com.sza.fastmediasorter.service

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.domain.model.WearTouristWeatherPayload
import com.sza.fastmediasorter.domain.model.WearTouristWeatherRequestPayload
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.domain.usecase.tourist.GetWatchTouristWeatherUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * S4152: answers the watch Tourist's weather request with the phone's weather at the watch position.
 *
 * A collaborator rather than a branch body in [PhoneWearListenerService], which has no room for another
 * function. No answer is published when the phone has no reading at all: the watch keeps a previous one
 * while it is fresh and otherwise shows no weather tiles, which is the correct picture of "no data".
 */
class WearTouristWeatherRequestHandler @Inject constructor(
    private val gson: Gson,
    private val getWatchTouristWeatherUseCase: GetWatchTouristWeatherUseCase,
    private val wearableRepository: WearableDataLayerRepository,
) {
    private val envelopeCodec = WearEventEnvelopeCodec()

    suspend fun handle(data: ByteArray) {
        val request = decode(data) ?: return
        val answer: WearTouristWeatherPayload? = getWatchTouristWeatherUseCase(request.latitude, request.longitude)
        if (answer == null) {
            Timber.i("Watch Tourist weather request left unanswered - no reading on the phone")
            return
        }
        runCatching {
            val envelope = WearEventEnvelope(
                eventType = WearDataLayerPaths.EVENT_TOURIST_WEATHER,
                sentAt = System.currentTimeMillis(),
                data = gson.toJson(answer).toByteArray(Charsets.UTF_8),
            )
            wearableRepository.putEnvelopeDataItem(WearDataLayerPaths.TOURIST_WEATHER, envelope)
        }.onFailure { e ->
            e.rethrowIfCancellation()
            Timber.w(e, "Watch Tourist weather answer not sent")
        }
    }

    private fun decode(data: ByteArray): WearTouristWeatherRequestPayload? = try {
        val envelope = envelopeCodec.decode(data)
        gson.fromJson(envelope.data.decodeToString(), WearTouristWeatherRequestPayload::class.java)
    } catch (e: JsonParseException) {
        Timber.w(e, "Watch Tourist weather request not decodable - dropped")
        null
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "Watch Tourist weather request envelope malformed - dropped")
        null
    } catch (e: IllegalStateException) {
        Timber.w(e, "Watch Tourist weather request envelope is not an object - dropped")
        null
    }
}
