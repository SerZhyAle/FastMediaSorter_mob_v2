package com.sza.fastmediasorter.service

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearScreenshotAckParseResult
import com.sza.fastmediasorter.domain.model.WearScreenshotRequestCodec
import timber.log.Timber
import javax.inject.Inject

/**
 * S3110: the watch's verdict on a screenshot this phone asked for, published to whoever waits.
 *
 * Its own collaborator rather than a method on [PhoneWearListenerService], for the reason S3109 gave
 * the clipboard receiver: that service holds no reference to the screen that asked and cannot acquire
 * one, so the answer travels the process-wide bus - and the service is at detekt's function ceiling,
 * which is the same observation stated as a number.
 */
class WearScreenshotAckReceiver @Inject constructor(
    private val gson: Gson
) {

    /**
     * An answer carrying no request id is the watch refusing something it could not parse, and it is
     * published like any other refusal: the screen that asked has nothing else to show, and dropping
     * it would leave that screen waiting out the whole timeout.
     */
    suspend fun publishAck(data: ByteArray) {
        Timber.d("S3110: watch screenshot verdict received on the phone")
        when (val parsed = WearScreenshotRequestCodec.parseAck(data, gson)) {
            is WearScreenshotAckParseResult.Malformed ->
                Timber.w("Watch screenshot: could not parse the watch's answer")

            is WearScreenshotAckParseResult.Parsed -> WearSyncEvents.emitScreenshotAck(parsed.ack)
        }
    }
}
