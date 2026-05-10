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
 */
@HiltViewModel
class WebViewAuthViewModel @Inject constructor(
    private val repository: AuthSessionRepository,
) : ViewModel() {

    fun saveSession(domain: String, cookies: List<HttpCookie>) {
        if (domain.isBlank() || cookies.isEmpty()) return
        viewModelScope.launch {
            repository.saveSession(domain, cookies)
        }
    }
}
