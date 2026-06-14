package com.sza.fastmediasorter.ui.settings.helpers

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.ui.common.DeviceProfileUi
import com.sza.fastmediasorter.ui.profile.DeviceProfilePickerDialogFragment
import com.sza.fastmediasorter.ui.settings.SettingsProfileViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.launch

/**
 * S0327: owns the "Device Profile" row in Settings -> General.
 *
 * Keeps the row text in sync with the current device profile and opens the shared device-profile
 * picker on click. The picker surfaces the overwrite warning (warnOnApply = true); on confirm the
 * chosen profile is applied through [SettingsProfileViewModel].
 */
class GeneralSettingsProfileHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: SettingsProfileViewModel,
    private val fragment: Fragment
) {

    fun setup() {
        fragment.childFragmentManager.setFragmentResultListener(
            DeviceProfilePickerDialogFragment.RESULT_KEY, fragment.viewLifecycleOwner
        ) { _, bundle ->
            val name = bundle.getString(DeviceProfilePickerDialogFragment.RESULT_PROFILE)
                ?: return@setFragmentResultListener
            runCatching { DeviceProfileType.valueOf(name) }.getOrNull()
                ?.let { type ->
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        if (!viewModel.shouldConfirmAllFilesProvision(type)) {
                            viewModel.saveProfile(type)
                            return@launch
                        }

                        AlertDialog.Builder(fragment.requireContext())
                            .setTitle(R.string.settings_all_files_profile_confirm_title)
                            .setMessage(R.string.settings_all_files_profile_confirm_message)
                            .setPositiveButton(R.string.yes) { _, _ ->
                                viewModel.saveProfile(type, ensureAllFilesResource = true)
                            }
                            .setNegativeButton(R.string.no, null)
                            .show()
                    }
                }
        }

        binding.rowDeviceProfile.setOnClickListener {
            val current = viewModel.currentProfile.value?.type ?: DeviceProfileType.PERSONAL_SMARTPHONE
            DeviceProfilePickerDialogFragment.newInstance(
                current = current,
                recommended = null,
                warnOnApply = true,
            ).show(fragment.childFragmentManager, DeviceProfilePickerDialogFragment.TAG)
        }

        fragment.viewLifecycleOwner.collectOnLifecycle(viewModel.currentProfile) { currentProfile ->
            currentProfile?.let { profile ->
                binding.tvDeviceProfileValue.text = fragment.getString(DeviceProfileUi.titleRes(profile.type))
            }
        }
    }
}
