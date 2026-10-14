package com.sza.fastmediasorter.domain.model

/**
 * The refusal reasons of contract DEVICE-EXCHANGE 7.10 a screen can say something specific about, keyed by
 * their wire token. Every other token is [OTHER]: the screen shows the generic refusal, never the raw token.
 */
enum class DeviceExchangeRefusal(private val tokens: Set<String>) {
    BAD_CREDENTIALS(setOf("bad-credentials")),

    /** The device was revoked, or the entry holds no token any more; only signing in again helps. */
    DEVICE_REVOKED(setOf("device-revoked", "not-enrolled")),
    VERSION_UNSUPPORTED(setOf("version-unsupported")),
    CAPACITY(setOf("capacity")),
    RATE_LIMITED(setOf("rate-limited")),
    UNSUPPORTED(setOf("unsupported")),

    /** Not a wire reason: this device's secret store refused the token it was given. */
    TOKEN_NOT_STORED(setOf(DeviceExchangeEnrollResult.REASON_TOKEN_NOT_STORED)),
    OTHER(emptySet());

    companion object {
        fun of(reason: String): DeviceExchangeRefusal = entries.firstOrNull { reason in it.tokens } ?: OTHER
    }
}
