package com.sza.fastmediasorter.wear.data.tourist

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearTouristWeatherPayload
import com.sza.fastmediasorter.wear.domain.model.WearTouristWeatherRequestPayload
import com.sza.fastmediasorter.wear.domain.repository.WearTouristWeatherRepository
import com.sza.fastmediasorter.wear.domain.tourist.WearWeatherPosition
import com.sza.fastmediasorter.wear.util.rethrowIfCancellation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * S4152: the paired phone as Tourist's weather source, over two Data Items.
 *
 * The answer is read from a listener registered only while Tourist collects it, plus the item already
 * stored, so opening the screen shows the last answer at once instead of waiting for the next request.
 * The build that withholds Tourist refuses to ask as well, so the store declaration does not depend on the
 * screen staying unreachable - the same guard as `PublishTouristHealthUseCase`.
 */
class DataLayerWearTouristWeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val capabilities: WearRestrictedCapabilities,
) : WearTouristWeatherRepository {

    private val envelopeCodec = WearEventEnvelopeCodec()

    private val answerUri: Uri = Uri.Builder()
        .scheme(PutDataRequest.WEAR_URI_SCHEME)
        .path(WearDataLayerPaths.TOURIST_WEATHER)
        .build()

    override fun observeWeather(): Flow<WearTouristWeatherPayload?> = callbackFlow {
        val client = Wearable.getDataClient(context)
        val listener = DataClient.OnDataChangedListener { events ->
            events.filter { it.type == DataEvent.TYPE_CHANGED }
                .mapNotNull { decodeAnswer(it.dataItem) }
                .lastOrNull()
                ?.let { trySend(it) }
        }
        client.addListener(listener, answerUri, DataClient.FILTER_LITERAL)
        trySend(readStoredAnswer(client))
        awaitClose { client.removeListener(listener) }
    }

    override suspend fun requestWeather(position: WearWeatherPosition) {
        if (!capabilities.offersHealthFeatures) return
        runCatching {
            val payload = WearTouristWeatherRequestPayload(
                latitude = position.latitude,
                longitude = position.longitude,
                requestedAtMs = System.currentTimeMillis(),
            )
            val envelope = WearEventEnvelope(
                eventType = WearDataLayerPaths.EVENT_TOURIST_WEATHER_REQUEST,
                sentAt = payload.requestedAtMs,
                data = gson.toJson(payload).toByteArray(),
            )
            val request = PutDataMapRequest.create(WearDataLayerPaths.TOURIST_WEATHER_REQUEST).apply {
                dataMap.putByteArray(PAYLOAD_KEY, envelopeCodec.encode(envelope))
                // The user is looking at the screen now; a batched delivery would answer after they left.
                setUrgent()
            }
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest()).await()
        }.onFailure { e ->
            e.rethrowIfCancellation()
            Timber.w(e, "Tourist weather request not sent to the phone")
        }
    }

    private suspend fun readStoredAnswer(client: DataClient): WearTouristWeatherPayload? = runCatching {
        val buffer = client.getDataItems(answerUri).await()
        try {
            buffer.mapNotNull { decodeAnswer(it) }.maxByOrNull { it.observedAtMs }
        } finally {
            buffer.release()
        }
    }.onFailure { e ->
        e.rethrowIfCancellation()
        Timber.w(e, "Stored Tourist weather answer not readable")
    }.getOrNull()

    private fun decodeAnswer(item: DataItem): WearTouristWeatherPayload? {
        val bytes = item.takeIf { it.uri.path == WearDataLayerPaths.TOURIST_WEATHER }
            ?.let { DataMapItem.fromDataItem(it).dataMap.getByteArray(PAYLOAD_KEY) }
            ?: return null
        return try {
            val envelope = envelopeCodec.decode(bytes)
            gson.fromJson(envelope.data.decodeToString(), WearTouristWeatherPayload::class.java)
        } catch (e: JsonParseException) {
            Timber.w(e, "Tourist weather answer not decodable - dropped")
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Tourist weather answer envelope malformed - dropped")
            null
        } catch (e: IllegalStateException) {
            Timber.w(e, "Tourist weather answer envelope is not an object - dropped")
            null
        }
    }

    private companion object {
        const val PAYLOAD_KEY = "payload"
    }
}
