package com.sza.fastmediasorter.utils

import java.util.Base64

/**
 * S0046 - Normalises SSH host-key fingerprints between supported input formats.
 *
 * Accepted input forms (case-insensitive for hex):
 *  - `SHA256:<base64>` - with or without trailing `=` padding (canonical-ish)
 *  - bare `<base64>` - base64 SHA256 with or without padding
 *  - hex with colons - `aa:bb:cc:..` (raw SHA256 bytes encoded as hex)
 *  - hex without colons - `aabbcc..`
 *
 * Output: canonical form `SHA256:<base64-no-padding>`. Unparseable input → `null`.
 *
 * Pure utility - no Timber, no I/O, no dependencies beyond `java.util.Base64`
 * (available on Android via core library desugaring; see app_v2/build.gradle.kts).
 */
object SshFingerprintNormalizer {

    private const val SHA256_PREFIX = "SHA256:"
    private const val SHA256_BYTE_LEN = 32

    /**
     * Convert any accepted fingerprint form to canonical `SHA256:<base64-no-padding>`.
     *
     * @return canonical string, or `null` if the input cannot be parsed as an SHA256 fingerprint.
     */
    fun canonical(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()

        // Form 1: SHA256:<base64>
        if (trimmed.startsWith(SHA256_PREFIX, ignoreCase = true)) {
            val payload = trimmed.substring(SHA256_PREFIX.length)
            val bytes = decodeBase64OrNull(payload) ?: return null
            if (bytes.size != SHA256_BYTE_LEN) return null
            return SHA256_PREFIX + encodeBase64NoPad(bytes)
        }

        // Form 2 / 3: hex (with or without colons)
        val maybeHex = if (trimmed.contains(':')) trimmed.replace(":", "") else trimmed
        if (maybeHex.length == SHA256_BYTE_LEN * 2 && maybeHex.all { it.isHexDigit() }) {
            val bytes = ByteArray(SHA256_BYTE_LEN)
            for (i in 0 until SHA256_BYTE_LEN) {
                val hi = Character.digit(maybeHex[i * 2], 16)
                val lo = Character.digit(maybeHex[i * 2 + 1], 16)
                bytes[i] = ((hi shl 4) or lo).toByte()
            }
            return SHA256_PREFIX + encodeBase64NoPad(bytes)
        }

        // Form 4: bare base64
        val bytes = decodeBase64OrNull(trimmed) ?: return null
        if (bytes.size != SHA256_BYTE_LEN) return null
        return SHA256_PREFIX + encodeBase64NoPad(bytes)
    }

    /**
     * Compact form for list rows / Snackbars - `SHA256:abcdefghijkl..` (first 12 chars + `..`).
     * Pass a value already known to be canonical (output of [canonical]).
     */
    fun shortForList(canonical: String): String {
        if (!canonical.startsWith(SHA256_PREFIX, ignoreCase = true)) return canonical
        val payload = canonical.substring(SHA256_PREFIX.length)
        if (payload.length <= 12) return canonical
        return SHA256_PREFIX + payload.substring(0, 12) + ".."
    }

    private fun decodeBase64OrNull(s: String): ByteArray? {
        // Accept both padded and unpadded base64; strategy: strip any trailing `=`, then re-pad
        // to a multiple of 4 so java.util.Base64.getDecoder() (which requires correct padding) succeeds.
        val noPad = s.trimEnd('=')
        if (noPad.isEmpty()) return null
        if (!noPad.all { it.isBase64Char() }) return null
        val pad = (4 - noPad.length % 4) % 4
        val padded = noPad + "=".repeat(pad)
        return try {
            Base64.getDecoder().decode(padded)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun encodeBase64NoPad(bytes: ByteArray): String =
        Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun Char.isBase64Char(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '+' || this == '/'
}
