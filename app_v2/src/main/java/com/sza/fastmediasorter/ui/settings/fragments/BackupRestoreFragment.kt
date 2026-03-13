package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsBackupRestoreBinding
import com.sza.fastmediasorter.domain.usecase.RestoreFromGoogleDriveUseCase
import com.sza.fastmediasorter.ui.settings.BackupRestoreUiState
import com.sza.fastmediasorter.ui.settings.BackupRestoreViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class BackupRestoreFragment : Fragment() {

    private var _binding: FragmentSettingsBackupRestoreBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BackupRestoreViewModel by viewModels()

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(com.google.android.gms.common.api.ApiException::class.java)
        } catch (e: com.google.android.gms.common.api.ApiException) {
            // Result delivery failed (e.g. 12502 race on emulator), but user may have
            // actually completed sign-in — check the current account before giving up.
            Timber.w("Sign-in result parsing failed (code=${e.statusCode}), falling back to last signed-in account")
            GoogleSignIn.getLastSignedInAccount(requireContext())
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error parsing Google sign-in result")
            GoogleSignIn.getLastSignedInAccount(requireContext())
        }
        viewModel.handleSignInResult(account)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBackupRestoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        observeState()
        updateAccountInfo()
    }

    private fun setupButtons() {
        binding.btnBackup.setOnClickListener {
            viewModel.startBackup()
        }
        binding.btnRestore.setOnClickListener {
            viewModel.startRestore()
        }
        binding.iconHelpBackupInfo.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_backup_info_title,
                R.string.tooltip_backup_info_message
            )
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    handleState(state)
                }
            }
        }
    }

    private fun handleState(state: BackupRestoreUiState) {
        // Reset visibility
        binding.progressBackup.visibility = View.GONE
        binding.progressRestore.visibility = View.GONE
        binding.btnBackup.isEnabled = true
        binding.btnRestore.isEnabled = true

        when (state) {
            is BackupRestoreUiState.Idle -> { /* default state */ }

            is BackupRestoreUiState.Authenticating -> {
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }

            is BackupRestoreUiState.NeedsSignIn -> {
                launchSignIn()
            }

            is BackupRestoreUiState.BackingUp -> {
                binding.progressBackup.visibility = View.VISIBLE
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }

            is BackupRestoreUiState.FetchingInfo -> {
                binding.progressRestore.visibility = View.VISIBLE
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }

            is BackupRestoreUiState.Restoring -> {
                binding.progressRestore.visibility = View.VISIBLE
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }

            is BackupRestoreUiState.BackupSuccess -> {
                showSnackbar(getString(R.string.backup_success, state.resourceCount, state.accountEmail))
                updateAccountInfo()
                viewModel.resetState()
            }

            is BackupRestoreUiState.BackupInfoReady -> {
                showRestoreConfirmDialog(state.info)
            }

            is BackupRestoreUiState.RestoreSuccess -> {
                showRestoreSuccessMessage(state.result)
                viewModel.resetState()
            }

            is BackupRestoreUiState.Error -> {
                showSnackbar(state.message)
                viewModel.resetState()
            }
        }
    }

    private fun launchSignIn() {
        try {
            val intent = viewModel.getSignInIntent()
            signInLauncher.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch Google sign-in")
            showSnackbar(getString(R.string.backup_failed, e.message ?: "Unknown error"))
        }
    }

    private fun showRestoreConfirmDialog(info: RestoreFromGoogleDriveUseCase.BackupInfo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restore_confirm_title)
            .setMessage(getString(R.string.restore_confirm_message, info.createdAt, info.deviceModel, info.resourceCount))
            .setPositiveButton(R.string.restore_from_google_drive) { _, _ ->
                viewModel.confirmRestore()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                viewModel.resetState()
            }
            .setOnCancelListener {
                viewModel.resetState()
            }
            .show()
    }

    private fun showRestoreSuccessMessage(result: RestoreFromGoogleDriveUseCase.RestoreResult) {
        showSnackbar(getString(R.string.restore_success, result.resourcesAdded, result.resourcesSkipped))
        if (result.resourcesNeedingAuth > 0) {
            Snackbar.make(binding.root, getString(R.string.restore_needs_auth), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun updateAccountInfo() {
        val email = viewModel.getAccountEmail()
        if (email != null) {
            binding.layoutBackupInfo.visibility = View.VISIBLE
            binding.tvBackupAccount.text = getString(R.string.backup_account, email)
        } else {
            binding.layoutBackupInfo.visibility = View.GONE
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
