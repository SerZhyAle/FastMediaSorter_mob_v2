package com.sza.fastmediasorter.domain.usecase.launcher

import android.webkit.CookieManager
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2286: Syncs primary Google account identity state into WebView cookie session storage
 * for launcher desktop widgets (Google Maps, Google Keep, Google Calendar live frames).
 */
@Singleton
class SyncGoogleLauncherSessionUseCase @Inject constructor(
    private val identityRepository: GoogleIdentityRepository,
) {

    suspend fun observeAndSync() {
        Timber.d("S2286: SyncGoogleLauncherSessionUseCase active observer started")
        identityRepository.state.collectLatest { state ->
            when (state) {
                is PrimaryGoogleAccountState.Bound -> {
                    Timber.d("S2286: Primary Google account bound (${state.account.email}) - WebView cookies active")
                    runCatching {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.flush()
                    }.onFailure { Timber.w(it, "Failed to configure WebView cookie manager") }
                }
                is PrimaryGoogleAccountState.Unbound,
                is PrimaryGoogleAccountState.Error,
                is PrimaryGoogleAccountState.NeedsResignIn -> {
                    Timber.d("S2286: Primary Google account inactive - clearing launcher WebView cookies")
                    runCatching {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.removeAllCookies(null)
                        cookieManager.flush()
                    }.onFailure { Timber.w(it, "Failed to clear WebView cookies on account unbind") }
                }
                PrimaryGoogleAccountState.Authenticating -> Unit
            }
        }
    }
}
