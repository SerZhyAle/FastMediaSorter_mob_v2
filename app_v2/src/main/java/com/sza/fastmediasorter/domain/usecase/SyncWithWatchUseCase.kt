package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.model.WearSyncLeg
import com.sza.fastmediasorter.domain.model.WearSyncLegResult
import com.sza.fastmediasorter.domain.model.WearSyncOutcome
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import timber.log.Timber
import javax.inject.Inject

private const val REASON_NO_WATCH = "no watch connected"
private const val REASON_RESOURCES = "resources were not accepted"
private const val REASON_SETTINGS = "settings were not accepted"

/**
 * S2484: runs the phone's half of one unified exchange and reports each leg separately.
 *
 * ADR-1 puts the composition of the exchange here and nowhere else: five controls across three
 * screens in two modules each used to carry their own half of it, and copying the sequence into each
 * call site is the duplication this ticket exists to remove.
 *
 * The connected-node check is hoisted out of the two legs, which each performed it and each produced
 * the same refusal text. Strategic 6.3 fixes that refusal as immediate - no exchange is queued for a
 * watch that shows up later.
 */
class SyncWithWatchUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository,
    private val sendResourcesToWatch: SendResourcesToWatchUseCase,
    private val pushWearSettings: PushWearSettingsUseCase
) {

    /**
     * Never throws. A failed leg is a value, so one unreachable half cannot abort the other and the
     * caller always receives an outcome it can show.
     */
    suspend operator fun invoke(settings: WearSettingsPayload): WearSyncOutcome {
        Timber.d("S2484: starting unified sync with watch")
        val nodes = runCatching { wearableRepository.getConnectedNodes() }.getOrDefault(emptyList())
        if (nodes.isEmpty()) {
            Timber.w("Unified sync refused - no watch connected")
            return WearSyncOutcome.allFailed(REASON_NO_WATCH)
        }
        return WearSyncOutcome(
            mapOf(
                WearSyncLeg.RESOURCES_OUT to sendResourcesLeg(),
                WearSyncLeg.SETTINGS_OUT to pushSettingsLeg(settings)
            )
        )
    }

    private suspend fun sendResourcesLeg(): WearSyncLegResult = sendResourcesToWatch().fold(
        onSuccess = { result ->
            if (result.sent == 0) {
                WearSyncLegResult.NothingToSend
            } else {
                WearSyncLegResult.Succeeded(result.sent)
            }
        },
        onFailure = { error ->
            Timber.e(error, "Unified sync: the resources leg failed")
            WearSyncLegResult.Failed(error.message ?: REASON_RESOURCES)
        }
    )

    private suspend fun pushSettingsLeg(settings: WearSettingsPayload): WearSyncLegResult =
        pushWearSettings(settings).fold(
            onSuccess = { WearSyncLegResult.Succeeded(1) },
            onFailure = { error ->
                Timber.e(error, "Unified sync: the settings leg failed")
                WearSyncLegResult.Failed(error.message ?: REASON_SETTINGS)
            }
        )
}
