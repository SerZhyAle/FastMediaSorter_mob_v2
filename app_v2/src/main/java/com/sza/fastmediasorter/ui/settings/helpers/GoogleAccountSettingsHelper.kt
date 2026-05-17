package com.sza.fastmediasorter.ui.settings.helpers

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.browser.CctAvailabilityChecker
import com.sza.fastmediasorter.domain.identity.IdentityFailureReason
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import com.sza.fastmediasorter.ui.settings.GoogleAccountSettingsViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S0200 Phase 06 — wires the "Google Account" Settings card to its ViewModel.
 *
 * Binds card children to the [GoogleAccountSettingsViewModel.uiState] Flow, dispatches
 * action button clicks, shows the sign-out confirmation dialog, renders the diagnostics line.
 */
class GoogleAccountSettingsHelper(
    private val fragment: Fragment,
    private val viewModel: GoogleAccountSettingsViewModel,
    private val cctChecker: CctAvailabilityChecker
) {

    fun bind(cardView: View) {
        val tvSummary = cardView.findViewById<TextView>(R.id.tvAccountSummary)
        val tvEmail = cardView.findViewById<TextView>(R.id.tvAccountEmail)
        val ivAvatar = cardView.findViewById<ImageView>(R.id.ivAccountAvatar)
        val btnAction = cardView.findViewById<Button>(R.id.btnAccountAction)
        val progress = cardView.findViewById<View>(R.id.progressAccountAuth)
        val tvDiag = cardView.findViewById<TextView>(R.id.tvDiagnosticsLine)
        val btnDiagToggle = cardView.findViewById<Button>(R.id.btnDiagnosticsToggle)

        // S0234: route both card buttons through UserActionLogger so clicks land in the log.
        btnDiagToggle.setOnClickListener(
            com.sza.fastmediasorter.utils.UserActionLogger.wrapClickListener(
                buttonName = "GoogleAccountDiagnosticsToggle",
                context = "Settings",
                listener = View.OnClickListener { viewModel.toggleDiagnostics() }
            )
        )

        var currentState: PrimaryGoogleAccountState = PrimaryGoogleAccountState.Unbound
        btnAction.setOnClickListener(
            com.sza.fastmediasorter.utils.UserActionLogger.wrapClickListener(
                buttonName = "GoogleAccountAction",
                context = "Settings",
                listener = View.OnClickListener { onActionClicked(currentState) }
            )
        )

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { ui ->
                    currentState = ui.state
                    render(ui, tvSummary, tvEmail, ivAvatar, btnAction, progress, tvDiag)
                }
            }
        }

        // S0234: one-shot error dialog on each transition INTO Error state (Decision D2).
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is GoogleAccountSettingsViewModel.Event.ShowSignInError -> showSignInErrorDialog(event.reason)
                    }
                }
            }
        }
    }

    // S0234: reuse the existing ErrorDialog (title + message + collapsible details + copy/share/save).
    private fun showSignInErrorDialog(reason: IdentityFailureReason) {
        Timber.d("S0234: showSignInErrorDialog reason=$reason")
        val context = fragment.context ?: return
        val (summaryRes, ctaRes) = errorReasonStrings(reason)
        val message = context.getString(summaryRes)
        val details = context.getString(R.string.s0234_card_state_error_diag_details_prefix, reason.name)
        com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
            context = context,
            title = context.getString(R.string.s0234_error_dialog_title),
            message = message,
            details = details,
            actionButtonText = context.getString(ctaRes),
            onActionClick = { handleErrorAction(reason) }
        )
    }

    private fun render(
        ui: GoogleAccountSettingsViewModel.UiState,
        tvSummary: TextView,
        tvEmail: TextView,
        ivAvatar: ImageView,
        btnAction: Button,
        progress: View,
        tvDiag: TextView
    ) {
        when (val state = ui.state) {
            PrimaryGoogleAccountState.Unbound -> {
                tvSummary.setText(R.string.s0200_card_state_unbound_summary)
                tvEmail.visibility = View.GONE
                ivAvatar.visibility = View.GONE
                progress.visibility = View.GONE
                btnAction.setText(R.string.s0200_card_state_unbound_cta)
                btnAction.isEnabled = true
            }
            PrimaryGoogleAccountState.Authenticating -> {
                tvSummary.setText(R.string.s0200_card_authenticating)
                progress.visibility = View.VISIBLE
                btnAction.isEnabled = false
            }
            is PrimaryGoogleAccountState.Bound -> {
                tvSummary.text = fragment.getString(
                    R.string.s0200_card_state_bound_summary,
                    state.account.email
                )
                tvEmail.text = state.account.displayName ?: state.account.email
                tvEmail.visibility = View.VISIBLE
                ivAvatar.visibility = if (state.account.photoUrl != null) View.VISIBLE else View.GONE
                progress.visibility = View.GONE
                btnAction.setText(R.string.s0200_card_state_bound_sign_out)
                btnAction.isEnabled = true
            }
            is PrimaryGoogleAccountState.NeedsResignIn -> {
                tvSummary.text = fragment.getString(
                    R.string.s0200_card_state_needs_resign_in_summary,
                    state.account.email
                )
                tvEmail.text = state.account.email
                tvEmail.visibility = View.VISIBLE
                ivAvatar.visibility = View.GONE
                progress.visibility = View.GONE
                btnAction.setText(R.string.s0200_card_state_needs_resign_in_cta)
                btnAction.isEnabled = true
            }
            is PrimaryGoogleAccountState.Error -> {
                val (summaryRes, ctaRes) = errorReasonStrings(state.cause)
                tvSummary.setText(summaryRes)
                tvEmail.visibility = View.GONE
                ivAvatar.visibility = View.GONE
                progress.visibility = View.GONE
                btnAction.setText(ctaRes)
                btnAction.isEnabled = true
            }
        }

        if (ui.showDiagnostics) {
            tvDiag.visibility = View.VISIBLE
            tvDiag.text = if (ui.cctPackage != null) {
                fragment.getString(R.string.s0200_card_diag_cct_ok, ui.cctPackage)
            } else {
                fragment.getString(R.string.s0200_card_diag_cct_missing)
            }
        } else {
            tvDiag.visibility = View.GONE
        }
    }

    private fun onActionClicked(currentState: PrimaryGoogleAccountState) {
        try {
            when (currentState) {
                PrimaryGoogleAccountState.Unbound -> viewModel.signInPrimary(fragment.requireActivity())
                is PrimaryGoogleAccountState.Bound -> confirmSignOut()
                is PrimaryGoogleAccountState.NeedsResignIn -> viewModel.reconnect(fragment.requireActivity())
                is PrimaryGoogleAccountState.Error -> handleErrorAction(currentState.cause)
                PrimaryGoogleAccountState.Authenticating -> Unit
            }
        } catch (e: Exception) {
            Timber.e(e, "GoogleAccountSettingsHelper: action click failed for state=$currentState")
        }
    }

    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.s0200_sign_out_confirm_title)
            .setMessage(R.string.s0200_sign_out_confirm_message)
            .setPositiveButton(R.string.s0200_sign_out_confirm_confirm) { _, _ -> viewModel.signOutPrimary() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // S0234: per-reason action click — PlayServicesOutdated routes to Play Store,
    // everything else retries signInPrimary (Decision D1).
    private fun handleErrorAction(reason: IdentityFailureReason) {
        Timber.d("S0234: handleErrorAction reason=$reason")
        when (reason) {
            IdentityFailureReason.PlayServicesOutdated -> openPlayServicesInPlayStore()
            else -> viewModel.signInPrimary(fragment.requireActivity())
        }
    }

    private fun openPlayServicesInPlayStore() {
        val context = fragment.requireContext()
        val marketUri = android.net.Uri.parse("market://details?id=com.google.android.gms")
        val webUri = android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, marketUri).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Timber.w(e, "GoogleAccountSettingsHelper: Play Store not available, falling back to web")
            try {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, webUri).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            } catch (e2: Exception) {
                Timber.e(e2, "GoogleAccountSettingsHelper: web fallback for Play Services also failed")
            }
        }
    }

    // S0234: per-reason copy mapping for the card summary + CTA pair (Decision D1).
    private fun errorReasonStrings(reason: IdentityFailureReason): Pair<Int, Int> = when (reason) {
        IdentityFailureReason.PlayServicesOutdated ->
            R.string.s0234_card_state_error_play_services_outdated_summary to
                R.string.s0234_card_state_error_play_services_outdated_cta
        IdentityFailureReason.NetworkError ->
            R.string.s0234_card_state_error_network_summary to
                R.string.s0234_card_state_error_network_cta
        IdentityFailureReason.UserCancelled ->
            R.string.s0234_card_state_error_user_cancelled_summary to
                R.string.s0234_card_state_error_user_cancelled_cta
        IdentityFailureReason.CctUnavailable ->
            R.string.s0234_card_state_error_cct_unavailable_summary to
                R.string.s0234_card_state_error_cct_unavailable_cta
        IdentityFailureReason.UnknownError ->
            R.string.s0234_card_state_error_unknown_summary to
                R.string.s0234_card_state_error_unknown_cta
    }
}
