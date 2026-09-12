package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearSyncLeg
import com.sza.fastmediasorter.wear.domain.model.WearSyncLegResult
import com.sza.fastmediasorter.wear.domain.model.WearSyncOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

private const val REASON_NO_PHONE = "no phone connected"
private const val REASON_REQUEST = "the phone was not asked for its resources"
private const val REASON_EXPORT = "the watch resources were not sent"
private const val REASON_SETTINGS = "the watch settings were not sent"

/**
 * S2484: runs the watch's half of one unified exchange and reports each leg separately.
 *
 * ADR-1 puts the composition here rather than in a screen because the watch carries the same control
 * twice - in the resources section and in the settings section - and a sequence copied into both
 * would drift the moment one of them changed.
 *
 * Strategic 6.3 fixes the behaviour with no phone in range as an immediate refusal: nothing is queued
 * for a phone that might come back, because no such deferral mechanism exists on either side.
 */
class SyncWithPhoneUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportSources: ExportSourcesUseCase,
    private val reportWearSettings: ReportWearSettingsUseCase
) {

    /** Never throws - a failed leg is a value, so one refusal cannot abort the rest of the exchange. */
    suspend operator fun invoke(): WearSyncOutcome {
        Timber.d("S2484: starting unified sync with phone")
        val nodes = runCatching {
            Wearable.getNodeClient(context).connectedNodes.await()
        }.getOrDefault(emptyList())
        if (nodes.isEmpty()) {
            Timber.w("Unified sync refused - no phone connected")
            return WearSyncOutcome.allFailed(REASON_NO_PHONE)
        }
        val nodeId = nodes.first().id
        return WearSyncOutcome(
            mapOf(
                WearSyncLeg.RESOURCES_IN to requestResourcesLeg(nodeId),
                WearSyncLeg.RESOURCES_OUT to exportSourcesLeg(),
                WearSyncLeg.SETTINGS_OUT to reportSettingsLeg()
            )
        )
    }

    /**
     * Only the asking is confirmed here. The resources themselves arrive later as a Data Item, and
     * whether they were applied is reported by the coordinator that listens for the import result.
     */
    private suspend fun requestResourcesLeg(nodeId: String): WearSyncLegResult = runCatching {
        Wearable.getMessageClient(context)
            .sendMessage(nodeId, WearDataLayerPaths.NETWORK_SOURCES_REQUEST, ByteArray(0))
            .await()
    }.fold(
        onSuccess = { WearSyncLegResult.Succeeded(0) },
        onFailure = { error ->
            Timber.e(error, "Unified sync: the phone could not be asked for its resources")
            WearSyncLegResult.Failed(error.message ?: REASON_REQUEST)
        }
    )

    private suspend fun exportSourcesLeg(): WearSyncLegResult = exportSources().fold(
        onSuccess = { count ->
            if (count == 0) {
                WearSyncLegResult.NothingToSend
            } else {
                WearSyncLegResult.Succeeded(count)
            }
        },
        onFailure = { error ->
            Timber.e(error, "Unified sync: the outbound resources leg failed")
            WearSyncLegResult.Failed(error.message ?: REASON_EXPORT)
        }
    )

    private suspend fun reportSettingsLeg(): WearSyncLegResult = reportWearSettings().fold(
        onSuccess = { WearSyncLegResult.Succeeded(1) },
        onFailure = { error ->
            Timber.e(error, "Unified sync: the settings leg failed")
            WearSyncLegResult.Failed(error.message ?: REASON_SETTINGS)
        }
    )
}
