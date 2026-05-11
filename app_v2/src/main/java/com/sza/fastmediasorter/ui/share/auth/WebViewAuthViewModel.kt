package com.sza.fastmediasorter.ui.share.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.net.HttpCookie
import javax.inject.Inject

/**
 * S0116 §5.1 pillar L: thin VM around [AuthSessionRepository] for the
 * WebView-auth dialog. The dialog harvests cookies from the WebView's
 * [android.webkit.CookieManager] and forwards them here for persistent storage.
 *
 * S0155: Adds multi-account overload [saveSession(host, accountId, displayName, cookies)].
 */
@HiltViewModel
class WebViewAuthViewModel @Inject constructor(
    private val repository: AuthSessionRepository,
) : ViewModel() {

    /**
     * S0155: saves cookies under a specific (host, accountId) pair with a user-confirmed
     * [displayName]. Called from [WebViewAuthDialogFragment] after the "Name this account"
     * dialog is confirmed.
     */
    fun saveSession(host: String, accountId: String, displayName: String, cookies: List<HttpCookie>) {
        if (host.isBlank() || accountId.isBlank() || cookies.isEmpty()) return
        viewModelScope.launch {
            repository.saveSession(host, accountId, displayName, cookies)
        }
    }

    /**
     * S0116 legacy overload — saves cookies without account context.
     * @Deprecated Use [saveSession(host, accountId, displayName, cookies)] instead.
     */
    @Deprecated("Use saveSession(host, accountId, displayName, cookies)", level = DeprecationLevel.WARNING)
    fun saveSession(domain: String, cookies: List<HttpCookie>) {
        if (domain.isBlank() || cookies.isEmpty()) return
        viewModelScope.launch {
            @Suppress("DEPRECATION")
            repository.saveSession(domain, cookies)
        }
    }
}
