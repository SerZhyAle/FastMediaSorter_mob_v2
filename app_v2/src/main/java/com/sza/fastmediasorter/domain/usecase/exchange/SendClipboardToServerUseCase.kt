package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeNotEnrolledException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeRefusedException
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardPutResult
import com.sza.fastmediasorter.data.repository.ExchangeClipboardRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.domain.model.ExchangeClipboardEntryFailure
import com.sza.fastmediasorter.domain.model.ExchangeClipboardEntryFailure.Cause
import com.sza.fastmediasorter.domain.model.ExchangeClipboardMeta
import com.sza.fastmediasorter.domain.model.ExchangeClipboardSendOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * "Send the clipboard to the server" (contract DEVICE-EXCHANGE 22 item CA): one value to every held entry that
 * takes part, each on its own stream and its own read of the source, all at once. One `clipboard-stored` is a
 * success; every other entry is named with what happened to it.
 */
class SendClipboardToServerUseCase @Inject constructor(
    private val clipboard: ExchangeClipboardRepository,
) {

    suspend fun hasEntry(): Boolean = clipboard.candidates().isNotEmpty()

    /**
     * [openSource] is called once per entry, so each stream reads the value from its start. [onProgress] gets the
     * largest byte count any send reached, from several threads.
     */
    suspend operator fun invoke(
        meta: ExchangeClipboardMeta,
        openSource: () -> InputStream,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardSendOutcome {
        val entries = clipboard.candidates()
        if (entries.isEmpty()) return ExchangeClipboardSendOutcome.NoEntry
        val furthest = AtomicLong()
        val failures = coroutineScope {
            entries.map { entry ->
                async {
                    sendTo(entry, meta, openSource) { done ->
                        val reached = furthest.accumulateAndGet(done) { held, next -> maxOf(held, next) }
                        if (reached == done) onProgress(done)
                    }
                }
            }.awaitAll()
        }
        val stored = failures.count { it == null }
        Timber.i("Exchange clipboard send: stored on %d of %d entries", stored, entries.size)
        return if (stored > 0) {
            ExchangeClipboardSendOutcome.Sent(stored, failures.filterNotNull())
        } else {
            ExchangeClipboardSendOutcome.NotSent(failures.filterNotNull())
        }
    }

    // Null when the entry answered `clipboard-stored`.
    private suspend fun sendTo(
        entry: DeviceExchangeAccountEntry,
        meta: ExchangeClipboardMeta,
        openSource: () -> InputStream,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardEntryFailure? {
        var flowed = false
        val cause = try {
            val result = clipboard.put(entry, meta, openSource) { done ->
                flowed = true
                onProgress(done)
            }
            causeOf(result)
        } catch (e: DeviceExchangeRefusedException) {
            Cause.Refused(e.reason)
        } catch (e: DeviceExchangeNotEnrolledException) {
            Timber.w(e, "Exchange clipboard send: the entry holds no token")
            Cause.Refused(REASON_NOT_ENROLLED)
        } catch (e: IOException) {
            Timber.w(e, "Exchange clipboard send failed on one entry")
            if (flowed) Cause.Broken else Cause.Unreachable
        }
        return cause?.let { ExchangeClipboardEntryFailure(ExchangeClipboardEntryFailure.labelOf(entry), it) }
    }

    private fun causeOf(result: ExchangeClipboardPutResult): Cause? = when (result) {
        is ExchangeClipboardPutResult.Stored -> null
        is ExchangeClipboardPutResult.Refused -> Cause.Refused(result.reason)
        ExchangeClipboardPutResult.NotOffered -> Cause.NotOffered
        ExchangeClipboardPutResult.Guest -> Cause.Guest
        ExchangeClipboardPutResult.NotSealed -> Cause.Sealing(ExchangeMessage.SERVER_NO_E2E)
        ExchangeClipboardPutResult.NoRecipient -> Cause.Sealing(ExchangeMessage.E2E_MISSING)
    }

    private companion object {
        const val REASON_NOT_ENROLLED = "not-enrolled"
    }
}
