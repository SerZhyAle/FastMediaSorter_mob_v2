package com.sza.fastmediasorter.data.broadcast.exchange

import java.security.MessageDigest

/**
 * S4160: `SHA256:` plus the unpadded standard base64 of the SHA-256 of a certificate's DER - the pin form of
 * `DEVICE-EXCHANGE` 7.1. Encoded by hand because `java.util.Base64` is absent below API 26 and the `legacy`
 * flavor, which plays streams, runs on API 23.
 */
object ExchangeCertificateFingerprint {

    private const val PREFIX = "SHA256:"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val BYTE_MASK = 0xFF
    private const val SEXTET_MASK = 0x3F
    private const val GROUP_BYTES = 3
    private const val SHIFT_FIRST = 18
    private const val SHIFT_SECOND = 12
    private const val SHIFT_THIRD = 6
    private const val SHIFT_BYTE_HIGH = 16
    private const val SHIFT_BYTE_MID = 8

    fun of(der: ByteArray): String = PREFIX + base64Unpadded(MessageDigest.getInstance("SHA-256").digest(der))

    fun base64Unpadded(bytes: ByteArray): String {
        val out = StringBuilder()
        var index = 0
        while (index < bytes.size) {
            val remaining = bytes.size - index
            val b0 = bytes[index].toInt() and BYTE_MASK
            val b1 = if (remaining > 1) bytes[index + 1].toInt() and BYTE_MASK else 0
            val b2 = if (remaining > 2) bytes[index + 2].toInt() and BYTE_MASK else 0
            val group = (b0 shl SHIFT_BYTE_HIGH) or (b1 shl SHIFT_BYTE_MID) or b2
            out.append(ALPHABET[(group shr SHIFT_FIRST) and SEXTET_MASK])
            out.append(ALPHABET[(group shr SHIFT_SECOND) and SEXTET_MASK])
            if (remaining > 1) out.append(ALPHABET[(group shr SHIFT_THIRD) and SEXTET_MASK])
            if (remaining > 2) out.append(ALPHABET[group and SEXTET_MASK])
            index += GROUP_BYTES
        }
        return out.toString()
    }
}
