package com.sza.fastmediasorter.ui.settings.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.data.link.auth.KnownAuthResource
import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
import com.sza.fastmediasorter.domain.repository.AuthAccountDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthHostGroup(
    val host: String,
    val resource: KnownAuthResource?,
    val accounts: List<AuthAccountDomain>,
)

@HiltViewModel
class AuthSessionsListViewModel @Inject constructor(
    private val repository: AuthSessionRepository,
) : ViewModel() {

    val accountGroups: Flow<List<AuthHostGroup>> = repository.observeAccountsAll()
        .map { accounts ->
            accounts
                .groupBy { it.host }
                .entries
                .map { (host, groupedAccounts) ->
                    AuthHostGroup(
                        host = host,
                        resource = KnownAuthResources.matchHost(host),
                        accounts = groupedAccounts,
                    )
                }
        }

    fun deleteAccount(host: String, accountId: String) {
        viewModelScope.launch { repository.deleteAccount(host, accountId) }
    }

    fun updateDisplayName(host: String, accountId: String, newName: String) {
        viewModelScope.launch { repository.updateDisplayName(host, accountId, newName) }
    }

    fun addAccount(loginUrl: String, onLaunchWebView: (url: String) -> Unit) {
        onLaunchWebView(loginUrl)
    }

}