package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearFileSendOutcome
import java.io.File

/**
 * S1861: the watch half of the symmetric transfer - the watch as initiator, not only as responder.
 *
 * The phone-side twin is a queue with a progress snapshot; this one is a single suspending call
 * because a watch sends one small recording at a time and has no screen to draw a queue on. What is
 * settled here is that either side may open the channel; the screens that call it arrive with the
 * recorder (S1862) and the watch-side file operations (S1863).
 */
interface WearFileSenderRepository {

    /** Sends [file] to the paired phone, refusing before the channel is opened if it is too large. */
    suspend fun sendFile(file: File): WearFileSendOutcome
}
