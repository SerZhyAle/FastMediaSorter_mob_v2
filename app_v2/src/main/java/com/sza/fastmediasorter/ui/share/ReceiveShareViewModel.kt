package com.sza.fastmediasorter.ui.share

import androidx.lifecycle.ViewModel
import com.sza.fastmediasorter.domain.repository.AuthAccountDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/**
 * S1329: the auth-offer decisions the share target used to make by calling the repository from the
 * Activity. Each function is named for the question the host is actually asking, so the host never
 * holds the repository and never re-implements the "newest usable account" rule three times over.
 *
 * The host wraps several of these calls in its own `runCatching`; that stays where it is, because the
 * recovery it chooses differs per site (skip the offer, fall back to no account, treat as not dismissed).
 */
@HiltViewModel
class ReceiveShareViewModel @Inject constructor(
    private val authSessionRepository: AuthSessionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** True when the user chose "don't ask again" for this host. */
    suspend fun isHostDismissed(host: String): Boolean =
        authSessionRepository.isDismissedForHost(host)

    /** Records the "don't ask again" choice, so the escalation path stops offering for this host. */
    suspend fun dismissHost(host: String) = authSessionRepository.markDismissed(host)

    /**
     * The account whose name the offer dialog should carry: most recently used, not dismissed, and
     * holding cookies. Null means no such record, and the dialog falls back to its generic wording.
     */
    suspend fun namedAccountForOffer(host: String): AuthAccountDomain? =
        authSessionRepository.listAccountsForHost(host)
            .filter { !it.isDismissed && it.cookieCount > 0 }
            .maxByOrNull { it.lastUsedAt ?: Instant.MIN }

    /** The account id a download should run under: most recently used and not dismissed. */
    suspend fun accountIdForDownload(host: String): String? =
        authSessionRepository.listAccountsForHost(host)
            .filter { !it.isDismissed }
            .maxByOrNull { it.lastUsedAt ?: Instant.MIN }
            ?.accountId

    /** True when any non-dismissed account exists, which is what "already signed in" means here. */
    suspend fun hasUsableAccount(host: String): Boolean =
        authSessionRepository.listAccountsForHost(host).any { !it.isDismissed }

    /** Whether a shared link may start a download on its own. */
    suspend fun isLinkAutoDownloadEnabled(): Boolean =
        settingsRepository.getSettings().first().linkAutoDownloadEnabled
}
