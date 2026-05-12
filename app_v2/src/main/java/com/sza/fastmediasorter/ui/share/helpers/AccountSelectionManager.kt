package com.sza.fastmediasorter.ui.share.helpers

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.AuthAccountDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import timber.log.Timber
import java.time.Instant

class AccountSelectionManager(
    private val repository: AuthSessionRepository,
) {

    suspend fun selectAccount(
        host: String,
        activity: AppCompatActivity,
        onSelected: (AuthAccountDomain) -> Unit,
        onNoneAvailable: () -> Unit,
        onCancelled: () -> Unit,
    ) {
        val accounts = repository.listAccountsForHost(host).filter { !it.isDismissed }
        Timber.i("AccountSelectionManager: host=%s accounts=%d", host, accounts.size)
        when {
            accounts.isEmpty() -> onNoneAvailable()
            accounts.size == 1 -> onSelected(accounts.first())
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
                .takeIf { index ->
                    if (index < 0) return@takeIf false
                    val candidate = accounts[index].lastUsedAt ?: Instant.MIN
                    accounts.all { (it.lastUsedAt ?: Instant.MIN) <= candidate }
                }
            lastUsedIndex ?: 0
        }
        val lastUsedLabel = activity.getString(R.string.s0155_pick_account_last_used)
        val labels = accounts.mapIndexed { index, account ->
            if (index == defaultIndex) "${account.displayName} $lastUsedLabel" else account.displayName
        }.toTypedArray()

        var selectedIndex = defaultIndex
        Timber.i("[S0166] account picker shown: count=%d, host=%s", accounts.size, host)
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.s0155_pick_account_title, host))
            .setSingleChoiceItems(labels, defaultIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val chosen = accounts[selectedIndex]
                Timber.i("AccountSelectionManager: user selected host=%s accountId=%s", host, chosen.accountId)
                onSelected(chosen)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancelled() }
            .setOnCancelListener { onCancelled() }
            .show()
    }
}