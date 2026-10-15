package com.sza.fastmediasorter.data.security.e2e

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** One record of a sealed relay chunk payload: its type byte and body. */
class E2eRelayRecord(val type: Int, val body: ByteArray)

/** The opened key ring of one epoch: the true media type and the epoch key. */
class E2eRelayRing(val epoch: Long, val mediaType: String, val epochKey: ByteArray)

sealed interface E2eRelayMedia {
    class Clear(val epoch: Long, val bytes: ByteArray) : E2eRelayMedia

    /** The listener reconnects once and reads the current header chunk (item DL). */
    data class UnknownEpoch(val epoch: Long) : E2eRelayMedia

    /** A failed tag: never played, the listener stops with `sealed-failed`. */
    data object Failed : E2eRelayMedia
}

/**
 * The record layer of a sealed relay upload (contract item DL): `len` (4 bytes, big-endian, counting
 * the type byte and the body), `type`, body. A key ring (`0x4B`) carries the epoch key sealed under
 * each group key; a media record (`0x4D`) carries media sealed under the epoch key.
 */
object E2eRelayRecords {

    const val CONTENT_TYPE = "application/x-fmsx-sealed"
    const val TYPE_KEY_RING = 0x4B
    const val TYPE_MEDIA = 0x4D
    const val GROUP_KEY_LENGTH = 32
    const val EPOCH_KEY_LENGTH = 32
    const val MAX_RING_ENTRIES = 64
    const val KEY_ID_LENGTH = 8
    private const val LENGTH_FIELD = 4
    private const val EPOCH_FIELD = 4
    private const val SEALED_EPOCH_KEY_LENGTH = EPOCH_KEY_LENGTH + E2eAead.TAG_LENGTH
    private const val RING_ENTRY_LENGTH = KEY_ID_LENGTH + E2eAead.NONCE_LENGTH + SEALED_EPOCH_KEY_LENGTH
    private const val MAX_MEDIA_TYPE_LENGTH = 255
    private const val BYTE_MASK = 0xFF

    /** The 1 MiB chunk bound of item U is the most one record can need. */
    private const val MAX_RECORD_LENGTH = 1_048_576L
    private val KEY_ID_LABEL = E2eBytes.ascii("fmsx-relay-v1 key-id")

    fun keyId(groupKey: ByteArray): ByteArray = E2eBytes.sha256(KEY_ID_LABEL, groupKey).copyOf(KEY_ID_LENGTH)

    fun ringAad(broadcastId: String, epoch: Long): ByteArray = E2eBytes.utf8("relay-ring:$broadcastId:$epoch")

    fun mediaAad(broadcastId: String, epoch: Long, flag: Int): ByteArray =
        E2eBytes.utf8("relay-media:$broadcastId:$epoch:$flag")

    fun encodeKeyRing(
        broadcastId: String,
        epoch: Long,
        mediaType: String,
        epochKey: ByteArray,
        groupKeys: List<ByteArray>,
        random: E2eRandom = E2eRandom.secure,
    ): ByteArray {
        val typeBytes = E2eBytes.ascii(mediaType)
        require(typeBytes.size <= MAX_MEDIA_TYPE_LENGTH) { "media type too long" }
        require(groupKeys.size in 1..MAX_RING_ENTRIES) { "a key ring holds 1 to $MAX_RING_ENTRIES group keys" }
        require(epochKey.size == EPOCH_KEY_LENGTH) { "an epoch key is 32 bytes" }
        val aad = ringAad(broadcastId, epoch)
        val body = ByteArrayOutputStream()
        body.write(E2eBytes.u32be(epoch))
        body.write(typeBytes.size)
        body.write(typeBytes)
        body.write(groupKeys.size)
        groupKeys.forEach { groupKey ->
            val nonce = random.next(E2eAead.NONCE_LENGTH)
            body.write(keyId(groupKey))
            body.write(nonce)
            body.write(E2eAead.seal(groupKey, nonce, aad, epochKey))
        }
        return record(TYPE_KEY_RING, body.toByteArray())
    }

