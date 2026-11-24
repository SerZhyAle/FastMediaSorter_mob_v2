package com.sza.fastmediasorter.ui.share.helpers

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.AuthAccountDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.util.showBoundToHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant

class AccountSelectionManager(
    private val repository: AuthSessionRepository,
) {

    /**
     * A record without its host's session cookie is left out before the size branch (S4876): it cannot
     * sign a download in, so it must neither fill the picker nor win the single-record auto-select.
     * The settings list keeps it, so the owner can still remove it there.
     */
    suspend fun selectAccount(
        host: String,
        activity: AppCompatActivity,
        onSelected: (AuthAccountDomain) -> Unit,
        onNoneAvailable: () -> Unit,
        onCancelled: () -> Unit,
        onAddAccount: (() -> Unit)? = null,
    ) {
        val accounts = repository.listAccountsForHost(host).filter { !it.isDismissed && it.hasSessionCookie }
        Timber.i("AccountSelectionManager: host=%s accounts=%d", host, accounts.size)
        when {
            accounts.isEmpty() -> onNoneAvailable()
            accounts.size == 1 -> {
                val selected = accounts.first()
                markSelected(selected)
                onSelected(selected)
            }
            else -> showPicker(host, accounts, activity, onSelected, onCancelled, onAddAccount)
        }
    }

    private fun showPicker(
        host: String,
        accounts: List<AuthAccountDomain>,
        activity: AppCompatActivity,
        onSelected: (AuthAccountDomain) -> Unit,
        onCancelled: () -> Unit,
        onAddAccount: (() -> Unit)?,
    ) {
        val defaultIndex = run {
            val lastUsedIndex = accounts.indexOfFirst { it.lastUsedAt != null }
                .takeIf { index ->
                    if (index < 0) return@takeIf false
                    val candidate = accounts[index].lastUsedAt ?: Instant.MIN
                    accounts.all { (it.lastUsedAt ?: Instant.MIN) <= candidate }
                }
            lastUsedIndex ?: 0
        }
        val lastUsedLabel = activity.getString(R.string.s0155_pick_account_last_used)
        val accountLabels = accounts.mapIndexed { index, account ->
            if (index == defaultIndex) "${account.displayName} $lastUsedLabel" else account.displayName
        }
        // The add row sits after every account, so its index equals accounts.size and it is never the preselection.
        val labels = if (onAddAccount != null) {
            accountLabels + activity.getString(R.string.s4581_pick_account_add_new)
        } else {
            accountLabels
        }.toTypedArray()

        var selectedIndex = defaultIndex
        Timber.i("AccountSelectionManager: picker shown count=%d host=%s", accounts.size, host)
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.s0155_pick_account_title, host))
            .setSingleChoiceItems(labels, defaultIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                if (onAddAccount != null && selectedIndex == accounts.size) {
                    Timber.i("AccountSelectionManager: user chose add account host=%s", host)
                    Timber.d("S4581: picker add row chosen host=%s", host)
                    onAddAccount()
                    return@setPositiveButton
                }
                val chosen = accounts[selectedIndex]
                Timber.i("AccountSelectionManager: user selected host=%s accountId=%s", host, chosen.accountId)
                activity.lifecycleScope.launch {
                    markSelected(chosen)
                    onSelected(chosen)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancelled() }
            .setOnCancelListener { onCancelled() }
            .showBoundToHost(activity)
    }

    private suspend fun markSelected(account: AuthAccountDomain) {
        try {
            // Selection itself defines recency; the next picker must not depend on download success.
            repository.markLastUsed(account.host, account.accountId)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            Timber.w(
                throwable,
                "AccountSelectionManager: failed to mark selected account last-used host=%s accountId=%s",
                account.host,
                account.accountId,
            )
        }
    }
}
