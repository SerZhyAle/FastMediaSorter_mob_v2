package com.sza.fastmediasorter.wear.data.wear.helpers

import com.sza.fastmediasorter.wear.core.notification.WearOpenOnWatchNotifier
import com.sza.fastmediasorter.wear.data.wear.WatchFileOpenEvents
import com.sza.fastmediasorter.wear.data.wear.WatchStreamOpenEvents
import com.sza.fastmediasorter.wear.domain.model.WearFileOpenRequest
import com.sza.fastmediasorter.wear.domain.model.WearFileReceiveOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileReceiveResult
import com.sza.fastmediasorter.wear.domain.model.WearFileTransferAck
import com.sza.fastmediasorter.wear.domain.model.WearFileTransferMetadata
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferAck
import com.sza.fastmediasorter.wear.domain.model.streamTargetRef
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/** S1944: long enough for a composed collector, far below the phone's own 15 s ack timeout. */
private const val OPEN_CONFIRM_TIMEOUT_MS = 2_000L

/**
 * S2431: decides what to answer the phone about a transfer the watch has already received.
 *
 * The decisions used to sit in the body of the `WearableListenerService` that receives the events,
 * which made a platform callback the owner of domain logic; the listener now dispatches and this
 * class decides. The file path and the stream path reach the same wait through
 * [awaitOpenConfirmation] rather than through two copies of it - they differ only in which event
 * pair carries the request, what counts as the echo, and which outcome constant the answer uses.
 */
class WearTransferOutcomeCoordinator @Inject constructor(
    private val openOnWatchNotifier: WearOpenOnWatchNotifier,
) {

    /** S1884: the outcome string the phone is told about a file it sent. */
    suspend fun fileOutcome(
        result: WearFileReceiveResult,
        declaration: WearFileTransferMetadata,
    ): String {
        Timber.d("S2431: coordinator decides file outcome")
        return decideFileOutcome(result, declaration)
    }

    private suspend fun decideFileOutcome(
        result: WearFileReceiveResult,
        declaration: WearFileTransferMetadata,
    ): String = when {
        result.outcome == WearFileReceiveOutcome.REFUSED_TOO_LARGE -> WearFileTransferAck.OUTCOME_TOO_LARGE
        result.outcome != WearFileReceiveOutcome.SAVED -> WearFileTransferAck.OUTCOME_FAILED
        !declaration.openNow -> WearFileTransferAck.OUTCOME_SAVED
        else -> openFileOnWatch(result.savedPath, declaration.mimeType)
    }

    /**
     * S1944: asks whatever screen is on to open [channel], and answers with what actually happened.
     *
     * The service that receives the transfer may not raise the app from the background (strategic
     * ADR-1), so the honest answer when nobody is listening is NOT_FOREGROUND rather than a claim of
     * playback.
     */
    suspend fun streamAck(channel: WearStreamChannel, requestId: String): WearStreamTransferAck {
        Timber.d("S2431: coordinator decides stream outcome")
        val handled = awaitOpenConfirmation(
            echo = WatchStreamOpenEvents.openedFlow,
            expected = channel.url,
            emitRequest = { WatchStreamOpenEvents.requestFlow.emit(channel) },
            fallbackTarget = WearLaunchTarget.Open(streamTargetRef(channel.url)),
            fallbackSubtitle = channel.name,
        )
        val outcome = if (handled) {
            WearStreamTransferAck.OUTCOME_OPENED
        } else {
            WearStreamTransferAck.OUTCOME_NOT_FOREGROUND
        }
        return WearStreamTransferAck(requestId = requestId, outcome = outcome)
    }

    /**
     * S1884: asks whatever screen is on to show the arrived file, and answers with what happened.
     *
     * A file with no landed path or no type is not something any screen could be asked to show, so
     * that case answers UNSUPPORTED without ever emitting a request.
     */
    private suspend fun openFileOnWatch(savedPath: String?, mimeType: String?): String {
        if (savedPath == null || mimeType.isNullOrBlank()) {
            return WearFileTransferAck.OUTCOME_UNSUPPORTED
        }
        val request = WearFileOpenRequest(path = savedPath, mimeType = mimeType)
        val handled = awaitOpenConfirmation(
            echo = WatchFileOpenEvents.openedFlow,
            expected = savedPath,
            emitRequest = { WatchFileOpenEvents.requestFlow.emit(request) },
            fallbackTarget = WearLaunchTarget.File(savedPath, mimeType),
            fallbackSubtitle = savedPath.substringAfterLast('/'),
        )
        return if (handled) {
            WearFileTransferAck.OUTCOME_OPENED
        } else {
            WearFileTransferAck.OUTCOME_NOT_FOREGROUND
        }
    }

    /**
     * Emits an open request and reports whether a live screen echoed it back in time.
     *
     * The collector is started UNDISPATCHED so it is already waiting when the request goes out - a
     * screen that answers immediately would otherwise echo into nobody. S1961: the wait expiring IS
     * the scenario, the watch being dark, so the notification is raised here and its tap becomes the
     * shorter way in; the outcome the caller then sends is deliberately unchanged by it.
     */
    private suspend fun awaitOpenConfirmation(
        echo: SharedFlow<String>,
        expected: String,
        emitRequest: suspend () -> Unit,
        fallbackTarget: WearLaunchTarget,
        fallbackSubtitle: String,
    ): Boolean {
        val confirmed = withTimeoutOrNull(OPEN_CONFIRM_TIMEOUT_MS) {
            val confirmation = async(start = CoroutineStart.UNDISPATCHED) {
                echo.first { it == expected }
            }
            emitRequest()
            confirmation.await()
        }
        if (confirmed == null) {
            openOnWatchNotifier.notifyPendingOpen(target = fallbackTarget, subtitle = fallbackSubtitle)
        }
        return confirmed != null
    }
}
