package com.sza.fastmediasorter.domain.identity

/**
 * Observable lifecycle state of the primary Google account binding.
 *
 * Per strategic S0200 §5.1: identity domain exposes a hot reactive state Flow consumed by both
 * cloud-layer code (Drive) and Settings UI. State transitions are bidirectional except [Bound] →
 * [Unbound] which is terminal until the user signs in again.
 */
sealed interface PrimaryGoogleAccountState {
    /** No primary account bound. Initial state on fresh install or after sign-out. */
    data object Unbound : PrimaryGoogleAccountState

    /** Credential Manager bottom sheet / consent dialog is in flight. UI shows progress. */
    data object Authenticating : PrimaryGoogleAccountState

    /** Account bound and tokens are usable (subject to silent refresh). */
    data class Bound(val account: PrimaryGoogleAccount) : PrimaryGoogleAccountState

    /** Account known but silent refresh failed — user must re-sign-in with the same email. */
    data class NeedsResignIn(
        val account: PrimaryGoogleAccount,
        val reason: NeedsResignInReason
    ) : PrimaryGoogleAccountState

    /** Last sign-in attempt failed terminally. User can retry. */
    data class Error(val cause: IdentityFailureReason) : PrimaryGoogleAccountState
}

enum class NeedsResignInReason {
    TokenExpired,
    ScopeRevoked,
    CredentialManagerError,
    NetworkUnavailable
}

enum class IdentityFailureReason {
    CctUnavailable,
    PlayServicesOutdated,
    UserCancelled,
    NetworkError,
    UnknownError
}
