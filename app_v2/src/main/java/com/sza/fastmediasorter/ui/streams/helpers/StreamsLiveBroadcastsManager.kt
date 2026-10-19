package com.sza.fastmediasorter.ui.streams.helpers

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorParser
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.ExchangeDeviceBroadcast
import com.sza.fastmediasorter.domain.usecase.exchange.ObserveAccountBroadcastsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import timber.log.Timber

/**
 * S4392: Prepares live broadcasts of the exchange account as synthetic [StreamSourceEntity] instances
 * for the "Own" category filter in the streams catalog.
 */
class StreamsLiveBroadcastsManager @Inject constructor(
    private val observeAccountBroadcasts: ObserveAccountBroadcastsUseCase,
    private val descriptorParser: BroadcastDescriptorParser,
) {

    private val refreshTrigger = MutableStateFlow(0L)

    /**
     * S4392: Triggers an immediate re-query of the account's live broadcasts.
     */
    fun refresh() {
        Timber.d("S4392: live broadcasts refreshed from exchange directory")
        refreshTrigger.value = System.currentTimeMillis()
    }

    /**
     * Emits the current list of live broadcast sources for this account.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLiveSources(): Flow<List<StreamSourceEntity>> =
        refreshTrigger.flatMapLatest {
            observeAccountBroadcasts().map { broadcastGroup ->
                broadcastGroup.items.mapNotNull(::toSyntheticSource)
            }
        }

    private fun toSyntheticSource(item: ExchangeDeviceBroadcast): StreamSourceEntity? {
        val dto = descriptorParser.parse(item.descriptorJson)
        val primaryUrl = dto?.url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val endpoints = BroadcastEndpointCodec.encode(dto.endpoints)
        val mediaKind = when (item.mode.trim().uppercase()) {
            "AUDIO_ONLY" -> "AUDIO"
            "VIDEO_AUDIO", "VIDEO_ONLY" -> if (primaryUrl.startsWith("rtsp://", ignoreCase = true)) "RTSP" else "VIDEO"
            else -> "VIDEO"
        }
        val displayTitle = item.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: item.deviceName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Broadcast"

        return StreamSourceEntity(
            id = ID_PREFIX + item.broadcastId,
            url = primaryUrl,
            title = displayTitle,
            mediaKind = mediaKind,
            sourceOrigin = SOURCE_ORIGIN_LIVE_BROADCAST,
            sortIndex = Int.MIN_VALUE,
            pinned = false,
            addedAt = System.currentTimeMillis(),
            sourceDeviceId = dto.sourceId,
            broadcastEndpoints = endpoints,
        )
    }

    companion object {
        const val SOURCE_ORIGIN_LIVE_BROADCAST = "LIVE_BROADCAST"
        const val ID_PREFIX = "live:"

        fun isLiveBroadcast(source: StreamSourceEntity): Boolean =
            source.sourceOrigin == SOURCE_ORIGIN_LIVE_BROADCAST
    }
}
