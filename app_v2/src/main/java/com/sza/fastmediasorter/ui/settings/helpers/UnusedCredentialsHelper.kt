package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.CredentialAuditEntry
import com.sza.fastmediasorter.domain.usecase.CredentialAuditor
import com.sza.fastmediasorter.domain.usecase.DeleteUnusedCredentialsUseCase
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S1649: shows how many stored credentials are past their grace period and offers to remove them.
 *
 * The row is hidden while the count is zero. A permanently visible "0 unused credentials" line would
 * be noise on a screen the user opened for something else, and the count is only ever interesting
 * when it is not zero.
 *
 * The count comes from [CredentialAuditor], the one definition of "orphaned" in the app - the same
 * audit the background worker reports from. A second query here would be the third answer to one
 * question, which is what this ticket exists to remove.
 */
class UnusedCredentialsHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val fragment: Fragment,
    private val credentialAuditor: CredentialAuditor,
    private val deleteUnusedCredentialsUseCase: DeleteUnusedCredentialsUseCase
) {

    /**
     * The entries the last audit marked deletable. Held so the confirmation can name them without
     * re-running the audit between the tap and the dialog - the use case re-checks eligibility
     * itself before deleting anything, so a list that went stale in between cannot delete too much.
     */
    private var eligible: List<CredentialAuditEntry> = emptyList()

    /**
     * Starts rendering the live count. Safe to call once, from the fragment's view setup.
     */
    fun bind() {
        // flowOn(IO) rather than trusting the collector's context: auditAsFlow re-reads the orphan
        // set on every emission, and the collector here is the view lifecycle scope on the main
        // thread. Room's suspend queries dispatch themselves, but the mapping around them should not
        // depend on that to stay off the main thread.
        fragment.collectOnLifecycle(credentialAuditor.auditAsFlow().flowOn(Dispatchers.IO)) { report ->
            eligible = report.entries.filter { it.eligibleForCleanup }
            render(eligible.size)
        }
        binding.btnUnusedCredentials.setOnClickListener { confirmDeletion() }
    }

    private fun render(eligibleCount: Int) {
        Timber.d("S1649: settings row rendered, eligible=$eligibleCount")
        val button = binding.btnUnusedCredentials
        button.isVisible = eligibleCount > 0
        if (eligibleCount > 0) {
            button.text = fragment.getString(R.string.settings_unused_credentials_button, eligibleCount)
        }
    }

    /**
     * Names every credential that would go, then asks.
     *
     * The list carries address, port and user name - never the password and never the credential id.
     * A confirmation that says only "3 credentials" cannot be judged by the person answering it, and
     * one that shows the secret defeats the point of storing it encrypted.
     */
    private fun confirmDeletion() {
        val doomed = eligible
        if (doomed.isEmpty()) {
            return
        }
        val body = fragment.getString(R.string.settings_unused_credentials_dialog_body) +
            "\n\n" + doomed.joinToString(separator = "\n") { it.label }
        MaterialAlertDialogBuilder(
            fragment.requireContext(),
            R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive
        )
            .setTitle(R.string.settings_unused_credentials_dialog_title)
            .setMessage(body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> delete() }
            // showBoundTo, not show: a bare dialog outlives the fragment that opened it and leaks
            // its window on rotation or backgrounding (S1456).
            .showBoundTo(fragment)
    }

    private fun delete() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) { deleteUnusedCredentialsUseCase.deleteAllEligible() }
            Timber.d("S1649: deletion confirmed, deleted=$deleted")
            Toast.makeText(
                fragment.requireContext(),
                fragment.getString(R.string.settings_unused_credentials_deleted, deleted),
                Toast.LENGTH_SHORT
            ).show()
            // The row's count is not refreshed here on purpose: the audit flow from bind() emits
            // again when the credentials table changes, and a manual re-read would be a second
            // answer that can disagree with it.
        }
    }
}
