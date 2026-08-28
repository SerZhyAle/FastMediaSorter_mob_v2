package com.sza.fastmediasorter.ui.settings

import android.Manifest
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.databinding.DialogLauncherResetConfirmBinding
import com.sza.fastmediasorter.databinding.DialogLauncherSettingsBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.usecase.launcher.IsCameraWallpaperAvailableUseCase
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLensEnumerationManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLensLabelFormatter
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // S2076: whether this device has a camera at all. Injected here rather than into the shared settings
    // ViewModel, whose constructor is already at its parameter ceiling - and the question is a UI one:
    // it decides which entries the wallpaper dropdown offers, not what gets written.
    @Inject
    lateinit var isCameraWallpaperAvailable: IsCameraWallpaperAvailableUseCase

    // S1422: the shared orchestrator already owns restore, animation and persistence of section state,
    // so this dialog only declares which rows belong together.
    private val sectionsManager by lazy { CollapsibleSectionsManager(requireContext()) }

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

    /**
     * S2076: the CAMERA grant for the live wallpaper. A refusal is not an error worth a message - the row
     * simply returns to the stored mode, exactly as a cancelled image pick does.
     */
    private val requestCameraForWallpaper =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) showCameraLensPicker() else renderWallpaperRow(viewModel.settings.value)
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
        Timber.d("S2017: launcher settings dialog opened")
        binding.btnClose.setOnClickListener { dismiss() }
        setupCollapsibleSections()
        setupRows()
        observeSettings()
    }

    /** S1422: only the top bar starts expanded - it is the group the launcher work keeps changing. */
    private fun setupCollapsibleSections() {
        sectionsManager.register(binding.headerLauncherTaskbar, binding.containerLauncherTaskbar, "launcher__taskbar")
        sectionsManager.register(
            binding.headerLauncherTopBar,
            binding.containerLauncherTopBar,
            "launcher__top_bar",
            defaultExpanded = true,
        )
        sectionsManager.register(binding.headerLauncherDesktop, binding.containerLauncherDesktop, "launcher__desktop")
        sectionsManager.register(binding.headerLauncherSystem, binding.containerLauncherSystem, "launcher__system")
        expandRequestedSection()
    }

    /**
     * S1466: unfolds the group the caller asked for, after every section restored its stored state -
     * the request has to win over that state, or a user who once folded the desktop group would open the
     * dialog from "Wallpaper" and see no wallpaper row.
     */
    private fun expandRequestedSection() {
        if (arguments?.getString(ARG_EXPAND_SECTION) != SECTION_DESKTOP) return
        binding.headerLauncherDesktop.setExpanded(true, notify = true)
    }

    private fun setupRows() {
        setupPlacementRow()
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
                viewModel.settings.value.copy(
                    launcherReplaceSystemStatusArea = isChecked,
                    // S1431: the mode has nowhere to draw without the freed band, so it is cleared with it
                    // rather than left stored as on and unreachable (strategic risk row 6).
                    launcherTopStatusStripMode = isChecked && viewModel.settings.value.launcherTopStatusStripMode,
                )
            )
        }
        binding.rowLauncherTopStatusStrip.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherTopStatusStripMode = isChecked))
        }
        binding.rowLauncherForeignNotifications.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(
                viewModel.settings.value.copy(launcherForeignNotificationsEnabled = isChecked)
            )
            // Turning it on without the system grant would leave a switch claiming to work, so the screen
            // that can fix it is offered in the same gesture rather than waiting for the user to find it.
            if (isChecked && !hasNotificationAccess()) {
                openNotificationAccessScreen()
            }
        }
        binding.btnLauncherGrantNotificationAccess.setOnClickListener { openNotificationAccessScreen() }
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
        setupWallpaperRow()
        binding.rowLauncherLockDesktop.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherDesktopLocked = isChecked))
        }
        setupScreenTimeoutRow()
        setupWidgetBackdropAlphaRow()
        binding.rowLauncherOpenHomeSettings.setOnClickListener {
            val host = activity ?: return@setOnClickListener
            launcherRoleManager.openHomeChooser(host)
        }
        binding.btnImportSystemShortcuts.setOnClickListener {
            launcherViewModel.importSystemShortcuts()
        }
        binding.btnResetLauncher.setOnClickListener { confirmReset() }
    }

    /**
     * S1643: which screen edge the whole taskbar is anchored to, kept out of [setupRows] for the same
     * reason [setupTrayRows] is. Entry order follows [AppSettings.LAUNCHER_TASKBAR_PLACEMENT_OPTIONS], so
     * the selected index is the option index and no second mapping can drift away from the first.
     */
    private fun setupPlacementRow() {
        binding.rowLauncherTaskbarPlacement.setEntries(
            listOf(
                getText(R.string.launcher_settings_taskbar_placement_bottom),
                getText(R.string.launcher_settings_taskbar_placement_top),
            )
        )
        binding.rowLauncherTaskbarPlacement.setOnItemSelectedListener { index ->
            if (isUpdatingFromSettings) return@setOnItemSelectedListener
            val options = AppSettings.LAUNCHER_TASKBAR_PLACEMENT_OPTIONS
            val placement = options.getOrElse(index) { AppSettings.LAUNCHER_TASKBAR_PLACEMENT_BOTTOM }
            viewModel.updateSettings(viewModel.settings.value.copy(launcherTaskbarPlacement = placement))
        }
    }

    /**
     * S1741: presets (0, 5, 15, 30, 60, 300 seconds) and custom duration entry.
     */
    private fun setupScreenTimeoutRow() {
        binding.rowLauncherScreenTimeout.setOnItemSelectedListener { index ->
            if (isUpdatingFromSettings) return@setOnItemSelectedListener
            val presets = AppSettings.LAUNCHER_SCREEN_TIMEOUT_PRESETS
            if (index in presets.indices) {
                val seconds = presets[index]
                Timber.d("Screen timeout preset chosen, seconds=%d", seconds)
                viewModel.updateSettings(
                    viewModel.settings.value.copy(launcherScreenBlackoutTimeoutSeconds = seconds)
                )
            } else if (index == presets.size) {
                showCustomScreenTimeoutDialog()
            }
        }
    }

    /** S1748: widget backdrop opacity presets selector. */
    /**
     * S2076: the offered modes, not all of them. A device with no camera never gets the camera entry,
     * because the launcher also ships to car head units and TV boxes where that row would be dead
     * forever rather than merely unavailable. The set is therefore variable, which is why every mapping
     * below goes through this list by value and never through a fixed index.
     */
    private val offeredWallpaperModes: List<String> by lazy {
        AppSettings.LAUNCHER_WALLPAPER_MODES.filter { mode ->
            (mode != AppSettings.LAUNCHER_WALLPAPER_CAMERA && mode != AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO) ||
                isCameraWallpaperAvailable.hasHardware()
        }
    }

    private fun setupWallpaperRow() {
        binding.rowLauncherWallpaper.setEntries(offeredWallpaperModes.map { getText(labelOf(it)) })
        binding.rowLauncherWallpaper.setOnItemSelectedListener { index ->
            if (isUpdatingFromSettings) return@setOnItemSelectedListener
            val fallback = AppSettings.LAUNCHER_WALLPAPER_BRANDED
            when (val mode = offeredWallpaperModes.getOrElse(index) { fallback }) {
                // Image mode only becomes real once a file is actually picked and copied, so the write
                // happens in the picker callback, not here.
                AppSettings.LAUNCHER_WALLPAPER_IMAGE -> pickWallpaperImage.launch(WALLPAPER_MIME_TYPES)
                // S2076 / S2210: camera modes are real only once a lens has been chosen.
                AppSettings.LAUNCHER_WALLPAPER_CAMERA -> beginCameraWallpaperSelection(isInstantPhoto = false)
                AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO -> beginCameraWallpaperSelection(isInstantPhoto = true)
                else -> viewModel.applyLauncherWallpaperMode(mode)
            }
        }
    }

    private fun labelOf(mode: String): Int = when (mode) {
        AppSettings.LAUNCHER_WALLPAPER_STATIC_STRIPES -> R.string.launcher_settings_wallpaper_static_stripes
        AppSettings.LAUNCHER_WALLPAPER_NONE -> R.string.launcher_settings_wallpaper_none
        AppSettings.LAUNCHER_WALLPAPER_IMAGE -> R.string.launcher_settings_wallpaper_image
        AppSettings.LAUNCHER_WALLPAPER_CAMERA -> R.string.launcher_settings_wallpaper_camera
        AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO -> R.string.launcher_wallpaper_mode_instant_photo
        else -> R.string.launcher_settings_wallpaper_branded
    }

    /** S2076: ask for the grant first when it is missing; the lens list needs an open camera to exist. */
    private fun beginCameraWallpaperSelection(isInstantPhoto: Boolean = false) {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        Timber.d("Camera wallpaper mode picked in settings (instantPhoto=$isInstantPhoto), permission granted=$granted")
        if (granted) {
            showCameraLensPicker(isInstantPhoto)
        } else {
            requestCameraForWallpaper.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * S2076: offers the lenses CameraX actually admits to, preselecting the one the capture screen would
     * open on. Cancelling, refusing, or finding no lens all put the row back on the stored mode - the
     * behaviour a cancelled image pick already has.
     */
    private fun showCameraLensPicker(isInstantPhoto: Boolean = false) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            // Enumerating lenses starts CameraX and reads Camera2 characteristics, which is disk and
            // binder work - never on the thread that is drawing the settings dialog.
            val entries = withContext(Dispatchers.IO) {
                runCatching {
                    val provider = ProcessCameraProvider.getInstance(context).get()
                    val enumeration = CameraLensEnumerationManager()
                    enumeration.select(enumeration.expand(provider))
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    Timber.e(error, "Launcher wallpaper: camera lenses could not be listed")
                    emptyList()
                }
            }
            if (entries.isEmpty()) {
                renderWallpaperRow(viewModel.settings.value)
                return@launch
            }
            val formatter = CameraLensLabelFormatter()
            val labels = entries.map { formatter.label(context, it, entries) }.toTypedArray()
            val preselected = CameraLensEnumerationManager().initialLensIndex(entries)
            var chosen = preselected
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.launcher_settings_wallpaper_camera_lens_title)
                .setSingleChoiceItems(labels, preselected) { _, which -> chosen = which }
                .setPositiveButton(R.string.ok) { _, _ ->
                    entries.getOrNull(chosen)?.let { entry ->
                        if (isInstantPhoto) {
                            viewModel.applyLauncherWallpaperInstantPhoto(entry.id)
                        } else {
                            viewModel.applyLauncherWallpaperCamera(entry.id)
                        }
                    }
                }
                .setNegativeButton(R.string.cancel) { _, _ -> renderWallpaperRow(viewModel.settings.value) }
                .setOnCancelListener { renderWallpaperRow(viewModel.settings.value) }
                .create()
                .showBoundTo(viewLifecycleOwner)
        }
    }

    private fun setupWidgetBackdropAlphaRow() {
        binding.rowLauncherWidgetBackdropAlpha.setEntries(
            listOf(
                getText(R.string.launcher_settings_widget_backdrop_alpha_0),
                getText(R.string.launcher_settings_widget_backdrop_alpha_25),
                getText(R.string.launcher_settings_widget_backdrop_alpha_50),
                getText(R.string.launcher_settings_widget_backdrop_alpha_70),
                getText(R.string.launcher_settings_widget_backdrop_alpha_85),
                getText(R.string.launcher_settings_widget_backdrop_alpha_100),
            )
        )
        binding.rowLauncherWidgetBackdropAlpha.setOnItemSelectedListener { index ->
            if (isUpdatingFromSettings) return@setOnItemSelectedListener
            val options = AppSettings.LAUNCHER_WIDGET_BACKDROP_ALPHA_OPTIONS
            val alpha = options.getOrElse(index) { options[BACKDROP_ALPHA_DEFAULT_INDEX] }
            viewModel.updateSettings(viewModel.settings.value.copy(launcherWidgetBackdropAlpha = alpha))
        }
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
        binding.rowLauncherTraySpeed.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(launcherTrayShowSpeed = isChecked))
        }
    }

    /**
     * S1400: every other control here applies immediately, so the one destructive action is the only
     * one that asks first.
     *
     * S1886: the preselected density is a one-shot suspend read of the device profile preset, so the
     * dialog is built inside a coroutine rather than collected - there is no stream to observe.
     */
    private fun confirmReset() {
        viewLifecycleOwner.lifecycleScope.launch {
            val presetIndex = densityIndexOf(launcherViewModel.presetDensityFactor())
            val content = DialogLauncherResetConfirmBinding.inflate(layoutInflater)
            content.rowResetDensity.setEntries(
                listOf(
                    getText(R.string.launcher_settings_density_sparse),
                    getText(R.string.launcher_settings_density_default),
                    getText(R.string.launcher_settings_density_dense),
                    getText(R.string.launcher_settings_density_densest),
                ),
            )
            content.rowResetDensity.setSelection(presetIndex)
            MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive,
            )
                .setTitle(R.string.launcher_settings_reset_title)
                .setView(content.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val index = densityIndexOrDefault(content.rowResetDensity.getSelectedIndex())
                    launcherViewModel.resetToDefaults(AppSettings.LAUNCHER_DENSITY_OPTIONS[index])
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showBoundTo(this@LauncherSettingsDialogFragment)
        }
    }

    /** S1886: a preset factor outside the four-step scale falls back to the standard step. */
    private fun densityIndexOf(factor: Float): Int =
        densityIndexOrDefault(AppSettings.LAUNCHER_DENSITY_OPTIONS.indexOf(factor))

    private fun densityIndexOrDefault(index: Int): Int =
        if (index in AppSettings.LAUNCHER_DENSITY_OPTIONS.indices) index else DENSITY_DEFAULT_INDEX

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
            binding.rowLauncherTraySpeed.setCheckedSilently(settings.launcherTrayShowSpeed)
            binding.rowLauncherReplaceStatusArea.setCheckedSilently(settings.launcherReplaceSystemStatusArea)
            binding.rowLauncherTopStatusStrip.setCheckedSilently(settings.launcherTopStatusStripMode)
            renderTopStatusStripRows(settings)
            binding.rowLauncherForeignNotifications.setCheckedSilently(settings.launcherForeignNotificationsEnabled)
            renderForeignNotificationsRow(settings.launcherForeignNotificationsEnabled)
            binding.rowLauncherTaskbarPlacement.setSelection(placementIndex(settings))
            binding.rowLauncherLockDesktop.setCheckedSilently(settings.launcherDesktopLocked)
            val densityIndex = AppSettings.LAUNCHER_DENSITY_OPTIONS.indexOf(settings.launcherDensityFactor)
            binding.rowLauncherDensity.setSelection(if (densityIndex >= 0) densityIndex else DENSITY_DEFAULT_INDEX)
            renderWallpaperRow(settings)
            renderScreenTimeoutRow(settings)
            renderWidgetBackdropAlphaRow(settings)
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
        collectOnLifecycle(launcherViewModel.importResult) { succeeded ->
            if (succeeded) {
                Snackbar.make(
                    binding.root,
                    R.string.launcher_settings_import_system_shortcuts_success,
                    Snackbar.LENGTH_LONG,
                ).show()
            }
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

    /**
     * S1431: the two rows the mode governs.
     *
     * The mode itself is offered only while the launcher owns the status area, because that band is where
     * it draws. The tray switch below it is disabled, not removed and not rewritten, while the mode is on:
     * strategic ADR-5 rejected removal because a vanished row never tells the user where the indicators
     * went, and rejected rewriting the stored value so switching the mode off restores exactly what the
     * user last chose. The subtitle is the explanation the disabled state would otherwise lack.
     */
    private fun renderTopStatusStripRows(settings: AppSettings) {
        binding.rowLauncherTopStatusStrip.isEnabled = settings.launcherReplaceSystemStatusArea
        val moved = settings.launcherTopStatusStripMode && settings.launcherReplaceSystemStatusArea
        binding.rowLauncherShowTray.isEnabled = !moved
        binding.rowLauncherShowTray.setSubtitle(
            if (moved) getText(R.string.launcher_settings_tray_moved_hint) else null
        )
    }

    /**
     * S1465: the row has three states, not two, because the app's own switch and the system's grant are
     * separate answers - a two-state row would read as "working" while the system silently refuses.
     * The grant is read here on every render rather than cached, which is what makes a revocation taken in
     * another screen visible without restarting the app (strategic §6.1).
     */
    private fun renderForeignNotificationsRow(isEnabled: Boolean) {
        val granted = hasNotificationAccess()
        binding.rowLauncherForeignNotifications.setSubtitle(
            when {
                !isEnabled -> R.string.launcher_settings_foreign_notifications_summary_off
                granted -> R.string.launcher_settings_foreign_notifications_summary_on
                else -> R.string.launcher_settings_foreign_notifications_summary_no_access
            }
        )
        binding.btnLauncherGrantNotificationAccess.visibility =
            if (isEnabled && !granted) View.VISIBLE else View.GONE
    }

    private fun hasNotificationAccess(): Boolean {
        val context = context ?: return false
        return context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }

    /**
     * The notification-access screen is a global list rather than a package-scoped page, which is why no
     * `package:` uri is attached - some OEM builds refuse the scoped form (`PermissionGrantIntentFactory`).
     */
    private fun openNotificationAccessScreen() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (screenMissing: ActivityNotFoundException) {
            Timber.w(screenMissing, "Notification access screen is absent on this build")
        }
    }

    /** A stored token from a newer build resolves to no entry, so the row falls back to the bottom edge. */
    private fun placementIndex(settings: AppSettings): Int {
        val index = AppSettings.LAUNCHER_TASKBAR_PLACEMENT_OPTIONS.indexOf(settings.launcherTaskbarPlacement)
        return if (index >= 0) index else 0
    }

    private fun renderWallpaperRow(settings: AppSettings) {
        val wasUpdating = isUpdatingFromSettings
        isUpdatingFromSettings = true
        val index = offeredWallpaperModes.indexOf(settings.launcherWallpaperMode)
        binding.rowLauncherWallpaper.setSelection(if (index >= 0) index else 0)
        isUpdatingFromSettings = wasUpdating
    }

    private fun renderScreenTimeoutRow(settings: AppSettings) {
        val presets = AppSettings.LAUNCHER_SCREEN_TIMEOUT_PRESETS
        val currentSeconds = settings.launcherScreenBlackoutTimeoutSeconds
        val customLabel = if (currentSeconds !in presets && currentSeconds > 0) {
            getString(R.string.launcher_settings_screen_timeout_custom_format, currentSeconds)
        } else {
            getString(R.string.launcher_settings_screen_timeout_custom)
        }
        val entries = listOf(
            getText(R.string.launcher_settings_screen_timeout_off),
            getText(R.string.launcher_settings_screen_timeout_5s),
            getText(R.string.launcher_settings_screen_timeout_15s),
            getText(R.string.launcher_settings_screen_timeout_30s),
            getText(R.string.launcher_settings_screen_timeout_60s),
            getText(R.string.launcher_settings_screen_timeout_300s),
            customLabel,
        )
        binding.rowLauncherScreenTimeout.setEntries(entries)
        val presetIndex = presets.indexOf(currentSeconds)
        val selectedIndex = if (presetIndex >= 0) presetIndex else presets.size
        binding.rowLauncherScreenTimeout.setSelection(selectedIndex)
    }

    private fun renderWidgetBackdropAlphaRow(settings: AppSettings) {
        val options = AppSettings.LAUNCHER_WIDGET_BACKDROP_ALPHA_OPTIONS
        val index = options.indexOfFirst {
            kotlin.math.abs(it - settings.launcherWidgetBackdropAlpha) < ALPHA_MATCH_EPSILON
        }
        binding.rowLauncherWidgetBackdropAlpha.setSelection(if (index >= 0) index else BACKDROP_ALPHA_DEFAULT_INDEX)
    }

    private fun showCustomScreenTimeoutDialog() {
        val current = viewModel.settings.value.launcherScreenBlackoutTimeoutSeconds
        val initialText = if (current > 0) current.toString() else ""
        val context = requireContext()
        val input = com.google.android.material.textfield.TextInputEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(initialText)
            hint = getString(R.string.launcher_settings_screen_timeout_dialog_hint)
            setSingleLine()
            setSelection(text?.length ?: 0)
        }
        val container = android.widget.FrameLayout(context).apply {
            val margin = resources.getDimensionPixelSize(R.dimen.margin_normal)
            setPadding(margin, margin / 2, margin, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.launcher_settings_screen_timeout_dialog_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val entered = input.text?.toString()?.trim()?.toIntOrNull()
                if (entered != null && entered > 0) {
                    viewModel.updateSettings(
                        viewModel.settings.value.copy(launcherScreenBlackoutTimeoutSeconds = entered)
                    )
                } else {
                    renderScreenTimeoutRow(viewModel.settings.value)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                renderScreenTimeoutRow(viewModel.settings.value)
            }
            .setOnCancelListener {
                renderScreenTimeoutRow(viewModel.settings.value)
            }
            .showBoundTo(this@LauncherSettingsDialogFragment)
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

    /**
     * The grant is changed on a system screen, so nothing in this process emits when it does - returning
     * here is the only moment the dialog can learn that the answer moved.
     */
    override fun onResume() {
        super.onResume()
        renderForeignNotificationsRow(viewModel.settings.value.launcherForeignNotificationsEnabled)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LauncherSettingsDialogFragment"

        /**
         * S1466: the desktop group holds the wallpaper row, and the quick menu's "Wallpaper" item has no
         * other home - wallpaper is a row of this dialog, not a screen of its own. Opening the dialog with
         * that group already unfolded is what keeps the two menu items from landing in the same place.
         */
        const val SECTION_DESKTOP = "desktop"

        private const val ARG_EXPAND_SECTION = "expand_section"

        /** [expandSection] unfolds one group on open regardless of its stored state; null keeps them as they were. */
        fun newInstance(expandSection: String? = null): LauncherSettingsDialogFragment =
            LauncherSettingsDialogFragment().apply {
                arguments = bundleOf(ARG_EXPAND_SECTION to expandSection)
            }

        // Standard density (1.0f) sits at index 1 of AppSettings.LAUNCHER_DENSITY_OPTIONS.
        private const val DENSITY_DEFAULT_INDEX = 1

        // 0.85f sits at index 4 of AppSettings.LAUNCHER_WIDGET_BACKDROP_ALPHA_OPTIONS.
        private const val BACKDROP_ALPHA_DEFAULT_INDEX = 4

        // The stored alpha is a float, so the row matches it by proximity rather than by equality.
        private const val ALPHA_MATCH_EPSILON = 0.01f

        // S1101: stills and GIFs both arrive as image/*; the decoder picks the right path per file.
        private val WALLPAPER_MIME_TYPES = arrayOf("image/*")
    }
}
