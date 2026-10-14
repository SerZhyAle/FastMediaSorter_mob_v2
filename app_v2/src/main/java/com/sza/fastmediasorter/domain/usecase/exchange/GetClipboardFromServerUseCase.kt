package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.core.clipboard.ExchangeClipboardFileNames
import com.sza.fastmediasorter.core.clipboard.ExchangeClipboardFiles
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeNotEnrolledException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeRefusedException
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardGetResult
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardState
import com.sza.fastmediasorter.data.repository.ExchangeClipboardRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeClipboardEntryFailure
import com.sza.fastmediasorter.domain.model.ExchangeClipboardEntryFailure.Cause
import com.sza.fastmediasorter.domain.model.ExchangeClipboardGetOutcome
import com.sza.fastmediasorter.domain.model.ExchangeClipboardValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

/**
 * "Get the clipboard from the server" (contract DEVICE-EXCHANGE 22 items BY and CA): ask every held entry that
 * takes part for its clipboard at once, take the value stored last - the entry listed first on a tie - and place
 * nothing until it arrived whole. An entry that stays silent is skipped and named; the person is asked nothing.
 */
class GetClipboardFromServerUseCase @Inject constructor(
    private val clipboard: ExchangeClipboardRepository,
    private val files: ExchangeClipboardFiles,
) {

    suspend fun hasEntry(): Boolean = clipboard.candidates().isNotEmpty()

    suspend operator fun invoke(onProgress: (Long) -> Unit): ExchangeClipboardGetOutcome {
        val entries = clipboard.candidates()
        if (entries.isEmpty()) return ExchangeClipboardGetOutcome.NoEntry
        val asked = coroutineScope { entries.map { entry -> async { entry to ask(entry) } }.awaitAll() }
        val failures = asked.mapNotNull { (entry, answer) ->
            (answer as? Asked.Failed)?.let { failure(entry, it.cause) }
        }
        val chosen = asked.mapNotNull { (entry, answer) -> (answer as? Asked.Holds)?.let { entry to it.value } }
            // maxWithOrNull keeps the first of equal elements, which is the entry listed first (22 item CA).
            .maxWithOrNull(compareBy { (_, value) -> value.storedAtInstant() ?: Instant.MIN })
        return when {
            chosen == null && asked.any { it.second == Asked.Empty } -> ExchangeClipboardGetOutcome.Empty(failures)
            chosen == null -> ExchangeClipboardGetOutcome.NotReceived(failures)
            !files.hasRoomFor(chosen.second.size) -> ExchangeClipboardGetOutcome.NoRoom(chosen.second.size)
            else -> fetch(chosen.first, chosen.second, failures, onProgress)
        }
    }

    private suspend fun ask(entry: DeviceExchangeAccountEntry): Asked = guarded {
        val state = withTimeoutOrNull(STATE_TIMEOUT_MS) { clipboard.state(entry) }
        when (state) {
            null -> Asked.Failed(Cause.Unreachable)
            is ExchangeClipboardState.Value -> Asked.Holds(state.value)
            ExchangeClipboardState.Empty -> Asked.Empty
            is ExchangeClipboardState.Refused -> Asked.Failed(Cause.Refused(state.reason))
            ExchangeClipboardState.NotOffered -> Asked.Failed(Cause.NotOffered)
            ExchangeClipboardState.Guest -> Asked.Failed(Cause.Guest)
        }
    }

    private suspend fun fetch(
        entry: DeviceExchangeAccountEntry,
        value: ExchangeClipboardValue,
        failures: List<ExchangeClipboardEntryFailure>,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardGetOutcome {
        files.clearIncoming()
        val target = files.newIncomingFile()
        val result = guardedGet(entry, value.clipId, target, onProgress).let { first ->
            // 22 item BY: the value was replaced between the state and the get; ask once more for the new one.
            if (first.isReplaced()) refetch(entry, target, onProgress) ?: first else first
        }
        return when (result) {
            is Fetched.Got -> received(result.value, target, failures)
            Fetched.Empty -> ExchangeClipboardGetOutcome.Empty(failures)
            Fetched.Damaged -> ExchangeClipboardGetOutcome.Damaged
            is Fetched.Failed -> ExchangeClipboardGetOutcome.NotReceived(failures + failure(entry, result.cause))
        }
    }

    private suspend fun refetch(entry: DeviceExchangeAccountEntry, target: File, onProgress: (Long) -> Unit): Fetched? {
        val fresh = (ask(entry) as? Asked.Holds)?.value ?: return null
        return guardedGet(entry, fresh.clipId, target, onProgress)
    }

    private fun received(
        value: ExchangeClipboardValue,
        incoming: File,
        failures: List<ExchangeClipboardEntryFailure>,
    ): ExchangeClipboardGetOutcome {
        val name = ExchangeClipboardFileNames.safeName(value.name, value.kind, value.mimeType, value.storedAt)
        return ExchangeClipboardGetOutcome.Received(value, files.promote(incoming, name), failures)
    }

    private suspend fun guardedGet(
        entry: DeviceExchangeAccountEntry,
        clipId: String,
        target: File,
        onProgress: (Long) -> Unit,
    ): Fetched {
        var flowed = false
        val progress: (Long) -> Unit = { done ->
            flowed = true
            onProgress(done)
        }
        val outcome = guardedFetch({ if (flowed) Cause.Broken else Cause.Unreachable }) {
            when (val result = clipboard.get(entry, clipId, target, progress)) {
                is ExchangeClipboardGetResult.Received -> Fetched.Got(result.value)
                ExchangeClipboardGetResult.Empty -> Fetched.Empty
                ExchangeClipboardGetResult.Damaged -> Fetched.Damaged
                is ExchangeClipboardGetResult.Refused -> Fetched.Failed(Cause.Refused(result.reason))
                ExchangeClipboardGetResult.NotOffered -> Fetched.Failed(Cause.NotOffered)
                ExchangeClipboardGetResult.Guest -> Fetched.Failed(Cause.Guest)
            }
        }
        return outcome
    }

    private fun Fetched.isReplaced(): Boolean =
        this is Fetched.Failed && cause == Cause.Refused(DeviceExchangeEnvelope.REASON_UNAVAILABLE)

    private suspend fun guarded(call: suspend () -> Asked): Asked = try {
        call()
    } catch (e: DeviceExchangeRefusedException) {
        Asked.Failed(Cause.Refused(e.reason))
    } catch (e: DeviceExchangeNotEnrolledException) {
        Timber.w(e, "Exchange clipboard get: the entry holds no token")
        Asked.Failed(Cause.Refused(REASON_NOT_ENROLLED))
    } catch (e: IOException) {
        Timber.w(e, "Exchange clipboard state failed on one entry")
        Asked.Failed(Cause.Unreachable)
    }

    private suspend fun guardedFetch(onBroken: () -> Cause, call: suspend () -> Fetched): Fetched = try {
        call()
    } catch (e: DeviceExchangeRefusedException) {
        Fetched.Failed(Cause.Refused(e.reason))
    } catch (e: IOException) {
        Timber.w(e, "Exchange clipboard get failed")
        Fetched.Failed(onBroken())
    }

    private fun failure(entry: DeviceExchangeAccountEntry, cause: Cause) =
        ExchangeClipboardEntryFailure(ExchangeClipboardEntryFailure.labelOf(entry), cause)

    private sealed interface Asked {
        data class Holds(val value: ExchangeClipboardValue) : Asked
        data object Empty : Asked
        data class Failed(val cause: Cause) : Asked
    }

    private sealed interface Fetched {
        data class Got(val value: ExchangeClipboardValue) : Fetched
        data object Empty : Fetched
        data object Damaged : Fetched
        data class Failed(val cause: Cause) : Fetched
    }

    companion object {
        /** How long one entry may stay silent before the action goes on without it (22 item CA). */
        const val STATE_TIMEOUT_MS = 15_000L
        private const val REASON_NOT_ENROLLED = "not-enrolled"
    }
}
