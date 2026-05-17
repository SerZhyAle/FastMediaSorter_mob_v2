package com.sza.fastmediasorter.domain.identity

import java.time.Instant

/**
 * Snapshot of the primary Google account currently bound to the application.
 *
 * Per strategic S0200: exactly one primary account is supported at a time. Secondary Drive accounts
 * use a different multi-account path and are NOT represented by this type.
 *
 * @property email canonical Google account email (also serves as the multi-account lookup key for [grantedScopes]).
 * @property displayName user-visible name from the Google profile, when available.
 * @property photoUrl avatar URL from the Google profile, when available. May be null when the user has no photo.
 * @property grantedScopes scopes the user consented to at sign-in (or after a successful [GoogleIdentityRepository.requestAdditionalScopes]).
 * @property boundAt UTC instant when this binding was established or last refreshed.
 */
data class PrimaryGoogleAccount(
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val grantedScopes: Set<GoogleScope>,
    val boundAt: Instant
)
