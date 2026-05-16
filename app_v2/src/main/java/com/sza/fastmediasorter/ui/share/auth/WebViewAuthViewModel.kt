package com.sza.fastmediasorter.ui.share.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.net.HttpCookie
import javax.inject.Inject

@HiltViewModel
class WebViewAuthViewModel @Inject constructor(
    private val repository: AuthSessionRepository,
) : ViewModel() {

    fun saveSession(
        host: String,
        accountId: String,
        displayName: String,
        cookies: List<HttpCookie>,
        userAgent: String? = null,
    ) {
        if (host.isBlank() || accountId.isBlank() || cookies.isEmpty()) return
        viewModelScope.launch {
            repository.saveSession(host, accountId, displayName, cookies, userAgent)
        }
    }

    /**
     * S0211: bridges Fragment harvest to the dedup-aware repository call.
     * Emits the accountId used (new or reused) via [onSaved] on the ViewModel scope.
     */
    fun saveSessionFromWebView(
        host: String,
        displayName: String,
        cookies: List<HttpCookie>,
        userAgent: String? = null,
        onSaved: (String?) -> Unit,
    ) {
        if (host.isBlank() || cookies.isEmpty()) {
            onSaved(null)
            return
        }
        viewModelScope.launch {
            val id = repository.saveSessionFromWebView(host, displayName, cookies, userAgent)
            onSaved(id)
        }
    }

    @Deprecated("Use saveSession(host, accountId, displayName, cookies)", level = DeprecationLevel.WARNING)
    fun saveSession(domain: String, cookies: List<HttpCookie>) {
        if (domain.isBlank() || cookies.isEmpty()) return
        viewModelScope.launch {
            @Suppress("DEPRECATION")
            repository.saveSession(domain, cookies)
        }
    }
}