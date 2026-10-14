package com.sza.fastmediasorter.wear.domain.repository

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardOffer
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardReceiveReport
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSendReport
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSink
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue
import kotlinx.coroutines.flow.Flow

/**
 * S4227: the clipboard on the exchange server, seen from the watch - every entry the watch belongs to, its own
 * Wi-Fi or LTE, and the two actions of contract DEVICE-EXCHANGE 22 item CA.
 */
interface WearServerClipboardRepository {

    /** True while at least one stored entry may take part (member account, server lists the clipboard). */
    val hasEligibleEntry: Flow<Boolean>

    /**
     * The value "get" would fetch now, for the line beside its action; null when every clipboard is empty or no
     * entry answered. Asking also refreshes what each entry's server declares.
     */
    suspend fun latestState(): WearServerClipboardValue?

    /** Sends the bytes behind [source] to every eligible entry; [onProgress] gets bytes sent of the current one. */
    suspend fun send(
        offer: WearServerClipboardOffer,
        source: Uri,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): WearServerClipboardSendReport

    /**
     * Fetches the latest value across entries. [destination] is asked for a place once the value is known and
     * before a byte flows; null declines it. The sink is committed only for a value whose size and SHA-256
     * matched, and discarded on every other path, cancellation included.
     */
    suspend fun receive(
        destination: suspend (WearServerClipboardValue) -> WearServerClipboardSink?,
        onProgress: (received: Long, total: Long) -> Unit,
    ): WearServerClipboardReceiveReport
}
