package com.sza.fastmediasorter.data.wear

import com.sza.fastmediasorter.domain.model.WearScheduledMessageReceipt
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4248: hands the watch's receipt for a scheduled message to the run that is waiting for it.
 *
 * The receipt arrives on the Data Layer listener service while the schedule worker is still inside
 * its run, in the same process; this is the rendezvous between the two.
 */
@Singleton
class ScheduledWatchReceiptRegistry @Inject constructor() {

    private val pending = ConcurrentHashMap<String, CompletableDeferred<WearScheduledMessageReceipt>>()

    fun expect(messageId: String): CompletableDeferred<WearScheduledMessageReceipt> =
        CompletableDeferred<WearScheduledMessageReceipt>().also { pending[messageId] = it }

    fun complete(receipt: WearScheduledMessageReceipt) {
        val waiting = pending.remove(receipt.messageId)
        if (waiting == null) {
            // The run already gave up on it; the next slot sends a fresh message.
            Timber.i("Scheduled watch receipt for %s arrived after its run ended", receipt.messageId)
            return
        }
        waiting.complete(receipt)
    }

    fun forget(messageId: String) {
        pending.remove(messageId)
    }
}
