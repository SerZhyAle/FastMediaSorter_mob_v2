package com.sza.fastmediasorter.ui.share.helpers

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.AuthAccountDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import timber.log.Timber
import java.time.Instant

/**
 * S0155: manages account selection before the link-download pipeline runs.
 *
 * Selection rules (strategic §5.1 pillar B + ADR-2):
 * - 0 accounts → [onNoneAvailable] (caller offers auth).
 * - 1 account  → selected silently; [onSelected] invoked immediately.
 * - ≥2 accounts → account picker dialog shown; default = last used.
 */
class AccountSelectionManager(
    private val repository: AuthSessionRepository,
) {
    /**
     * Resolves the account to use for [host] and notifies the caller via the
     * appropriate callback. All callbacks are invoked on the main thread.
     */
    suspend fun selectAccount(
        host: String,
        activity: AppCompatActivity,
        onSelected: (AuthAccountDomain) -> Unit,
        onNoneAvailable: () -> Unit,
        onCancelled: () -> Unit,
    ) {
        val accounts = repository.listAccountsForHost(host)
        Timber.i("AccountSelectionManager: host=%s accounts=%d", host, accounts.size)
        when {
            accounts.isEmpty() -> {
                Timber.i("AccountSelectionManager: no accounts — offering auth host=%s", host)
                onNoneAvailable()
            }
            accounts.size == 1 -> {
                Timber.i(
                    "AccountSelectionManager: single account selected host=%s accountId=%s",
                    host,
                    accounts[0].accountId,
                )
                onSelected(accounts[0])
            }
            else -> showPicker(host, accounts, activity, onSelected, onCancelled)
        }
    }

    private fun showPicker(
        host: String,
        accounts: List<AuthAccountDomain>,
        activity: AppCompatActivity,
        onSelected: (AuthAccountDomain) -> Unit,
        onCancelled: () -> Unit,
    ) {
        val defaultIndex = run {
            val lastUsedIndex = accounts.indexOfFirst { it.lastUsedAt != null }
                .takeIf { idx ->
                    if (idx < 0) return@takeIf false
                    val candidate = accounts[idx].lastUsedAt ?: Instant.MIN
                    accounts.all { (it.lastUsedAt ?: Instant.MIN) <= candidate }
                }
            lastUsedIndex ?: 0
        }
        val lastUsedLabel = activity.getString(R.string.s0155_pick_account_last_used)
        val labels = accounts.mapIndexed { i, acc ->
            if (i == defaultIndex) "${acc.displayName} $lastUsedLabel" else acc.displayName
        }.toTypedArray()

        var selectedIndex = defaultIndex
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.s0155_pick_account_title, host))
            .setSingleChoiceItems(labels, defaultIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val chosen = accounts[selectedIndex]
                Timber.i(
                    "AccountSelectionManager: user selected host=%s accountId=%s",
                    host,
                    chosen.accountId,
                )
                onSelected(chosen)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancelled() }
            .setOnCancelListener { onCancelled() }
            .show()
    }
}
