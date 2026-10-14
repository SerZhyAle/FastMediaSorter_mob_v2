package com.sza.fastmediasorter.service

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearResourcePassCodec
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4228: reads the watch's answer to one resource pass. Nothing waits on it - the pass is sent after the selection
 * push and a lost answer changes nothing the person sees - so the answer is the log line a support report needs to
 * tell "the watch refused the PIN" from "the watch never got it".
 *
 * A collaborator in the listener's fall-through, like the delete handler beside it, because that class sits at
 * detekt's function ceiling.
 */
@Singleton
class WearResourcePassAckHandler @Inject constructor(
    private val gson: Gson,
) {

    /** Answers whether [path] is the pass-answer route, acting on it when it is. */
    fun handle(path: String, payload: ByteArray): Boolean {
        if (path != WearDataLayerPaths.RESOURCE_PASS_ACK) return false
        val ack = WearResourcePassCodec.parseAck(payload, gson)
        when {
            ack == null -> Timber.w("Resource pass: an unreadable answer from the watch")
            ack.stored -> Timber.i("Resource pass: the watch keeps the PIN")
            else -> Timber.w("Resource pass: the watch refused it - %s", ack.reason)
        }
        return true
    }
}
