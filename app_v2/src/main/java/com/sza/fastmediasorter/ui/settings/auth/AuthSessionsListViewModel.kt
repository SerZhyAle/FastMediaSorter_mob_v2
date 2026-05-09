package com.sza.fastmediasorter.ui.settings.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.repository.AuthSessionDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S0116 §5.1 pillar K: VM driving the saved-authorizations settings sub-screen.
 */
@HiltViewModel
class AuthSessionsListViewModel @Inject constructor(
    private val repository: AuthSessionRepository,
) : ViewModel() {

    val sessions: Flow<List<AuthSessionDomain>> = repository.observeDomains()

    fun delete(host: String) {
        viewModelScope.launch { repository.deleteSession(host) }
    }
}
