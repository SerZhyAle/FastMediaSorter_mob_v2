package com.sza.fastmediasorter.wear.domain.repository

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.WearFileSendOutcome
import java.io.File

/**
 * S1861 / S2161: the watch half of the symmetric transfer - the watch as initiator, not only as responder.
 *
 * The phone-side twin is a queue with a progress snapshot; this one is a single suspending call
 * because a watch sends one small recording at a time and has no screen to draw a queue on. What is
 * settled here is that either side may open the channel; the screens that call it arrive with the
 * recorder (S1862) and the watch-side file operations (S1863). S2161 adds sending from a content [Uri].
 */
data class WearFileSendResult(
    val outcome: WearFileSendOutcome,
    val destination: String? = null
)

interface WearFileSenderRepository {

    /**
     * Sends [file] to the paired phone, refusing before the channel is opened if it is too large.
     *
     * S2142: [sendToReceiverId] turns the transfer into an errand - the phone is asked to offer the
     * file to that receiver instead of filing it away. Null keeps the shipped behaviour, which is
     * what every caller but the «Send to..» branch wants.
     */
    suspend fun sendFile(file: File, sendToReceiverId: String? = null): WearFileSendResult

    /** Sends payload at [uri] to the paired phone, refusing before the channel is opened if it is too large. */
    suspend fun sendUri(uri: Uri, displayName: String, sizeBytes: Long): WearFileSendResult

    /**
     * S2142: whether a phone is reachable right now, asked before anything is prepared to send.
     *
     * Strategic 11 criterion 9 requires an out-of-reach phone to be named before the transfer starts
     * rather than as its ending, and a caller that stages a copy first has already begun. The answer
     * goes stale the moment it is given - the phone leaves the room - so the transfer keeps its own
     * check; this one exists to avoid the work, not to replace it.
     */
    suspend fun isPhoneReachable(): Boolean
}
