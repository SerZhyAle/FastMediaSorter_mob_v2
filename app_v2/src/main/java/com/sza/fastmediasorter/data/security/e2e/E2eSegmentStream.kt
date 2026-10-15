package com.sza.fastmediasorter.data.security.e2e

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream

/** A sealed clipboard stream that did not open: a failed tag, a missing last segment or a short read. */
class E2eSegmentException(message: String) : IOException(message)

/**
 * The bytes of a sealed clipboard value (contract item DN): 64 KiB segments, each under AES-256-GCM
 * with the value key, the nonce being the segment index followed by a last-segment flag (the STREAM
 * construction), so truncation, reordering and appending all fail a tag.
 */
object E2eSegmentStream {

    const val SEGMENT_SIZE = 65536
    private const val INDEX_LENGTH = 11
    private const val VALUE_KEY_LENGTH = 32
    private const val LAST_FLAG: Byte = 0x01
    private const val NOT_LAST_FLAG: Byte = 0x00
    private val VALUE_KEY_INFO = E2eBytes.ascii("fmsx-e2e-v1 clipboard")

    fun valueKey(contentKey: ByteArray): ByteArray = E2eKdf.derive(contentKey, VALUE_KEY_INFO, VALUE_KEY_LENGTH)

    fun segmentCount(clearSize: Long): Long {
        require(clearSize > 0) { "a sealed clipboard value is never empty" }
        return (clearSize + SEGMENT_SIZE - 1) / SEGMENT_SIZE
    }

    fun sealedSize(clearSize: Long): Long = clearSize + E2eAead.TAG_LENGTH * segmentCount(clearSize)

    fun nonce(index: Long, last: Boolean): ByteArray =
        E2eBytes.concat(E2eBytes.i2osp(index, INDEX_LENGTH), byteArrayOf(if (last) LAST_FLAG else NOT_LAST_FLAG))

    /** Seals [input] to [output]; returns the number of clear bytes read. */
    fun seal(input: InputStream, output: OutputStream, valueKey: ByteArray): Long {
        var index = 0L
        var total = 0L
        var current = readSegment(input, SEGMENT_SIZE)
        require(current.isNotEmpty()) { "a sealed clipboard value is never empty" }
        while (true) {
            val next = readSegment(input, SEGMENT_SIZE)
            val last = next.isEmpty()
            output.write(E2eAead.seal(valueKey, nonce(index, last), ByteArray(0), current))
            total += current.size
            if (last) {
                break
            }
            current = next
            index++
        }
        return total
    }

    /**
     * Opens [input] to [output]; returns the number of clear bytes written. Throws on any failed
     * segment - the caller discards everything it wrote, because nothing partial may be placed.
     */
    fun open(input: InputStream, output: OutputStream, valueKey: ByteArray): Long {
        val source = PushbackInputStream(input, 1)
        var index = 0L
        var total = 0L
        var done = false
        while (!done) {
            val sealed = readSegment(source, SEGMENT_SIZE + E2eAead.TAG_LENGTH)
            if (sealed.size <= E2eAead.TAG_LENGTH) {
                throw E2eSegmentException("segment $index is missing or short")
            }
            done = atEnd(source)
            val clear = E2eAead.open(valueKey, nonce(index, done), ByteArray(0), sealed)
                ?: throw E2eSegmentException("segment $index failed its tag")
            output.write(clear)
            total += clear.size
            index++
        }
        return total
    }

    private fun atEnd(source: PushbackInputStream): Boolean {
        val next = source.read()
        if (next >= 0) {
            source.unread(next)
        }
        return next < 0
    }

    private fun readSegment(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var filled = 0
        while (filled < size) {
            val read = input.read(buffer, filled, size - filled)
            if (read < 0) {
                break
            }
            filled += read
        }
        return if (filled == size) buffer else buffer.copyOf(filled)
    }
}
