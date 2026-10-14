package com.sza.fastmediasorter.domain.model

import java.security.SecureRandom
import java.util.Base64

/**
 * The share id of contract ANYWHERE-ACCESS section 6.2: 16 random bytes, base64url without padding.
 * Whoever holds it may reach the share through the exchange server, so it is a capability, not a
 * label - it is generated only from a CSPRNG and its form is checked on every decode.
 */
object SftpShareId {

    private const val BYTE_COUNT = 16
    private const val ENCODED_LENGTH = 22
    private val ALPHABET = Regex("^[A-Za-z0-9_-]{$ENCODED_LENGTH}$")

    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(BYTE_COUNT).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // 22 base64url characters carry 132 bits; the last character must leave the 4 spare bits zero,
    // otherwise two spellings would name the same 16 bytes.
    fun isValid(value: String?): Boolean {
        val decoded = value?.takeIf(ALPHABET::matches)
            ?.let { runCatching { Base64.getUrlDecoder().decode(it) }.getOrNull() }
        return decoded != null && decoded.size == BYTE_COUNT &&
            Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == value
    }
}