    fun encodeMedia(
        broadcastId: String,
        epoch: Long,
        epochKey: ByteArray,
        flag: Int,
        media: ByteArray,
        random: E2eRandom = E2eRandom.secure,
    ): ByteArray {
        val nonce = random.next(E2eAead.NONCE_LENGTH)
        val sealed = E2eAead.seal(epochKey, nonce, mediaAad(broadcastId, epoch, flag), media)
        return record(TYPE_MEDIA, E2eBytes.concat(E2eBytes.u32be(epoch), nonce, sealed))
    }

    /** Opens the ring with [groupKey]; null when no entry carries its key id or the tag fails. */
    fun openKeyRing(broadcastId: String, body: ByteArray, groupKey: ByteArray): E2eRelayRing? = runCatching {
        val epoch = E2eBytes.readU32be(body, 0)
        var offset = EPOCH_FIELD
        val typeLength = body[offset++].toInt() and BYTE_MASK
        val mediaType = String(body, offset, typeLength, Charsets.US_ASCII)
        offset += typeLength
        val count = body[offset++].toInt() and BYTE_MASK
        val wanted = keyId(groupKey)
        val entry = (0 until count).map { offset + it * RING_ENTRY_LENGTH }
            .firstOrNull { start -> body.copyOfRange(start, start + KEY_ID_LENGTH).contentEquals(wanted) }
        entry?.let { start ->
            val nonceStart = start + KEY_ID_LENGTH
            val nonce = body.copyOfRange(nonceStart, nonceStart + E2eAead.NONCE_LENGTH)
            val sealed = body.copyOfRange(nonceStart + E2eAead.NONCE_LENGTH, start + RING_ENTRY_LENGTH)
            E2eAead.open(groupKey, nonce, ringAad(broadcastId, epoch), sealed)
                ?.let { E2eRelayRing(epoch, mediaType, it) }
        }
    }.getOrNull()

    fun openMedia(broadcastId: String, body: ByteArray, flag: Int, epochKeyFor: (Long) -> ByteArray?): E2eRelayMedia {
        if (body.size < EPOCH_FIELD + E2eAead.NONCE_LENGTH + E2eAead.TAG_LENGTH) {
            return E2eRelayMedia.Failed
        }
        val epoch = E2eBytes.readU32be(body, 0)
        val key = epochKeyFor(epoch)
        return if (key == null) {
            E2eRelayMedia.UnknownEpoch(epoch)
        } else {
            val nonce = body.copyOfRange(EPOCH_FIELD, EPOCH_FIELD + E2eAead.NONCE_LENGTH)
            val sealed = body.copyOfRange(EPOCH_FIELD + E2eAead.NONCE_LENGTH, body.size)
            val clear = E2eAead.open(key, nonce, mediaAad(broadcastId, epoch, flag), sealed)
            clear?.let { E2eRelayMedia.Clear(epoch, it) } ?: E2eRelayMedia.Failed
        }
    }

    /** Splits a whole chunk payload into records; null when a length runs past the end. */
    fun split(payload: ByteArray): List<E2eRelayRecord>? = runCatching {
        val reader = Reader(payload.inputStream())
        generateSequence { reader.next() }.toList()
    }.getOrNull()

    private fun record(type: Int, body: ByteArray): ByteArray =
        E2eBytes.concat(E2eBytes.u32be(body.size + 1L), byteArrayOf(type.toByte()), body)

    /**
     * Reads records from a byte stream by `len` alone, however HTTP split it. [next] returns null at a
     * clean end between records and throws on a record cut short.
     */
    class Reader(private val input: InputStream) {
        fun next(): E2eRelayRecord? {
            val header = ByteArray(LENGTH_FIELD)
            val first = input.read(header, 0, LENGTH_FIELD)
            if (first < 0) {
                return null
            }
            readFully(header, first, LENGTH_FIELD - first)
            val length = E2eBytes.readU32be(header, 0)
            if (length < 1 || length > MAX_RECORD_LENGTH) {
                throw IOException("relay record length $length out of bounds")
            }
            val record = ByteArray(length.toInt())
            readFully(record, 0, record.size)
            return E2eRelayRecord(record[0].toInt() and BYTE_MASK, record.copyOfRange(1, record.size))
        }

        private fun readFully(target: ByteArray, offset: Int, length: Int) {
            var filled = 0
            while (filled < length) {
                val read = input.read(target, offset + filled, length - filled)
                if (read < 0) {
                    throw EOFException("relay record cut short")
                }
                filled += read
            }
        }
    }
}
