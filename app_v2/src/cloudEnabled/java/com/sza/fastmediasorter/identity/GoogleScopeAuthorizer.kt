package com.sza.fastmediasorter.identity

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.ui.cloudauth.GoogleDriveAuthResolutionTracker
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of one interactive [GoogleScopeAuthorizer.authorize] call (S4427). */
sealed interface ScopeAuthorization {
    /** The scopes Google reports as granted, which may be fewer than requested. */
    data class Granted(val scopes: Set<GoogleScope>) : ScopeAuthorization

    data object Cancelled : ScopeAuthorization

    data class Failed(val cause: Throwable) : ScopeAuthorization
}

/**
 * The one interactive OAuth scope grant on the GMS path, shared by sign-in and the "allow" action
 * of the cross-device exchange (S4427).
 *
 * Credential Manager proves the identity only; the Drive scopes need this second call. The result
 * is read back from Google rather than assumed, because the consent screen may grant fewer scopes
 * than were asked for and the binding must list only what was granted.
 */
@Singleton
class GoogleScopeAuthorizer @Inject constructor() {

    // The GMS authorization client throws undeclared runtime types; this is the boundary where they
    // become a Failed outcome instead of crashing the sign-in flow.
    @Suppress("TooGenericExceptionCaught")
    suspend fun authorize(activityContext: Context, email: String, scopes: Set<GoogleScope>): ScopeAuthorization {
        if (scopes.isEmpty()) return ScopeAuthorization.Granted(emptySet())
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopes.map { Scope(it.value) })
            .setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
            .build()
        return try {
            grantedScopes(activityContext, request)
                ?.let { granted -> ScopeAuthorization.Granted(granted.map(::GoogleScope).toSet() intersect scopes) }
                ?: ScopeAuthorization.Cancelled
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            Timber.w(error, "Google scope authorization failed for scopes=$scopes")
            ScopeAuthorization.Failed(error)
        }
    }

    // Null means the user dismissed the consent screen.
    private suspend fun grantedScopes(activityContext: Context, request: AuthorizationRequest): List<String>? {
        val client = Identity.getAuthorizationClient(activityContext)
        val result = client.authorize(request).await()
        val pendingIntent = result.pendingIntent
        return when {
            !result.hasResolution() -> result.grantedScopes
            pendingIntent == null -> null
            else -> GoogleDriveAuthResolutionTracker.resolveForResult(activityContext, pendingIntent)
                ?.let { data -> client.getAuthorizationResultFromIntent(data).grantedScopes }
        }
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
