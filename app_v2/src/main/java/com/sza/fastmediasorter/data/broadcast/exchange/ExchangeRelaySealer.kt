package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eAead
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eRelayRecords
import java.io.IOException

/**
 * Turns the payloads of one upload stream into the sealed records of contract DEVICE-EXCHANGE 27 item DL: the header
 * chunk opens with the key ring of [ring]'s epoch, followed by the format header as a media record when there is
 * one; every other payload becomes one media record under the flag of the chunk it rides in.
 */
class ExchangeRelaySealer(
    private val broadcastId: String,
    private val mediaType: String,
    private val ring: ExchangeRelayRing,
    private val isCurrent: () -> Boolean = { true },
) {

    /** The header chunk's payload: always present, since a sealed upload always sends one (item DL). */
    fun headerChunk(formatHeader: ByteArray?): ByteArray {
        val keyRing = E2eRelayRecords.encodeKeyRing(broadcastId, ring.epoch, mediaType, ring.epochKey, ring.groupKeys)
        val header = formatHeader?.let { media(it, ExchangeRelayChunkWriter.FLAG_HEADER) }
        return if (header == null) keyRing else E2eBytes.concat(keyRing, header)
    }

    /**
     * One media record. Throws once the own group key was replaced (a device left the account), which ends this
     * upload stream; the uploader reconnects at once with a replacement upload and a new epoch (item DL).
     */
    fun media(payload: ByteArray, flag: Int): ByteArray {
        if (!isCurrent()) throw IOException("the relay epoch was replaced")
        return E2eRelayRecords.encodeMedia(broadcastId, ring.epoch, ring.epochKey, flag, payload)
    }

    companion object {
        /** What one media record adds to its payload: length, type, epoch, nonce and tag. */
        const val RECORD_OVERHEAD = 4 + 1 + 4 + E2eAead.NONCE_LENGTH + E2eAead.TAG_LENGTH
    }
}
