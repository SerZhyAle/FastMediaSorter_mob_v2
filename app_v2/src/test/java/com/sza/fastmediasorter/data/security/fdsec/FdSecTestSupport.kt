package com.sza.fastmediasorter.data.security.fdsec

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader

/** Hex helpers and the conformance-vector loader shared by the FD-SEC tests. */
object FdSecTestSupport {

    private const val HEX_RADIX = 16
    private const val HEX_PER_BYTE = 2

    fun hex(value: String): ByteArray {
        val out = ByteArray(value.length / HEX_PER_BYTE)
        for (i in out.indices) {
            val at = i * HEX_PER_BYTE
            out[i] = value.substring(at, at + HEX_PER_BYTE).toInt(HEX_RADIX).toByte()
        }
        return out
    }

    fun toHex(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }

    fun vectors(): JsonObject {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("fdsec/vectors.json")) {
            "fdsec/vectors.json is missing from the test resources"
        }
        return InputStreamReader(stream, Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
    }

    fun resourceBytes(name: String): ByteArray {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("fdsec/$name")) {
            "fdsec/$name is missing from the test resources"
        }
        return stream.use { it.readBytes() }
    }
}

/**
 * Replays the vectors' fixed random inputs in the order the writer consumes them: salt, file key,
 * wrap nonce, metadata padding, alignment padding, tail padding.
 */
class QueuedRandomSource(private val queue: ArrayDeque<ByteArray>) : FdSecRandomSource {

    constructor(vararg values: ByteArray) : this(ArrayDeque(values.toList()))

    override fun nextBytes(size: Int): ByteArray {
        val next = queue.removeFirstOrNull()
            ?: error("the writer asked for $size more random bytes than the vector fixes")
        check(next.size == size) { "expected $size random bytes, the vector fixes ${next.size}" }
        return next
    }
}
