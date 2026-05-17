package com.sza.fastmediasorter.identity

import android.content.Context
import com.sza.fastmediasorter.domain.identity.GoogleAccessToken
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.IdentityFailureReason
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * No-op implementation of [GoogleIdentityRepository] for the `cloudDisabled` source set (lite flavor).
 *
 * The `lite` build has `SUPPORT_CLOUD = false` — no Drive UI is reachable, but Hilt still needs a
 * concrete binding so the graph compiles. All operations are inert. Sign-in attempts return
 * [IdentityFailureReason.UnknownError] since the lite flavor exposes no Google integration path
 * (no dedicated `CloudDisabled` reason exists — this is the closest semantic and is documented
 * here so callers do not interpret it as a CCT / Play Services problem).
 */
@Singleton
class NoOpGoogleIdentityRepository @Inject constructor() : GoogleIdentityRepository {

    override val state: StateFlow<PrimaryGoogleAccountState> =
        MutableStateFlow(PrimaryGoogleAccountState.Unbound).asStateFlow()

    override suspend fun signInPrimary(activityContext: Context, scopes: Set<GoogleScope>): IdentitySignInResult =
        IdentitySignInResult.Failed(IdentityFailureReason.UnknownError)

    override suspend fun requestAdditionalScopes(activityContext: Context, scopes: Set<GoogleScope>): IdentitySignInResult =
        IdentitySignInResult.Failed(IdentityFailureReason.UnknownError)

    override suspend fun signOutPrimary() = Unit

    override suspend fun getAccessToken(scopes: Set<GoogleScope>): GoogleAccessToken? = null

    override suspend fun invalidateToken() = Unit

    override suspend fun requestSecondaryAccount(activityContext: Context, scopes: Set<GoogleScope>): IdentitySignInResult =
        IdentitySignInResult.Failed(IdentityFailureReason.UnknownError)
}
