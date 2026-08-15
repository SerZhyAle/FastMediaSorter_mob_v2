package com.sza.fastmediasorter.domain.identity

import com.google.gson.annotations.SerializedName

/**
 * Strongly-typed wrapper for a Google OAuth 2.0 scope URI.
 *
 * Per strategic S0200 / S0639: the full Drive scopes defined here (DRIVE, DRIVE_READONLY) are
 * RESTRICTED scopes, deliberately accepted as the core cloud-browsing feature. Published builds
 * are subject to Google restricted-scope OAuth App Verification (brand + scope verification,
 * Limited Use compliance, published privacy policy).
 *
 * They are NOT subject to the annual CASA security assessment. Per Google's restricted-scope
 * policy, CASA is required only for apps that "store or transmit restricted scope data on
 * servers" - i.e. that can access the data from or through a developer/third-party server. This
 * app handles Drive data strictly client-side (device <-> Google APIs only, no backend), which
 * keeps it out of CASA scope. That client-only data flow is a load-bearing invariant:
 * introducing any server-side storage/transmission of Drive data would trigger CASA and must go
 * through a separate ticket. No ADDITIONAL restricted scopes (Gmail, Photos, YouTube user-data)
 * may be added here without an explicit separate ticket either.
 *
 * @see <a href="https://developers.google.com/identity/protocols/oauth2/scopes">Google OAuth 2.0 Scopes</a>
 * @see <a href="https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification">Restricted scope verification (CASA trigger wording)</a>
 */
// S1657: a scope reaches Gson boxed, as an element of PrimaryGoogleAccount.grantedScopes, so what is written
// to the encrypted preferences is this class's backing field and not the scope URI on its own. The field name
// is an ordinary identifier that R8 renames, which would desync the stored binding across an app update -
// pinning it is the same fix as on the enclosing model, applied where the name actually travels.
@JvmInline value class GoogleScope(@SerializedName("value") val value: String) {
    companion object {
        /** Full read-write access to user's Drive. RESTRICTED scope (per Google): subject to restricted-scope OAuth App Verification. CASA does not apply - client-only data flow, see class KDoc. */
        val DRIVE = GoogleScope("https://www.googleapis.com/auth/drive")

        /** Read-only access to user's Drive metadata + content. RESTRICTED scope (per Google): subject to restricted-scope OAuth App Verification. CASA does not apply - client-only data flow, see class KDoc. */
        val DRIVE_READONLY = GoogleScope("https://www.googleapis.com/auth/drive.readonly")

        /** User's primary email address. */
        val EMAIL = GoogleScope("email")

        /** Basic profile information (name, photo URL). */
        val PROFILE = GoogleScope("profile")

        /** Required for ID token issuance via Credential Manager. */
        val OPENID = GoogleScope("openid")
    }
}
