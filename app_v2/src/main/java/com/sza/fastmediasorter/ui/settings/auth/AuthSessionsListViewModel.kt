package com.sza.fastmediasorter.ui.settings.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.data.link.auth.KnownAuthResource
import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
import com.sza.fastmediasorter.domain.repository.AuthAccountDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S0155: groups saved accounts by host for the Settings "Saved authorizations" screen.
 * Previously exposed a flat [AuthSessionDomain] list (S0116); now uses [AuthAccountDomain].
 */
data class AuthHostGroup(
    val host: String,
    /** Non-null when [host] matches a [KnownAuthResources] entry. */
    val resource: KnownAuthResource?,
    val accounts: List<AuthAccountDomain>,
)

@HiltViewModel
class AuthSessionsListViewModel @Inject constructor(
    private val repository: AuthSessionRepository,
) : ViewModel() {

    /** Live stream of accounts grouped by host, preserving repository activity ordering. */
    val accountGroups: Flow<List<AuthHostGroup>> = repository.observeAccountsAll()
        .map { accounts ->
            accounts
                .groupBy { it.host }
                .entries
                .map { (host, accs) ->
                    AuthHostGroup(
                        host = host,
                        resource = KnownAuthResources.matchHost(host),
                        accounts = accs,
                    )
                }
        }

    fun deleteAccount(host: String, accountId: String) {
        viewModelScope.launch { repository.deleteAccount(host, accountId) }
    }

    fun updateDisplayName(host: String, accountId: String, newName: String) {
        viewModelScope.launch { repository.updateDisplayName(host, accountId, newName) }
    }

    /**
     * The UI side launches [WebViewAuthDialogFragment] with [loginUrl]; this method
     * exists so the fragment stays as a thin shell (routing logic in the ViewModel).
     */
    fun addAccount(loginUrl: String, onLaunchWebView: (url: String) -> Unit) {
        onLaunchWebView(loginUrl)
    }

    // ── Deprecated stubs (S0116 callers; replaced by S0155 account-scoped API) ─

    @Deprecated("Use accountGroups", ReplaceWith("accountGroups"))
    val sessions: Flow<List<AuthSessionDomain>> = repository.observeDomains()

    @Deprecated("Use deleteAccount(host, accountId)")
    fun delete(host: String) {
        viewModelScope.launch { @Suppress("DEPRECATION") repository.deleteSession(host) }
    }
}
