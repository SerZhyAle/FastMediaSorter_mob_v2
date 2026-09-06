package com.sza.fastmediasorter.ui.settings.helpers

import androidx.core.view.isVisible
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import timber.log.Timber

/**
 * S2516: owns the sub-program switches on the Operations tab - the calculator, the network monitor,
 * system information, the embedded game, both screen lights and the mirror.
 *
 * They moved out of `OperationsSettingsFragment` because adding the water flashlight's row took that
 * class past detekt's `LargeClass` ceiling. The group is the natural seam rather than an arbitrary
 * slice: every row here answers one question - is this sub-program offered to the user - and each is
 * one boolean on [AppSettings] with no other effect, which is why [render] can treat all seven the
 * same way.
 *
 * The network monitor is the one row that can be absent from a build, so its availability arrives as a
 * constructor value the way [OperationsCaptureManager] takes the screen-recording one, rather than by
 * this manager reaching for the contract itself.
 */
class OperationsProgramsManager(
    private val binding: FragmentSettingsDestinationsBinding,
    private val viewModel: SettingsViewModel,
    private val networkMonitorAvailableInBuild: Boolean,
    private val isUpdatingFromSettings: () -> Boolean,
) {

    fun setup() {
        binding.rowEnableCalculator.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableCalculator = isChecked))
        }
        binding.rowEnableStopwatch.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableStopwatch = isChecked))
        }
        binding.rowEnableNetworkMonitor.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableNetworkMonitor = isChecked))
        }
        binding.rowEnableSystemInfo.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableSystemInfo = isChecked))
        }
        binding.rowEmbeddedGame.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateEmbeddedGameEnabled(isChecked)
        }
        binding.rowFrontFlashlight.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            Timber.d("front flashlight toggle -> $isChecked")
            viewModel.updateSettings(viewModel.settings.value.copy(frontFlashlightEnabled = isChecked))
        }
        binding.rowWaterFlashlight.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            Timber.d("water flashlight toggle -> $isChecked")
            viewModel.updateSettings(viewModel.settings.value.copy(waterFlashlightEnabled = isChecked))
        }
        binding.rowMirror.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            Timber.d("mirror toggle -> $isChecked")
            viewModel.updateSettings(viewModel.settings.value.copy(mirrorEnabled = isChecked))
        }
    }

    fun render(settings: AppSettings) {
        if (binding.rowEnableCalculator.isChecked != settings.enableCalculator) {
            binding.rowEnableCalculator.setCheckedSilently(settings.enableCalculator)
        }
        // No visibility line: S1411 §6.5 makes the stopwatch universal across flavors, so unlike the
        // Monitor below the row is never absent from a build.
        if (binding.rowEnableStopwatch.isChecked != settings.enableStopwatch) {
            binding.rowEnableStopwatch.setCheckedSilently(settings.enableStopwatch)
        }
        binding.rowEnableNetworkMonitor.isVisible = networkMonitorAvailableInBuild
        if (binding.rowEnableNetworkMonitor.isChecked != settings.enableNetworkMonitor) {
            binding.rowEnableNetworkMonitor.setCheckedSilently(settings.enableNetworkMonitor)
        }
        // No visibility line, unlike the Monitor above: system information is compiled into every
        // flavor, so the row is never absent from a build.
        if (binding.rowEnableSystemInfo.isChecked != settings.enableSystemInfo) {
            binding.rowEnableSystemInfo.setCheckedSilently(settings.enableSystemInfo)
        }
        if (binding.rowEmbeddedGame.isChecked != settings.embeddedGameEnabled) {
            binding.rowEmbeddedGame.setCheckedSilently(settings.embeddedGameEnabled)
        }
        if (binding.rowFrontFlashlight.isChecked != settings.frontFlashlightEnabled) {
            binding.rowFrontFlashlight.setCheckedSilently(settings.frontFlashlightEnabled)
        }
        if (binding.rowWaterFlashlight.isChecked != settings.waterFlashlightEnabled) {
            binding.rowWaterFlashlight.setCheckedSilently(settings.waterFlashlightEnabled)
        }
        if (binding.rowMirror.isChecked != settings.mirrorEnabled) {
            binding.rowMirror.setCheckedSilently(settings.mirrorEnabled)
        }
    }
}
