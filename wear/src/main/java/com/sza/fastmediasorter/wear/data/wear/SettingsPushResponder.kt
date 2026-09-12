package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.Gson
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearSettingsDivergence
import com.sza.fastmediasorter.wear.domain.model.WearSettingsFieldIssue
import com.sza.fastmediasorter.wear.domain.model.WearSettingsPayloadDecoder
import com.sza.fastmediasorter.wear.domain.usecase.ApplyWearSettingsUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ReportWearSettingsUseCase
import com.sza.fastmediasorter.wear.util.errorUnlessCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2461: applies a settings push from the phone and answers it with the watch's own report, on a scope
 * the application owns rather than the one of the listener service that received it.
 *
 * **The scope is the whole point of the class**, as it is for [ListenAckSender]. The platform destroys
 * [WatchWearListenerService] as soon as nothing is bound to it, shortly after the callback returns, and
 * until S2915 the service cancelled its own scope in onDestroy, taking the work in flight with it. A
 * cancelled job is not an error, so the work in flight
 * vanished without a report, an error or a single log line - and the phone was left showing the
 * previous sync time next to a watch version that no longer existed. It surfaced on 2026-09-11, once
 * S2862 gave the push its per-field stamps: every applied field then costs a second DataStore write,
 * and the exchange that had answered in 0.6 s on 2026-09-10 decoded on the watch and never answered.
 */
@Singleton
class SettingsPushResponder @Inject constructor(
    gson: Gson,
    private val applyWearSettingsUseCase: ApplyWearSettingsUseCase,
    private val reportWearSettingsUseCase: ReportWearSettingsUseCase
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val envelopeCodec = WearEventEnvelopeCodec()

    private val settingsPayloadDecoder = WearSettingsPayloadDecoder(gson)

    // The scope now outlives any one service instance, so two pushes in quick succession could apply
    // side by side, each merging against stamps read before the other wrote. One exchange at a time
    // keeps every read-apply-report sequence whole.
    private val exchangeLock = Mutex()

    /**
     * @param receivedAtEpochMillis when the watch took delivery, read by the caller before handing over,
     *   because the merge measures the clock skew from it against the envelope's own sentAt.
     */
    fun respond(payloadBytes: ByteArray, receivedAtEpochMillis: Long) {
        scope.launch {
            exchangeLock.withLock {
                runCatching { applyAndReport(payloadBytes, receivedAtEpochMillis) }
                    .onFailure { e ->
                        e.errorUnlessCancellation("Failed to apply settings push")
                        WatchSyncEvents.settingsErrorFlow.emit(e.message ?: "Settings apply failed")
                    }
            }
        }
    }

    private suspend fun applyAndReport(payloadBytes: ByteArray, receivedAtEpochMillis: Long) {
        val envelope = envelopeCodec.decode(payloadBytes)
        // S2462: decoded key by key rather than straight into the payload class. A phone on a different
        // build may omit a key or send it as another type, and a single typed fromJson answers both with
        // an exception that drops the event - one incompatible field silencing the whole exchange.
        val decoded = settingsPayloadDecoder.decode(envelope.data.decodeToString())
        Timber.d("S2462: push decoded p=%d d=%s", decoded.presentFields.size, decoded.divergences)
        logSettingsDivergences(decoded.divergences)
        val payload = decoded.payload
        if (payload == null) {
            Timber.w("Settings push carried nothing decodable")
            return
        }
        applyWearSettingsUseCase(payload, envelope.sentAt, receivedAtEpochMillis, decoded.presentFields)
        // S2093: a push is answered with what the watch ended up holding, so one press on the phone
        // completes the exchange in both directions rather than only sending.
        reportWearSettingsUseCase()
    }

    /**
     * S2462: says which keys did not survive the decode, split by what the reason implies.
     *
     * A wrong type is a defect in the contract and is worth a warning; a key the peer never sent or a
     * key this build does not know are the normal shape of two devices on different versions, so they
     * stay at debug rather than crying wolf on every exchange with an older phone.
     */
    private fun logSettingsDivergences(divergences: List<WearSettingsDivergence>) {
        if (divergences.isEmpty()) return
        val mistyped = divergences.filter { it.issue == WearSettingsFieldIssue.WRONG_TYPE }
        if (mistyped.isNotEmpty()) {
            Timber.w("Settings push: %d field(s) of unexpected type - %s", mistyped.size, mistyped)
        }
        val skewed = divergences - mistyped.toSet()
        if (skewed.isNotEmpty()) {
            Timber.d("Settings push: %d field(s) not exchanged - %s", skewed.size, skewed)
        }
    }
}
