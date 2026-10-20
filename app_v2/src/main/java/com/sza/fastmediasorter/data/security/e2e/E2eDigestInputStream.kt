package com.sza.fastmediasorter.data.security.e2e

import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Reads at most [limit] bytes of [input], hashing and counting them on the way. A sealed clipboard value is read
 * by its declared size and checked against the server's SHA-256 while its segments open (contract DEVICE-EXCHANGE
 * 22 item BY, 27 item DN); a clear source is cut at its declared size before it is sealed.
 */
class E2eDigestInputStream(
    input: InputStream,
    private val limit: Long,
    private val digest: MessageDigest,
    private val onRead: (Long) -> Unit = {},
) : FilterInputStream(input) {

    var count = 0L
        private set

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and BYTE_MASK
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val left = limit - count
        val read = if (left <= 0) -1 else super.read(buffer, offset, minOf(length.toLong(), left).toInt())
        if (read > 0) {
            digest.update(buffer, offset, read)
            count += read
            onRead(count)
        }
        return read
    }

    /** The lower-case hex SHA-256 of what was read so far; ends the digest, so it is asked once. */
    fun hexDigest(): String = E2eBytes.hex(digest.digest())

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}
