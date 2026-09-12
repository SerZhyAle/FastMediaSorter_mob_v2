package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.google.gson.Gson
import com.sza.fastmediasorter.core.share.ShareTarget
import com.sza.fastmediasorter.core.share.ShareTargetAvailability
import com.sza.fastmediasorter.core.share.ShareTargetAvailabilityResolver
import com.sza.fastmediasorter.core.share.ShareTargetIconResolver
import com.sza.fastmediasorter.core.share.ShareTargetRegistry
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearSendToReceiverEntry
import com.sza.fastmediasorter.domain.model.WearSendToReceiversPayload
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S2142: publishes the phone's «Send to..» receiver list to the watch, so the watch offers the same
 * receivers the owner switched on here rather than keeping a second list of its own.
 *
 * Shaped after [PushWearStreamPinsUseCase] - same envelope, same data-item transport, and the same
 * rule that the set is always sent whole, including when it is empty: a delta or a skipped empty
 * push would leave a receiver the owner has just switched off still offered on the watch.
 *
 * Branch К (strategic §6 question 4): what crosses the channel is data, not code. Labels are
 * resolved here and travel as text, because the `R` class holding them does not exist on the watch.
 */
class PushWearSendToReceiversUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val wearableRepository: WearableDataLayerRepository,
    private val gson: Gson,
    private val registry: ShareTargetRegistry,
    private val availabilityResolver: ShareTargetAvailabilityResolver,
    private val iconResolver: ShareTargetIconResolver,
    private val isEnabled: IsShareTargetEnabledUseCase,
    private val settingsRepository: SettingsRepository
) {

    suspend operator fun invoke(): Result<Unit> = push(settingsRepository.getSettings().first())

    /**
     * Republishes on every settings change, so switching a receiver on or off reaches the watch
     * without the settings screen having to know a watch exists. Gated on enableWearCompanion.
     */
    fun observeAndPush(scope: CoroutineScope): Job = scope.launch {
        settingsRepository.getSettings()
            // Narrowed to the three fields the published list is actually derived from BEFORE
            // deduplicating, the way the pattern narrows to enableWearCompanion before its own
            // distinctUntilChanged. AppSettings carries the whole app's settings and getSettings()
            // adds no deduplication of its own, so comparing whole objects lets an unrelated write -
            // the last opened resource, a grid mode - through as a change: each one would cost a
            // getConnectedNodes RPC, fourteen availability probes, and a byte-identical file written
            // to the watch's flash, once per opened folder.
            .distinctUntilChangedBy {
                Triple(it.enableWearCompanion, it.enabledShareTargets, it.disabledShareTargets)
            }
            // The collector outlives the process's foreground, so nothing it touches may escape: an
            // unhandled throw here reaches the default uncaught-exception handler, which is a
            // process-wide surface this feature has no business taking down.
            .catch { Timber.w(it, "Wear send-to receivers: settings stream failed") }
            .collectLatest { settings ->
                if (settings.enableWearCompanion) {
                    push(settings).onFailure { Timber.d("Send-to receivers not pushed: ${it.message}") }
                }
            }
    }

    private suspend fun push(settings: AppSettings): Result<Unit> = runCatching {
        val nodes = wearableRepository.getConnectedNodes()
        check(nodes.isNotEmpty()) { "No watch connected" }
        val payload = WearSendToReceiversPayload(receivers = offeredReceivers(settings))
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_SEND_TO_RECEIVERS,
            sentAt = System.currentTimeMillis(),
            data = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        )
        wearableRepository.putEnvelopeDataItem(WearDataLayerPaths.SEND_TO_RECEIVERS, envelope)
    }

    /**
     * The same two gates [BuildSendToReceiverListUseCase] builds the phone's own menu with, reused
     * rather than restated so the two lists cannot drift apart silently. The content-type gate is
     * deliberately not applied: it depends on the file open on the watch, so the watch applies it.
     */
    private fun offeredReceivers(settings: AppSettings): List<WearSendToReceiverEntry> =
        registry.all()
            // Excluded explicitly: this receiver IS the opposite direction - the phone sending a file
            // to the paired watch - so offering it on the watch would mean sending a file to itself.
            .filter { it.availability != ShareTargetAvailability.REQUIRES_WATCH }
            .filter { isEnabled(it.id, settings) && availabilityResolver.isAvailable(it, settings) }
            .map { it.toEntry() }

    /**
     * The label prefers the installed app's own name: six receivers share the neutral "application"
     * title, because brand literals are denied by `verifyNoPlatformNames`, and a bare `getString`
     * would hand the watch six rows spelled identically and indistinguishable by touch.
     */
    private fun ShareTarget.toEntry(): WearSendToReceiverEntry = WearSendToReceiverEntry(
        id = id,
        title = (iconResolver.resolveLabel(this) ?: context.getString(titleRes)).toString(),
        subtitle = subtitleRes?.let { context.getString(it) },
        iconName = wearIconName,
        servedOnWatch = servedOnWatch,
        applicableTypes = applicableTypes.map { it.name },
        batchCapable = batchCapable,
        textCapable = textCapable,
        requiresLocalFile = requiresLocalFile
    )
}
