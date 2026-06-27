package com.sza.fastmediasorter.identity

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.util.GmsAvailabilityChecker
import com.sza.fastmediasorter.domain.identity.GoogleAccessToken
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.IdentityFailureReason
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
import com.sza.fastmediasorter.domain.identity.NeedsResignInReason
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccount
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Credential Manager-backed implementation of [GoogleIdentityRepository] (strategic S0200 ADR-5).
 *
 * Owns the single primary Google account binding for the application. Cloud (Drive) and any
 * future Google integrations consume this repository instead of the deprecated `GoogleSignIn`
 * SDK (strategic ADR-1).
 *
 * Token issuance is delegated to [GoogleTokenIssuer]; identity persistence is delegated to
 * [PrimaryGoogleAccountStore]. This class owns only the observable state, the Credential Manager
 * call surface, and the exception → reason mapping.
 *
 * Lives in the `cloudEnabled` source set - mounted into every cloud-enabled flavor.
 */
@Singleton
class CredentialManagerGoogleIdentityRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val store: PrimaryGoogleAccountStore,
    private val tokenIssuer: GoogleTokenIssuer,
    @ApplicationScope private val scope: CoroutineScope,
    @Named("googleWebClientId") private val webClientId: String
) : GoogleIdentityRepository {

    private val _state = MutableStateFlow<PrimaryGoogleAccountState>(PrimaryGoogleAccountState.Unbound)
    override val state: StateFlow<PrimaryGoogleAccountState> = _state.asStateFlow()

    init {
        scope.launch {
            _state.value = restoreFromStore()
        }
    }

    // region - interactive sign-in

    override suspend fun signInPrimary(
        activityContext: Context,
        scopes: Set<GoogleScope>,
        preferAccountChooser: Boolean
    ): IdentitySignInResult {
        // Pre-check Google Play Services availability before invoking Credential Manager.
        // Without this guard, an outdated / missing GMS produces a generic GetCredentialException
        // subtype that mapException() funnels into IdentityFailureReason.UnknownError, hiding the
        // actionable "update Play Services" CTA from the user (see GmsAvailabilityChecker).
        //
        // S0233 Front A: if the guard reports PlayServicesOutdated AND the caller passed a real
        // Activity, offer the user the standard Play Services repair flow. On success we re-check
        // the guard once and proceed with Credential Manager as if the sign-in had been tapped
        // afresh; the user does not need a second click. On user-decline or device-unfixable
        // we fall through to the legacy Failed path so existing UX remains intact.
        gmsGuard()?.let { reason ->
            val repaired = maybeRepairPlayServices(activityContext, reason)
            if (!repaired || gmsGuard() != null) {
                _state.value = PrimaryGoogleAccountState.Error(reason)
                return IdentitySignInResult.Failed(reason)
            }
        }
        val previous = _state.value
        _state.value = PrimaryGoogleAccountState.Authenticating
        return runCatching { performSignIn(activityContext, scopes, preferAccountChooser) }
            .recover { exception ->
                _state.value = previous
                mapException(exception).also {
                    if (it is IdentitySignInResult.Failed) {
                        _state.value = PrimaryGoogleAccountState.Error(it.reason)
                    }
                }
            }
            .getOrThrow()
    }

    private suspend fun performSignIn(
        activityContext: Context,
        scopes: Set<GoogleScope>,
        preferAccountChooser: Boolean
    ): IdentitySignInResult {
        val response = tryGetCredentialWithChooserFallback(activityContext, preferAccountChooser)
        val customCred = response.credential as? CustomCredential
        if (customCred == null || customCred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return IdentitySignInResult.Failed(IdentityFailureReason.UnknownError)
        }
        val tokenCred = GoogleIdTokenCredential.createFrom(customCred.data)
        val account = PrimaryGoogleAccount(
            email = tokenCred.id,
            displayName = tokenCred.displayName,
            photoUrl = tokenCred.profilePictureUri?.toString(),
            grantedScopes = scopes,
            boundAt = Instant.now()
        )
        store.save(account)
        _state.value = PrimaryGoogleAccountState.Bound(account)
        return IdentitySignInResult.Success(account)
    }

    private suspend fun tryGetCredentialWithChooserFallback(
        activityContext: Context,
        preferAccountChooser: Boolean
    ) = runCatching {
        // S0744: preferAccountChooser skips the authorized-accounts-only first pass and goes
        // straight to the full chooser, so the user can pick an account other than a previously
        // authorized one that can no longer mint a token.
        val firstRequest = buildGetCredentialRequest(filterByAuthorizedAccounts = !preferAccountChooser)
        CredentialManager.create(appContext).getCredential(activityContext, firstRequest)
    }.recoverCatching { error ->
        if (error is NoCredentialException) {
            // Fresh flavor package ids (for example vr / noLegal) may not have any pre-authorized
            // Google accounts yet even though an interactive chooser can still complete sign-in.
            Timber.i(error, "No pre-authorized Google credential; retrying with account chooser")
            val chooserRequest = buildGetCredentialRequest(filterByAuthorizedAccounts = false)
            CredentialManager.create(appContext).getCredential(activityContext, chooserRequest)
        } else {
            throw error
        }
    }.getOrThrow()

    override suspend fun requestAdditionalScopes(activityContext: Context, scopes: Set<GoogleScope>): IdentitySignInResult {
        val current = (_state.value as? PrimaryGoogleAccountState.Bound)?.account
            ?: return IdentitySignInResult.Failed(IdentityFailureReason.UnknownError)
        val union = current.grantedScopes + scopes
        val result = signInPrimary(activityContext, union)
        if (result is IdentitySignInResult.Success && result.account.email != current.email) {
            // User picked a different account at the prompt - revert to the original primary.
            store.save(current)
            _state.value = PrimaryGoogleAccountState.Bound(current)
            return IdentitySignInResult.Cancelled
        }
        return result
    }

    override suspend fun requestSecondaryAccount(activityContext: Context, scopes: Set<GoogleScope>): IdentitySignInResult {
        // Secondary path does NOT mutate _state nor the primary store. Still pre-check GMS to
        // surface PlayServicesOutdated explicitly instead of letting it bubble up as UnknownError.
        // S0233 Front A: the same auto-repair attempt is offered for secondary accounts; on success
        // we re-check the guard and continue, otherwise we fall through to the existing Failed path.
        gmsGuard()?.let { reason ->
            val repaired = maybeRepairPlayServices(activityContext, reason)
            if (!(repaired && gmsGuard() == null)) {
                return IdentitySignInResult.Failed(reason)
            }
        }
        return runCatching {
            val request = buildGetCredentialRequest(filterByAuthorizedAccounts = false)
            val response = CredentialManager.create(appContext).getCredential(activityContext, request)
            val customCred = response.credential as? CustomCredential
                ?: return@runCatching IdentitySignInResult.Failed(IdentityFailureReason.UnknownError)
            if (customCred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return@runCatching IdentitySignInResult.Failed(IdentityFailureReason.UnknownError)
            }
            val tokenCred = GoogleIdTokenCredential.createFrom(customCred.data)
            val secondaryAccount = PrimaryGoogleAccount(
                email = tokenCred.id,
                displayName = tokenCred.displayName,
                photoUrl = tokenCred.profilePictureUri?.toString(),
                grantedScopes = scopes,
                boundAt = Instant.now()
            )
            IdentitySignInResult.Success(secondaryAccount)
        }.recover { mapException(it) }
         .getOrThrow()
    }

    // endregion

    // region - sign-out & token

    override suspend fun signOutPrimary() {
        tokenIssuer.invalidate()
        store.clear()
        _state.value = PrimaryGoogleAccountState.Unbound
        // Note: server-side OAuth revocation requires the access-token string which we just
        // discarded via tokenIssuer.invalidate(). Local revocation through GoogleAuthUtil.clearToken
        // is sufficient for the Credential Manager flow; the next sign-in will re-prompt the user
        // for consent if any cached server-side state survives.
    }

    override suspend fun getAccessToken(scopes: Set<GoogleScope>): GoogleAccessToken? {
        val bound = (_state.value as? PrimaryGoogleAccountState.Bound)?.account ?: return null
        return when (val result = tokenIssuer.issue(bound.email, scopes)) {
            is TokenIssueResult.Success -> result.token
            TokenIssueResult.AccountAbsent -> {
                // Bound account is no longer on the device (GMS ACCOUNT_NOT_PRESENT). Drop the stale
                // binding so the next sign-in re-binds to a present account instead of dead-ending on
                // the absent one. NeedsResignIn is wrong here - its contract is "same email". S0743.
                Timber.i("Primary Google account no longer present on device; clearing stale binding")
                store.clear()
                _state.value = PrimaryGoogleAccountState.Unbound
                null
            }
            TokenIssueResult.Failed -> {
                _state.value = PrimaryGoogleAccountState.NeedsResignIn(bound, NeedsResignInReason.TokenExpired)
                null
            }
        }
    }

    override suspend fun invalidateToken() {
        tokenIssuer.invalidate()
    }

    // endregion

    // region - private helpers

    private suspend fun restoreFromStore(): PrimaryGoogleAccountState {
        return runCatching { store.load() }
            .onFailure { Timber.e(it, "Failed to restore primary account; treating as Unbound") }
            .getOrNull()
            ?.let { PrimaryGoogleAccountState.Bound(it) }
            ?: PrimaryGoogleAccountState.Unbound
    }

    private fun buildGetCredentialRequest(filterByAuthorizedAccounts: Boolean): GetCredentialRequest {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setAutoSelectEnabled(false)
            .build()
        return GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
    }

    private fun mapException(t: Throwable): IdentitySignInResult {
        // Diagnostic breadcrumb: without this line, the only signal in logcat for a failed sign-in
        // is the downstream "ShowSignInError reason=UnknownError" - the actual exception type and
        // message are lost. Cancellation is not noisy and is filtered out at info level.
        if (t !is GetCredentialCancellationException) {
            Timber.w(t, "Credential Manager sign-in failed: %s: %s", t::class.simpleName, t.message)
        }
        return when (t) {
            is GetCredentialCancellationException -> IdentitySignInResult.Cancelled
            is NoCredentialException -> IdentitySignInResult.Failed(IdentityFailureReason.PlayServicesOutdated, t)
            // Safety net: GMS-level SERVICE_VERSION_UPDATE_REQUIRED leaks through Credential Manager
            // as this provider-config exception with the misleading "no provider dependencies found"
            // message. The gmsGuard() should normally catch it earlier, but fall through here when
            // the cached status is stale (e.g. GMS updated mid-session). Without this branch the
            // user sees a generic UnknownError CTA instead of the actionable "Update Play Services".
            is GetCredentialProviderConfigurationException -> {
                IdentitySignInResult.Failed(IdentityFailureReason.PlayServicesOutdated, t)
            }
            is GetCredentialException -> IdentitySignInResult.Failed(IdentityFailureReason.UnknownError, t)
            else -> IdentitySignInResult.Failed(IdentityFailureReason.UnknownError, t)
        }
    }

    /**
     * Returns the failure reason to short-circuit with when Google Play Services is unusable
     * before invoking Credential Manager, or `null` when GMS is healthy enough to proceed.
     *
     * - `UPDATE_REQUIRED` → [IdentityFailureReason.PlayServicesOutdated] (user-actionable: update GMS).
     * - `UNAVAILABLE`     → [IdentityFailureReason.UnknownError] (no dedicated reason exists; the
     *   error dialog still routes the user to a sensible CTA via [GmsAvailabilityChecker] elsewhere).
     * - `OK`              → null (proceed to Credential Manager).
     *
     * Re-evaluates against [GmsAvailabilityChecker.MIN_GMS_VERSION_FOR_CREDENTIAL_MANAGER] on every
     * sign-in attempt, because:
     *   1. The cached status from `FastMediaSorterApp` uses the default `play-services-auth` baseline,
     *      which returns SUCCESS for GMS versions Credential Manager actually rejects (observed on
     *      emulator AVDs pinned to GMS 21.x while Credential Manager requires 23.08.15+).
     *   2. GMS can be updated via Play Store between process start and the user tapping sign-in.
     */
    private fun gmsGuard(): IdentityFailureReason? {
        GmsAvailabilityChecker.recheckFor(
            appContext,
            GmsAvailabilityChecker.MIN_GMS_VERSION_FOR_CREDENTIAL_MANAGER
        )
        return when {
            GmsAvailabilityChecker.needsUpdate -> {
                Timber.w("Sign-in aborted: Google Play Services update required")
                IdentityFailureReason.PlayServicesOutdated
            }
            GmsAvailabilityChecker.isUnavailable -> {
                Timber.w("Sign-in aborted: Google Play Services unavailable on this device")
                IdentityFailureReason.UnknownError
            }
            else -> null
        }
    }

    /**
     * S0233 Front A: attempt to launch the standard Play Services repair flow.
     *
     * Returns `true` when the Play Services Task suspended-await completed without throwing,
     * which Google's contract documents as "Play Services is now available at the required
     * version (or higher) in the current process scope". A `false` return covers every other
     * outcome - user cancelled the dialog, device cannot satisfy the requirement, or the
     * caller did not supply an Activity-typed context.
     *
     * Only reacts to [IdentityFailureReason.PlayServicesOutdated]; for [UnknownError] (typically
     * GMS absent / disabled) Play Services itself cannot be coaxed into self-repair through this
     * call and we leave the legacy fail-fast path in place.
     *
     * The call site re-runs [gmsGuard] after this returns `true`; we intentionally do NOT trust
     * the GMS-availability cache here because `makeGooglePlayServicesAvailable` updates GMS
     * out-of-process and the previously cached status may stay stale until the next live recheck.
     */
    private suspend fun maybeRepairPlayServices(
        activityContext: Context,
        reason: IdentityFailureReason
    ): Boolean {
        if (reason != IdentityFailureReason.PlayServicesOutdated) {
            return false
        }
        val activity = activityContext as? Activity
        if (activity == null) {
            return false
        }
        return runCatching {
            GoogleApiAvailability.getInstance()
                .makeGooglePlayServicesAvailable(activity)
                .await()
            true
        }.getOrElse {
            false
        }
    }

    // endregion
}
