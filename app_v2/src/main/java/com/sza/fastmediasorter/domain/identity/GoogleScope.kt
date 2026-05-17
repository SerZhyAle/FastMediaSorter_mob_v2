package com.sza.fastmediasorter.domain.identity

/**
 * Strongly-typed wrapper for a Google OAuth 2.0 scope URI.
 *
 * Per strategic S0200 §3.2: only non-restricted Google scopes are permitted.
 * Restricted scopes (Gmail, Photos, YouTube user-data) require an explicit separate
 * ticket and Google Security Assessment — never add them here without that gate.
 *
 * @see <a href="https://developers.google.com/identity/protocols/oauth2/scopes">Google OAuth 2.0 Scopes</a>
 */
@JvmInline value class GoogleScope(val value: String) {
    companion object {
        /** Full read-write access to user's Drive (non-sensitive scope, no Security Assessment required). */
        val DRIVE = GoogleScope("https://www.googleapis.com/auth/drive")

        /** Read-only access to user's Drive metadata + content. */
        val DRIVE_READONLY = GoogleScope("https://www.googleapis.com/auth/drive.readonly")

        /** User's primary email address. */
        val EMAIL = GoogleScope("email")

        /** Basic profile information (name, photo URL). */
        val PROFILE = GoogleScope("profile")

        /** Required for ID token issuance via Credential Manager. */
        val OPENID = GoogleScope("openid")
    }
}
