package com.sza.fastmediasorter.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.databinding.DialogLauncherSettingsBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S1088: hosts the system-launcher configuration rows (taskbar composition + grid density + change-home)
 * that used to sit in the Operations settings "System launcher" group. The enable toggle stays in
 * General settings; this dialog is only reachable from that toggle's neighbouring entry row and from the
 * launcher Start menu, both gated by [LauncherModeContract.isAvailableInBuild]. Every control applies
 * immediately (dismiss-to-close), reusing the same [SettingsViewModel] writes as the former inline block -
 * no row logic is duplicated.
 */
@AndroidEntryPoint
class LauncherSettingsDialogFragment : DialogFragment() {

    private var _binding: DialogLauncherSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: SettingsViewModel by activityViewModels()

    @Inject
    lateinit var launcherModeContract: LauncherModeContract

    @Inject
    lateinit var launcherRoleManager: LauncherRoleManager

    // Guards render() writes so setCheckedSilently / setSelection never bounce back into a settings update.
    private var isUpdatingFromSettings = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogLauncherSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Defensive: the dialog is never opened on a build without the launcher surface, but never leave a
        // functional launcher UI reachable if a caller slips through the gate.
        if (!launcherModeContract.isAvailableInBuild) {
            dismiss()
            return
        }
        binding.btnClose.setOnClickListener { dismiss() }
        setupRows()
        observeSettings()
    }

    private fun setupRows() {
        binding.rowLauncherShowRecents.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherTaskbarShowRecents = isChecked))
        }
        binding.rowLauncherShowPinned.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherTaskbarShowPinned = isChecked))
        }
        binding.rowLauncherShowTray.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherTaskbarShowTray = isChecked))
        }
        binding.rowLauncherDensity.setEntries(
            listOf(
                getText(R.string.launcher_settings_density_sparse),
                getText(R.string.launcher_settings_density_default),
                getText(R.string.launcher_settings_density_dense),
                getText(R.string.launcher_settings_density_densest),
            )
        )
        binding.rowLauncherDensity.setOnItemSelectedListener { index ->
            if (isUpdatingFromSettings) return@setOnItemSelectedListener
            val options = AppSettings.LAUNCHER_DENSITY_OPTIONS
            val factor = options.getOrElse(index) { options[DENSITY_DEFAULT_INDEX] }
            viewModel.updateSettings(viewModel.settings.value.copy(launcherDensityFactor = factor))
        }
        binding.rowLauncherOpenHomeSettings.setOnClickListener {
            val host = activity ?: return@setOnClickListener
            launcherRoleManager.openHomeChooser(host)
        }
    }

    private fun observeSettings() {
        collectOnLifecycle(viewModel.settings) { settings ->
            isUpdatingFromSettings = true
            binding.rowLauncherShowRecents.setCheckedSilently(settings.launcherTaskbarShowRecents)
            binding.rowLauncherShowPinned.setCheckedSilently(settings.launcherTaskbarShowPinned)
            binding.rowLauncherShowTray.setCheckedSilently(settings.launcherTaskbarShowTray)
            val densityIndex = AppSettings.LAUNCHER_DENSITY_OPTIONS.indexOf(settings.launcherDensityFactor)
            binding.rowLauncherDensity.setSelection(if (densityIndex >= 0) densityIndex else DENSITY_DEFAULT_INDEX)
            isUpdatingFromSettings = false
        }
    }

    override fun onStart() {
        super.onStart()
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
        const val TAG = "LauncherSettingsDialogFragment"

        // Standard density (1.0f) sits at index 1 of AppSettings.LAUNCHER_DENSITY_OPTIONS.
        private const val DENSITY_DEFAULT_INDEX = 1
    }
}
