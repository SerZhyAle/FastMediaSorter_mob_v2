package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.logging.DebugLogMirrorPrefs
import com.sza.fastmediasorter.core.logging.LoggingHelper
import com.sza.fastmediasorter.core.util.LanguageSplitInstaller
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.domain.model.PowerSavingTrigger
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.usecase.EnsureAllFilesPredefinedResourceUseCase
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import com.sza.fastmediasorter.ui.statistics.StatisticsActivity
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.reflect.KMutableProperty0

/** Owns the entire setupViews() body: switch/spinner/input setup + button wiring. */
class GeneralSettingsViewSetupHelper(
    private val hostContext: GeneralSettingsHostContext,
    private val isUpdatingSpinner: KMutableProperty0<Boolean>,
    private val actionHelpers: GeneralSettingsActionHelpers,
    private val ensureAllFilesPredefinedResourceUseCase: EnsureAllFilesPredefinedResourceUseCase,
    private val remoteSourceAvailabilityGate: RemoteSourceAvailabilityGate,
    private val languageSplitInstaller: LanguageSplitInstaller,
) {
    // S1351: delegating properties, not a body-wide holder-prefix rewrite - every setupXxx
    // function below keeps reading the bare short name it always did (binding.xxx, viewModel.xxx,
    // cacheHelper.xxx, ...), only the constructor arity shrank. Prefixing every call site with
    // hostContext./actionHelpers. instead pushed ~30 already-dense lines over MaxLineLength.
    private val binding get() = hostContext.binding
    private val viewModel get() = hostContext.viewModel
    private val fragment get() = hostContext.fragment
    private val cacheHelper get() = actionHelpers.cacheHelper
    private val importExportHelper get() = actionHelpers.importExportHelper
    private val credentialHelper get() = actionHelpers.credentialHelper
    private val logHelper get() = actionHelpers.logHelper
    private val resetHelper get() = actionHelpers.resetHelper

    // S2601: the setup order is observable - every group below installs listeners that read the same
    // viewModel.settings, so the delegated groups keep the exact positions their inline versions held.
    fun setup() {
        GeneralSettingsLanguageSetupHelper(hostContext, languageSplitInstaller).setup()
        setupSwitches()
        setupStatisticsRow()
        setupRemoteSources()
        setupTooltips()
        setupIconSizeInput()
        setupNetworkParallelism()
        setupCacheSizeInput()
        GeneralSettingsSyncSetupHelper(hostContext, isUpdatingSpinner).setup()
        GeneralSettingsDefaultCredentialsSetupHelper(hostContext).setup()
        GeneralSettingsLinkButtonsSetupHelper(hostContext).setup()
        setupActionButtons()
    }

    private fun setupSwitches() {
        setupAllFilesResourceButton()
        binding.rowEnableFavorites.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableFavorites = isChecked))
        }
        // S0473: opt-in statistics. Routed through a dedicated VM method (not updateSettings) so the
        // off-toggle also wipes detailed activity; the VM does the work off the UI thread.
        binding.rowEnableStatistics.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            if (viewModel.settings.value.enableStatistics == isChecked) return@setOnCheckedChangeListener
            viewModel.setStatisticsCollectionEnabled(isChecked)
        }
        // S1045: secure-sensitive-screens toggle (default ON) - plain settings write like enableFavorites.
        binding.rowSecureSensitiveScreens.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.secureSensitiveScreens == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(secureSensitiveScreens = isChecked))
        }
        // S0028: Multi-window toggle. Relocated from VideoSettings to General → Interface (bottom of section).
        binding.rowAllowSeparateWindow.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(allowSeparateWindow = isChecked))
        }
        binding.rowDefaultGridMode.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.defaultGridMode == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(defaultGridMode = isChecked))
        }
        binding.rowHideGridActionButtons.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.hideGridActionButtons == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(hideGridActionButtons = isChecked))
        }
        binding.rowFileOpsInOverflowMenu.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.fileOpsInOverflowMenu == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(fileOpsInOverflowMenu = isChecked))
        }
        GeneralSettingsBrowseSwipeSetupHelper(hostContext).setup()
        binding.rowDisableAnimations?.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.disableAnimations == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(disableAnimations = isChecked))
        }
        // S2536: entries come from app:sdr_entries in the layout, in PowerSavingTrigger declaration
        // order, so the position IS the ordinal and no parallel lookup table can drift out of step.
        binding.rowPowerSaving?.setOnItemSelectedListener { position ->
            if (isUpdatingSpinner.get()) return@setOnItemSelectedListener
            val trigger = PowerSavingTrigger.entries.getOrNull(position)
                ?: return@setOnItemSelectedListener
            val current = viewModel.settings.value
            if (current.powerSavingTrigger == trigger) return@setOnItemSelectedListener
            viewModel.updateSettings(current.copy(powerSavingTrigger = trigger))
        }
        binding.rowCompactElements?.let { row ->
            row.setOnCheckedChangeListener { isChecked ->
                if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
                val current = viewModel.settings.value
                if (current.useCompactElements == isChecked) return@setOnCheckedChangeListener
                // Player controls layout is bound to this flag at inflate time (see PlayerLayoutModePrefs);
                // the switch only takes effect after an app restart, so confirm with the user first.
                MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle(R.string.restart_app_title)
                    .setMessage(R.string.restart_app_compact_elements_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.restart) { _, _ ->
                        viewModel.updateSettings(current.copy(useCompactElements = isChecked))
                        LocaleHelper.markReturnToSettings(fragment.requireContext())
                        LocaleHelper.restartApp(fragment.requireActivity())
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        isUpdatingSpinner.set(true)
                        row.setCheckedSilently(current.useCompactElements)
                        isUpdatingSpinner.set(false)
                        dialog.dismiss()
                    }
                    .showBoundTo(fragment)
            }
        }
        // S0911: main-window programs panel toggle (moved from Operations > Additional Programs).
        binding.rowShowProgramsPanel.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.showProgramsPanelInMainWindow == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(showProgramsPanelInMainWindow = isChecked))
        }
        // S0911: main-window streams panel toggle (moved from Media > Streams).
        binding.rowShowStreamsPanel.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.showStreamsPanelInMainWindow == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(showStreamsPanelInMainWindow = isChecked))
        }
        // S0160: resource ops overflow toggle
        binding.rowResourceOpsInOverflowMenu?.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.resourceOpsInOverflowMenu == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(resourceOpsInOverflowMenu = isChecked))
        }
        binding.rowAllFiles.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) {
                Timber.d("GeneralSettings: rowAllFiles listener blocked by isUpdatingSpinner")
                return@setOnCheckedChangeListener
            }
            Timber.d("GeneralSettings: User changed All Files to $isChecked")
            val current = viewModel.settings.value
            if (current.allFiles == isChecked) {
                Timber.d("GeneralSettings: All Files already $isChecked, ignoring duplicate event")
                return@setOnCheckedChangeListener
            }
            val updatedSettings = current.copy(allFiles = isChecked)
            viewModel.updateSettings(updatedSettings)
            if (!isChecked) {
                binding.rowShowHiddenFiles.setCheckedSilently(false)
                viewModel.updateSettings(updatedSettings.copy(showHiddenFiles = false))
            }
        }
        binding.rowShowHiddenFiles.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(showHiddenFiles = isChecked))
        }
        binding.rowShowSubfoldersAsItems.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(showSubfoldersAsItems = isChecked))
        }
    }

    /**
     * S0473 Phase 04: the "Statistics" navigation row sitting under the opt-in toggle. The row is
     * only meaningful while collection is on (strategic ADR-3), so its visibility tracks
     * [com.sza.fastmediasorter.domain.model.AppSettings.enableStatistics] reactively - turning the
     * toggle off hides it again without a screen reload. Click opens the dashboard activity.
     */
    private fun setupStatisticsRow() {
        binding.rowOpenStatistics.setOnRowClickListener {
            fragment.startActivity(Intent(fragment.requireContext(), StatisticsActivity::class.java))
        }
        fragment.viewLifecycleOwner.collectOnLifecycle(viewModel.settings) { settings ->
            binding.rowOpenStatistics.isVisible = settings.enableStatistics
        }
    }

    /**
     * S0391: the three remote-source group toggles. Each mass-writes its member flags; the display
     * state (group ON if any member ON) is kept in sync by [GeneralSettingsObserversHelper]. The
     * cloud row is hidden on flavors without cloud support. Turning a group OFF while it still has
     * saved resources asks for confirmation first (folders are hidden, never deleted).
     */
    private fun setupRemoteSources() {
        binding.rowSourceCloud.visibility =
            if (remoteSourceAvailabilityGate.isCloudGroupSupported()) View.VISIBLE else View.GONE

        binding.rowSourceSmb.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            applyRemoteSourceToggle(
                row = binding.rowSourceSmb,
                enabled = isChecked,
                affectedTypes = listOf(ResourceType.SMB),
            ) { it.copy(smbEnabled = isChecked) }
        }
        binding.rowSourceFtp.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            applyRemoteSourceToggle(
                row = binding.rowSourceFtp,
                enabled = isChecked,
                affectedTypes = listOf(ResourceType.SFTP, ResourceType.FTP),
            ) { it.copy(sftpEnabled = isChecked, ftpEnabled = isChecked) }
        }
        binding.rowSourceCloud.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            applyRemoteSourceToggle(
                row = binding.rowSourceCloud,
                enabled = isChecked,
                affectedTypes = listOf(ResourceType.CLOUD),
            ) { it.copy(googleDriveEnabled = isChecked, oneDriveEnabled = isChecked, dropboxEnabled = isChecked) }
        }
    }

    /**
     * Persists a group toggle. Enabling is immediate; disabling a group that still has saved
     * resources of [affectedTypes] first confirms with the user (revert on cancel), since the
     * folders become hidden, not deleted.
     */
    private fun applyRemoteSourceToggle(
        row: SettingsToggleRow,
        enabled: Boolean,
        affectedTypes: List<ResourceType>,
        transform: (
            com.sza.fastmediasorter.domain.model.AppSettings
        ) -> com.sza.fastmediasorter.domain.model.AppSettings,
    ) {
        val current = viewModel.settings.value
        if (enabled || !groupHasResources(affectedTypes)) {
            viewModel.updateSettings(transform(current))
            return
        }
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.settings_remote_source_disable_confirm_title)
            .setMessage(R.string.settings_remote_source_disable_confirm_message)
            .setCancelable(false)
            .setPositiveButton(R.string.yes) { _, _ -> viewModel.updateSettings(transform(current)) }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                isUpdatingSpinner.set(true)
                row.setCheckedSilently(true)
                isUpdatingSpinner.set(false)
                dialog.dismiss()
            }
            .showBoundTo(fragment)
    }

    private fun groupHasResources(types: List<ResourceType>): Boolean =
        viewModel.resources.value.any { it.type in types }

    private fun setupAllFilesResourceButton() {
        // defStyleAttr must be the Material outlined-button attr: MaterialButton's built-in
        // defStyleRes is the FILLED button, which would win over the theme overlay and render a
        // solid blue button. Passing materialButtonOutlinedStyle gives the real outlined base.
        val button = MaterialButton(
            ContextThemeWrapper(fragment.requireContext(), R.style.Widget_FastMediaSorter_SettingsButton_Outlined),
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = fragment.getString(R.string.settings_all_files_create_resource)
            isAllCaps = false
            setOnClickListener {
                isEnabled = false
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    ensureAllFilesPredefinedResourceUseCase()
                        .onSuccess { result ->
                            if (result.created && fragment.isAdded) {
                                Toast.makeText(
                                    fragment.requireContext(),
                                    R.string.settings_all_files_resource_created,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .onFailure { error ->
                            Timber.e(error, "GeneralSettings: failed to create All Files resource")
                            if (fragment.isAdded) {
                                Toast.makeText(
                                    fragment.requireContext(),
                                    R.string.settings_unknown_error,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    isEnabled = true
                }
            }
        }
        fragment.viewLifecycleOwner.collectOnLifecycle(viewModel.resources) { resources ->
            val hasPredefinedResource = resources.any(ensureAllFilesPredefinedResourceUseCase::isPredefinedResource)
            binding.rowAllFiles.setTrailingControl(if (hasPredefinedResource) null else button)
        }
    }

    private fun setupTooltips() {
        binding.iconHelpDefaultCredentials.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                fragment.requireContext(),
                R.string.tooltip_default_credentials_title,
                R.string.tooltip_default_credentials_message
            )
        }
        binding.iconHelpGridSize.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                fragment.requireContext(),
                R.string.tooltip_grid_size_title,
                R.string.tooltip_grid_size_message
            )
        }
    }

    // S0567: Network Parallelism migrated to SettingsInputRow (numeric, free-form). The fixed-option
    // dropdown is dropped (ADR-1); the value commits on focus loss / IME done with the same 1..32 clamp.
    private fun setupNetworkParallelism() {
        binding.actvNetworkParallelism.text = fragment.getString(R.string.number_format, viewModel.settings.value.networkParallelism)
        binding.actvNetworkParallelism.setOnCommitListener { value ->
            if (isUpdatingSpinner.get()) return@setOnCommitListener
            val limit = value.toString().toIntOrNull()
            if (limit != null && limit in 1..32) {
                val current = viewModel.settings.value
                if (current.networkParallelism != limit) {
                    viewModel.updateSettings(current.copy(networkParallelism = limit))
                    com.sza.fastmediasorter.data.network.ConnectionThrottleManager.setUserNetworkLimit(limit)
                }
            } else {
                binding.actvNetworkParallelism.text = fragment.getString(R.string.number_format, viewModel.settings.value.networkParallelism)
            }
        }
    }

    // S0567: Cache Size migrated to SettingsInputRow (numeric, free-form). The fixed-option dropdown
    // is dropped (ADR-1); the value commits on focus loss / IME done with the same 512..16384 range.
    private fun setupCacheSizeInput() {
        binding.actvCacheSizeLimit.text = fragment.getString(R.string.number_format, viewModel.settings.value.cacheSizeMb)
        binding.actvCacheSizeLimit.setOnCommitListener { value ->
            if (isUpdatingSpinner.get()) return@setOnCommitListener
            val sizeMb = value.toString().toIntOrNull()
            if (sizeMb != null && sizeMb in 512..16384) {
                if (viewModel.settings.value.cacheSizeMb != sizeMb) cacheHelper.showCacheSizeRestartDialog(sizeMb)
            } else {
                binding.actvCacheSizeLimit.text = fragment.getString(R.string.number_format, viewModel.settings.value.cacheSizeMb)
                Toast.makeText(
                    fragment.requireContext(),
                    fragment.getString(R.string.settings_cache_size_range_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupIconSizeInput() {
        val iconSizeOptions = (32..256 step 8).map { it.toString() }.toTypedArray()
        val iconSizeAdapter = ArrayAdapter(
            fragment.requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            iconSizeOptions
        )
        binding.etIconSize.setAdapter(iconSizeAdapter)
        binding.etIconSize.setText(
            fragment.getString(R.string.number_format, viewModel.settings.value.defaultIconSize),
            false
        )
        binding.etIconSize.setOnItemClickListener { _, _, position, _ ->
            if (isUpdatingSpinner.get()) return@setOnItemClickListener
            val size = iconSizeOptions[position].toInt()
            val current = viewModel.settings.value
            if (current.defaultIconSize != size) {
                viewModel.updateSettings(current.copy(defaultIconSize = size))
            }
        }
        binding.etIconSize.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !isUpdatingSpinner.get()) {
                val size = binding.etIconSize.text.toString().toIntOrNull()
                if (size != null && size in 32..256 && (size - 32) % 8 == 0) {
                    val current = viewModel.settings.value
                    if (current.defaultIconSize != size) {
                        viewModel.updateSettings(current.copy(defaultIconSize = size))
                    }
                } else {
                    binding.etIconSize.setText(
                        fragment.getString(R.string.number_format, viewModel.settings.value.defaultIconSize),
                        false
                    )
                }
            }
        }
    }

    private fun setupActionButtons() {
        binding.btnResetSettings.setOnClickListener { resetHelper.showResetSettingsConfirmation() }
        binding.btnResetGeneralSection.setOnClickListener { resetHelper.showResetGeneralSectionConfirmation() }

        binding.headerDebugSettings.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        binding.containerDebugSettings.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        if (BuildConfig.DEBUG) {
            setupDebugLogMirrorRow()
        }

        if (BuildConfig.DEBUG && com.sza.fastmediasorter.ui.settings.IntegrationTestDialog.isAvailable()) {
            binding.btnIntegrationTests.visibility = View.VISIBLE
            binding.btnIntegrationTests.setOnClickListener {
                com.sza.fastmediasorter.ui.settings.IntegrationTestDialog()
                    .show(fragment.childFragmentManager, com.sza.fastmediasorter.ui.settings.IntegrationTestDialog.TAG)
            }
            binding.btnImportTestCredentials.visibility = View.VISIBLE
            binding.btnImportTestCredentials.setOnClickListener { credentialHelper.importTestCredentials() }
        } else {
            binding.btnIntegrationTests.visibility = View.GONE
            binding.btnImportTestCredentials.visibility = View.GONE
        }

        binding.btnExportSettings.setOnClickListener { importExportHelper.showExportSettingsConfirmation() }
        binding.btnImportSettings.setOnClickListener { importExportHelper.showImportSettingsConfirmation() }

        logHelper.setupButtons()

        binding.btnAutoCalculateCache.setOnClickListener { cacheHelper.autoCalculateCacheSize() }
        binding.btnClearCache.setOnClickListener { cacheHelper.clearCache() }
        binding.btnResetSmbConnections.setOnClickListener { resetHelper.resetSmbConnections() }
        cacheHelper.updateCacheSize()
    }

    /**
     * S1357: the mirror flag is stored outside AppSettings (see [DebugLogMirrorPrefs]), so the row
     * is seeded from that store directly. Switching it off also drops the folder already selected
     * this session - otherwise it would keep receiving writes until the process restarts.
     */
    private fun setupDebugLogMirrorRow() {
        val context = fragment.requireContext().applicationContext
        binding.rowDebugLogMirror.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            DebugLogMirrorPrefs.setEnabled(context, isChecked)
            if (!isChecked) {
                LoggingHelper.clearDebugMirrorTarget()
            }
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val enabled = withContext(Dispatchers.IO) { DebugLogMirrorPrefs.isEnabled(context) }
            isUpdatingSpinner.set(true)
            binding.rowDebugLogMirror.setCheckedSilently(enabled)
            isUpdatingSpinner.set(false)
        }
    }
}
