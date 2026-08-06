package com.sza.fastmediasorter.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.databinding.DialogLauncherSettingsBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
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

    // S1400: dialog-scoped, so the reset action does not have to be threaded through the shared
    // settings ViewModel's already-oversized constructor.
    private val launcherViewModel: LauncherSettingsViewModel by viewModels()

    @Inject
    lateinit var launcherModeContract: LauncherModeContract

    @Inject
    lateinit var launcherRoleManager: LauncherRoleManager

    // Guards render() writes so setCheckedSilently / setSelection never bounce back into a settings update.
    private var isUpdatingFromSettings = false

    /**
     * S1101: picks the desktop wallpaper image. The file is copied into private storage right away, so
     * no persistable Uri grant is taken - the picked document is read once and never referenced again.
     */
    private val pickWallpaperImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                // Cancelled: the row already moved to "My image", so put it back on the stored mode.
                renderWallpaperRow(viewModel.settings.value)
                return@registerForActivityResult
            }
            viewModel.applyLauncherWallpaperImage(uri)
        }

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
        setupTrayRows()
        binding.rowLauncherReplaceStatusArea.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(
                viewModel.settings.value.copy(launcherReplaceSystemStatusArea = isChecked)
            )
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
        binding.rowLauncherWallpaper.setEntries(
            listOf(
                getText(R.string.launcher_settings_wallpaper_branded),
                getText(R.string.launcher_settings_wallpaper_none),
                getText(R.string.launcher_settings_wallpaper_image),
            )
        )
        binding.rowLauncherWallpaper.setOnItemSelectedListener { index ->
            if (isUpdatingFromSettings) return@setOnItemSelectedListener
            val modes = AppSettings.LAUNCHER_WALLPAPER_MODES
            when (val mode = modes.getOrElse(index) { AppSettings.LAUNCHER_WALLPAPER_BRANDED }) {
                // Image mode only becomes real once a file is actually picked and copied, so the write
                // happens in the picker callback, not here.
                AppSettings.LAUNCHER_WALLPAPER_IMAGE -> pickWallpaperImage.launch(WALLPAPER_MIME_TYPES)
                else -> viewModel.applyLauncherWallpaperMode(mode)
            }
        }
        binding.rowLauncherLockDesktop.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherDesktopLocked = isChecked))
        }
        binding.rowLauncherOpenHomeSettings.setOnClickListener {
            val host = activity ?: return@setOnClickListener
            launcherRoleManager.openHomeChooser(host)
        }
        binding.btnResetLauncher.setOnClickListener { confirmReset() }
    }

    /** S1415: the six tray-composition rows, kept out of [setupRows] so neither function grows past its limit. */
    private fun setupTrayRows() {
        binding.rowLauncherTrayClock.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherTrayShowClock = isChecked))
        }
        binding.rowLauncherTrayBluetooth.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherTrayShowBluetooth = isChecked))
        }
        binding.rowLauncherTraySim1.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherTrayShowSim1 = isChecked))
        }
        binding.rowLauncherTraySim2.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherTrayShowSim2 = isChecked))
        }
        binding.rowLauncherTrayNetwork.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherTrayShowNetwork = isChecked))
        }
        binding.rowLauncherTrayBattery.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherTrayShowBattery = isChecked))
        }
    }

    /**
     * S1400: every other control here applies immediately, so the one destructive action is the only
     * one that asks first.
     */
    private fun confirmReset() {
        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive,
        )
            .setTitle(R.string.launcher_settings_reset_title)
            .setMessage(R.string.launcher_settings_reset_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Timber.d("S1400: launcher reset confirmed in the settings dialog")
                launcherViewModel.resetToDefaults()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun observeSettings() {
        collectOnLifecycle(viewModel.settings) { settings ->
            isUpdatingFromSettings = true
            binding.rowLauncherShowRecents.setCheckedSilently(settings.launcherTaskbarShowRecents)
            binding.rowLauncherShowPinned.setCheckedSilently(settings.launcherTaskbarShowPinned)
            binding.rowLauncherShowTray.setCheckedSilently(settings.launcherTaskbarShowTray)
            binding.rowLauncherTrayClock.setCheckedSilently(settings.launcherTrayShowClock)
            binding.rowLauncherTrayBluetooth.setCheckedSilently(settings.launcherTrayShowBluetooth)
            binding.rowLauncherTraySim1.setCheckedSilently(settings.launcherTrayShowSim1)
            binding.rowLauncherTraySim2.setCheckedSilently(settings.launcherTrayShowSim2)
            binding.rowLauncherTrayNetwork.setCheckedSilently(settings.launcherTrayShowNetwork)
            binding.rowLauncherTrayBattery.setCheckedSilently(settings.launcherTrayShowBattery)
            binding.rowLauncherReplaceStatusArea.setCheckedSilently(settings.launcherReplaceSystemStatusArea)
            binding.rowLauncherLockDesktop.setCheckedSilently(settings.launcherDesktopLocked)
            val densityIndex = AppSettings.LAUNCHER_DENSITY_OPTIONS.indexOf(settings.launcherDensityFactor)
            binding.rowLauncherDensity.setSelection(if (densityIndex >= 0) densityIndex else DENSITY_DEFAULT_INDEX)
            renderWallpaperRow(settings)
            isUpdatingFromSettings = false
        }
        collectOnLifecycle(launcherViewModel.resetResult) { succeeded ->
            Snackbar.make(
                binding.root,
                if (succeeded) {
                    R.string.launcher_settings_reset_success
                } else {
                    R.string.launcher_settings_reset_failed
                },
                Snackbar.LENGTH_LONG,
            ).show()
        }
        collectOnLifecycle(viewModel.launcherWallpaperImportFailed) {
            // The stored mode never changed, so the row has to be walked back off "My image" by hand.
            renderWallpaperRow(viewModel.settings.value)
            Snackbar.make(
                binding.root,
                R.string.launcher_settings_wallpaper_import_failed,
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    private fun renderWallpaperRow(settings: AppSettings) {
        val wasUpdating = isUpdatingFromSettings
        isUpdatingFromSettings = true
        val index = AppSettings.LAUNCHER_WALLPAPER_MODES.indexOf(settings.launcherWallpaperMode)
        binding.rowLauncherWallpaper.setSelection(if (index >= 0) index else 0)
        isUpdatingFromSettings = wasUpdating
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

        // S1101: stills and GIFs both arrive as image/*; the decoder picks the right path per file.
        private val WALLPAPER_MIME_TYPES = arrayOf("image/*")
    }
}
