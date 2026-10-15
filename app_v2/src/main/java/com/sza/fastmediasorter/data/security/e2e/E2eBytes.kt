package com.sza.fastmediasorter.data.security.e2e

import org.bouncycastle.util.encoders.Base64
import java.security.MessageDigest

/**
 * Byte helpers of the DEVICE-EXCHANGE sealing layer (contract section 27).
 *
 * The package is compiled into the watch module as well, so it imports nothing from Android: the
 * base64url codec is built on Bouncy Castle rather than `java.util.Base64`, which the `legacy`
 * flavor's API 23 floor does not have.
 */
object E2eBytes {

    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8
    private const val U32_SIZE = 4
    private const val HEX_RADIX = 16

    /** Base64url without padding - the form of every binary member of a sealed box. */
    fun b64url(bytes: ByteArray): String =
        Base64.toBase64String(bytes).replace('+', '-').replace('/', '_').trimEnd('=')

    /** Decodes base64url without padding; padding and the standard alphabet are refused. */
    fun fromB64url(text: String): ByteArray {
        require(text.none { it == '=' || it == '+' || it == '/' }) { "not base64url without padding" }
        val standard = text.replace('-', '+').replace('_', '/')
        val padded = standard + "=".repeat((U32_SIZE - standard.length % U32_SIZE) % U32_SIZE)
        return Base64.decode(padded)
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }

    fun fromHex(text: String): ByteArray {
        require(text.length % 2 == 0) { "odd hex length" }
        return ByteArray(text.length / 2) { i -> text.substring(i * 2, i * 2 + 2).toInt(HEX_RADIX).toByte() }
    }

    fun u32be(value: Long): ByteArray = ByteArray(U32_SIZE) { i ->
        (value ushr (BITS_PER_BYTE * (U32_SIZE - 1 - i))).toByte()
    }

    fun readU32be(source: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until U32_SIZE) {
            value = (value shl BITS_PER_BYTE) or (source[offset + i].toLong() and BYTE_MASK.toLong())
        }
        return value
    }

    /** I2OSP of RFC 8017: [value] as an unsigned big-endian integer of exactly [length] bytes. */
    fun i2osp(value: Long, length: Int): ByteArray = ByteArray(length) { i ->
        val shift = BITS_PER_BYTE * (length - 1 - i)
        if (shift >= Long.SIZE_BITS) 0 else (value ushr shift).toByte()
    }

    fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(out, offset)
            offset += part.size
        }
        return out
    }

    fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII)

    fun utf8(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    fun sha256(vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { digest.update(it) }
        return digest.digest()
    }

    /** Constant-time equality, so a key comparison never leaks where two values first differ. */
    fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean {
        if (left.size != right.size) {
            return false
        }
        var diff = 0
        for (i in left.indices) {
            diff = diff or (left[i].toInt() xor right[i].toInt())
        }
        return diff == 0
    }
}
