package com.sza.fastmediasorter.domain.identity

import java.time.Instant

/**
 * Opaque OAuth 2.0 access token issued for a specific [scopes] set.
 *
 * Per strategic S0200: the [token] string is sensitive and MUST NEVER be logged.
 * Callers pass it directly to network clients (`Authorization: Bearer <token>`) and discard.
 *
 * @property token raw bearer string returned by `GoogleAuthUtil.getToken` (issued under Credential Manager).
 * @property scopes the exact scope set this token covers - callers must request matching scopes when re-issuing.
 * @property expiresAt UTC instant at which Google considers this token invalid. Refresh ahead of time using [GoogleIdentityRepository.getAccessToken].
 */
data class GoogleAccessToken(
    val token: String,
    val scopes: Set<GoogleScope>,
    val expiresAt: Instant
)
