package com.sza.fastmediasorter.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.DialogDefaultAppsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerSettingsManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S0880: hosts the default-player registration subgroup (per-type buttons + the primary-player /
 * accept-shared toggles) that used to sit inline in the Operations settings screen. Only reachable
 * from the "Set as default" launcher, which is itself gated by [MediaCapabilities.supportsDefaultPlayer],
 * so the dialog never needs its own top-level gate. The actual OS-registration logic is delegated to
 * [DefaultPlayerSettingsManager] (buttons) and [DefaultPlayerManager] (toggles) - no logic is duplicated.
 */
@AndroidEntryPoint
class DefaultAppsDialogFragment : DialogFragment() {

    private var _binding: DialogDefaultAppsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels()

    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

    private val defaultPlayerSettingsManager = DefaultPlayerSettingsManager()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogDefaultAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindContent()
        observeSettings()
    }

    private fun bindContent() {
        binding.btnClose.setOnClickListener { dismiss() }
        // Per-type registration buttons + hint (gated by media-type capability inside the manager).
        defaultPlayerSettingsManager.bind(this, binding, mediaCapabilities)
        setupToggles()
    }

    private fun setupToggles() {
        binding.rowPrimaryMediaPlayer.setOnCheckedChangeListener { isChecked ->
            DefaultPlayerManager.applyPrimaryPlayerState(requireContext(), isChecked, mediaCapabilities)
            viewModel.updateSettings(viewModel.settings.value.copy(isPrimaryMediaPlayer = isChecked))
        }
        binding.rowAcceptSharedFiles.setOnCheckedChangeListener { isChecked ->
            DefaultPlayerManager.applyShareReceiverState(requireContext(), isChecked, mediaCapabilities)
            viewModel.updateSettings(viewModel.settings.value.copy(acceptSharedFiles = isChecked))
        }
    }

    private fun observeSettings() {
        collectOnLifecycle(viewModel.settings) { renderToggles(it) }
    }

    // setCheckedSilently reflects the persisted value without re-firing the OS component-state write.
    private fun renderToggles(settings: AppSettings) {
        binding.rowPrimaryMediaPlayer.setCheckedSilently(settings.isPrimaryMediaPlayer)
        binding.rowAcceptSharedFiles.setCheckedSilently(settings.acceptSharedFiles)
    }

    override fun onStart() {
        super.onStart()
        applyDialogChrome()
    }

    private fun applyDialogChrome() {
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        // Settings panel: every control applies immediately, so there is no positive action - a no-op
        // confirm keeps Esc-dismiss and focus traversal without a false Enter-confirm.
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
        binding.btnClose.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DefaultAppsDialogFragment"
    }
}
