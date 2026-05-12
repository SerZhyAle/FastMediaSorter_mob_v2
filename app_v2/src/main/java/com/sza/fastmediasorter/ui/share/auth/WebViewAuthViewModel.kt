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

    fun saveSession(host: String, accountId: String, displayName: String, cookies: List<HttpCookie>) {
        if (host.isBlank() || accountId.isBlank() || cookies.isEmpty()) return
        viewModelScope.launch {
            repository.saveSession(host, accountId, displayName, cookies)
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