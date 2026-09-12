package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.util.StreamChannelIdentity
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearStreamPinsPayload
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.domain.usecase.streams.ObservePinnedStreamSourcesUseCase
import com.sza.fastmediasorter.service.WearDataLayerPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S2149: publishes the phone's pinned stream channels to the watch, so the watch list can raise them.
 *
 * Shaped after [PushWearSettingsUseCase] - same envelope, same data-item transport. The set is always
 * sent whole, including when it is empty: a delta or a skipped empty push would leave the last pin
 * stuck on the watch with no way to withdraw it.
 */
class PushWearStreamPinsUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository,
    private val gson: Gson,
    private val observePinnedSources: ObservePinnedStreamSourcesUseCase,
    private val settingsRepository: SettingsRepository
) {

    suspend operator fun invoke(): Result<Unit> = push(observePinnedSources().first())

    /**
     * Collects the pinned set and republishes on every change, so pin, unpin and reorder all reach the
     * watch without each calling screen having to know a watch exists. Gated on [SettingsRepository]
     * enableWearCompanion.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAndPush(scope: CoroutineScope): Job = scope.launch {
        settingsRepository.getSettings()
            .map { it.enableWearCompanion }
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (enabled) {
                    observePinnedSources()
                } else {
                    emptyFlow()
                }
            }
            // The collector outlives the process's foreground, so nothing it touches may escape: an
            // unhandled throw here reaches the default uncaught-exception handler, which is a
            // process-wide surface this feature has no business taking down.
            .catch { Timber.w(it, "Wear stream pins: pinned-source stream failed") }
            .collectLatest { sources ->
                push(sources).onFailure { Timber.d("Wear stream pins not pushed: ${it.message}") }
            }
    }

    private suspend fun push(sources: List<StreamSourceEntity>): Result<Unit> = runCatching {
        val nodes = wearableRepository.getConnectedNodes()
        check(nodes.isNotEmpty()) { "No watch connected" }
        val identities = sources.map { StreamChannelIdentity.ofSource(it) }
        val payloadBytes = gson.toJson(WearStreamPinsPayload(identities)).toByteArray(Charsets.UTF_8)
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_STREAM_PINS,
            sentAt = System.currentTimeMillis(),
            data = payloadBytes
        )
        wearableRepository.putEnvelopeDataItem(WearDataLayerPaths.STREAM_PINS, envelope)
    }
}
