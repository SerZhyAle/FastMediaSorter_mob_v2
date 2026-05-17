package com.sza.fastmediasorter.domain.identity

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for the Google identity domain (strategic S0200 ADR-1).
 *
 * Owns the single primary Google account binding for the entire application. Cloud (Drive) and any
 * future Google-integration consumers depend on this interface; they MUST NOT call `GoogleSignIn`
 * directly. Implementations live in the `cloudEnabled` source set (real Credential Manager binding)
 * and `cloudDisabled` (no-op for `lite` flavor).
 *
 * Thread safety: implementations are expected to be safe for concurrent callers — token issuance
 * serialises internally via mutex.
 *
 * @see PrimaryGoogleAccountState observable state contract
 * @see GoogleScope permitted OAuth scopes
 */
interface GoogleIdentityRepository {
    /** Hot, deduplicated state of the primary account. Replayed to new collectors. */
    val state: StateFlow<PrimaryGoogleAccountState>

    /**
     * Launches interactive sign-in via Credential Manager. Suspends until the user cancels or
     * the grant completes. On success, [state] flips to [PrimaryGoogleAccountState.Bound].
     *
     * @param activityContext an Activity context — Credential Manager bottom sheet needs an Activity.
     * @param scopes the OAuth scopes to request. Must be a subset of [GoogleScope] non-restricted constants.
     */
    suspend fun signInPrimary(activityContext: Context, scopes: Set<GoogleScope>): IdentitySignInResult

    /**
     * Adds additional [scopes] to an already-bound primary account. If the user picks a different
     * Google account in the Credential Manager dialog, the primary is preserved and the call returns
     * [IdentitySignInResult.Cancelled].
     */
    suspend fun requestAdditionalScopes(activityContext: Context, scopes: Set<GoogleScope>): IdentitySignInResult

    /**
     * Drops the primary binding. Cached tokens are invalidated; remote revocation is queued via
     * `PendingRevocationDao` so it survives network/process loss.
     */
    suspend fun signOutPrimary()

    /**
     * Returns a fresh access token covering [scopes] for the currently bound primary account.
     * Performs silent refresh as needed. Returns null when:
     * - no primary account is bound, or
     * - silent refresh failed (state transitions to [PrimaryGoogleAccountState.NeedsResignIn]).
     *
     * Never logs the returned token.
     */
    suspend fun getAccessToken(scopes: Set<GoogleScope>): GoogleAccessToken?

    /** Marks every cached token as expired so the next [getAccessToken] forces a refresh. */
    suspend fun invalidateToken()

    /**
     * Launches an interactive sign-in for a SECONDARY Google account (does NOT change primary).
     * Returned account email is used by `AddResource` flows to persist a new
     * `NetworkCredentialsEntity` for multi-account Drive. The caller is responsible for storing
     * the secondary email and any per-secondary-account state.
     */
    suspend fun requestSecondaryAccount(activityContext: Context, scopes: Set<GoogleScope>): IdentitySignInResult
}
