package com.sza.fastmediasorter.wear.domain.repository

import com.google.android.gms.wearable.ChannelClient
import com.sza.fastmediasorter.wear.domain.model.WearFileReceiveResult
import com.sza.fastmediasorter.wear.domain.model.WearFileTransferMetadata

/**
 * S1861: owner of every file arriving on the watch over the Data Layer.
 *
 * The declaration and the bytes travel apart - a message announces the file, a channel carries it -
 * so the two halves are separate calls on one `@Singleton`: `WearableListenerService` is recreated
 * per event and cannot itself remember what the message said by the time the channel opens.
 */
interface WearFileReceiverRepository {

    /**
     * Records what the other side says it is about to send, so [receiveFile] can refuse an oversized
     * file before a single byte is written. Never trusted as a check - see [receiveFile].
     */
    fun declare(metadata: WearFileTransferMetadata)

    /**
     * Drains [channel] into a file named [fileName], and reports where it landed.
     *
     * A declaration already over the ceiling is refused without opening the stream. The bytes are
     * counted while they are written regardless, and a file that outgrows what was declared is
     * aborted and deleted: metadata written by the other side is a hint, never a check.
     *
     * S1884: the destination follows the announced intent. A file sent to be looked at once goes to a
     * pruned preview cache; a file sent to be kept goes to the watch's downloads directory as it
     * always has. The caller is told the path rather than deriving it, so the rule has one owner.
     */
    suspend fun receiveFile(channel: ChannelClient.Channel, fileName: String): WearFileReceiveResult
}
