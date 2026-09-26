package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearFaceSlotsPayload
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.WearFaceSlotsRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S3558: publishes what each watch face button is set to, on every change.
 *
 * Shaped after [PushWearClockStyleUseCase] and for its reason does not wait for a connected watch: the
 * assignment is state carried by a Data Item, which the Data Layer delivers on reconnect, so a choice
 * made while the watch is away must still be put rather than dropped.
 */
class PushWearFaceSlotsUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository,
    private val gson: Gson,
    private val faceSlotsRepository: WearFaceSlotsRepository,
    private val settingsRepository: SettingsRepository,
) {

    /** Gated on [SettingsRepository] enableWearCompanion; re-enabling it republishes the assignment. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAndPush(scope: CoroutineScope): Job = scope.launch {
        settingsRepository.getSettings()
            .map { it.enableWearCompanion }
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (enabled) {
                    observeSlots()
                } else {
                    emptyFlow()
                }
            }
            // The collector lives for the whole process; a throw escaping it would reach the default
            // uncaught-exception handler, a process-wide surface this feature must not take down.
            .catch { Timber.w(it, "Wear face slots: assignment stream failed") }
            .collectLatest { slots ->
                push(slots).onFailure { Timber.w(it, "Wear face slots not pushed") }
            }
    }

    // Debounced so a quick run of picks across the four rows costs one Data Layer sync, not four.
    @OptIn(FlowPreview::class)
    private fun observeSlots(): Flow<WearFaceSlotsPayload> = faceSlotsRepository.observe()
        .map { WearFaceSlotsPayload.from(it) }
        .debounce(DEBOUNCE_MS)
        .distinctUntilChanged()

    private suspend fun push(slots: WearFaceSlotsPayload): Result<Unit> = runCatching {
        val sentAt = System.currentTimeMillis()
        val payloadBytes = gson.toJson(slots.copy(sentAt = sentAt)).toByteArray(Charsets.UTF_8)
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_FACE_SLOTS,
            sentAt = sentAt,
            data = payloadBytes,
        )
        Timber.d("S3558: face slots published to watch")
        wearableRepository.putEnvelopeDataItem(WearDataLayerPaths.FACE_SLOTS, envelope)
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
