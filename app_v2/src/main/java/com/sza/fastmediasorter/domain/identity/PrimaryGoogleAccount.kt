package com.sza.fastmediasorter.domain.identity

import com.google.gson.annotations.SerializedName
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
// S1657: this envelope is Gson field-reflection persisted into the `primary_google_account_v1` encrypted
// preferences, which survive an app update. Release builds are minified, so the build that reads the blob can
// carry a different R8 mapping than the build that wrote it: unpinned field names get renamed and the stored
// keys stop matching. The read degrades to "no account bound" at best (silent re-login) and to a half-built
// object with nulls under non-null properties at worst, because Gson has no no-arg constructor to call here.
// Pinning every persisted field to its own name keeps obfuscation on while making the wire format
// R8-independent, and keeps already-stored bindings parseable because the wire names do not change.
data class PrimaryGoogleAccount(
    @SerializedName("email") val email: String,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("photoUrl") val photoUrl: String?,
    @SerializedName("grantedScopes") val grantedScopes: Set<GoogleScope>,
    @SerializedName("boundAt") val boundAt: Instant
)
