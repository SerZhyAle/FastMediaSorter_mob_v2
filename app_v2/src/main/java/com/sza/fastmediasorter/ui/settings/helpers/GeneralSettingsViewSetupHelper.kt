package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.compat.ChromeOsCompat
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.usecase.EnsureAllFilesPredefinedResourceUseCase
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.statistics.StatisticsActivity
import com.sza.fastmediasorter.ui.welcome.WelcomeActivity
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.launch
import timber.log.Timber

/** Owns the entire setupViews() body: switch/spinner/input setup + button wiring. */
class GeneralSettingsViewSetupHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
    private val getIsUpdatingSpinner: () -> Boolean,
    private val setIsUpdatingSpinner: (Boolean) -> Unit,
    private val cacheHelper: GeneralSettingsCacheHelper,
    private val permissionsHelper: GeneralSettingsPermissionsHelper,
    private val importExportHelper: GeneralSettingsImportExportHelper,
    private val credentialHelper: GeneralSettingsCredentialHelper,
    private val logHelper: GeneralSettingsLogHelper,
    private val resetHelper: GeneralSettingsResetHelper,
    private val ensureAllFilesPredefinedResourceUseCase: EnsureAllFilesPredefinedResourceUseCase,
    private val remoteSourceAvailabilityGate: RemoteSourceAvailabilityGate,
) {
    private var lastCommittedDefaultUser: String = ""
    private var lastCommittedDefaultPassword: String = ""

    fun setup() {
        setupLanguageSpinner()
        setupSwitches()
        setupStatisticsRow()
        setupRemoteSources()
        setupTooltips()
        setupIconSizeInput()
        setupNetworkParallelism()
        setupCacheSizeInput()
        setupSyncSection()
        setupDefaultCredentials()
        setupLinkButtons()
        setupActionButtons()
    }

    // S0567: spinnerLanguage migrated from raw Spinner to SettingsDropdownRow (ADR-1).
    private fun setupLanguageSpinner() {
        val languages = listOf<CharSequence>(
            fragment.getString(R.string.language_default),
            fragment.getString(R.string.language_english),
            fragment.getString(R.string.language_russian),
            fragment.getString(R.string.language_ukrainian)
        )
        setIsUpdatingSpinner(true)
        binding.spinnerLanguage.setEntries(languages)
        binding.spinnerLanguage.setSelection(languageSelectionToPosition(currentLanguageSelectionCode()))
        binding.spinnerLanguage.setOnItemSelectedListener { position ->
            if (getIsUpdatingSpinner()) return@setOnItemSelectedListener
            val newLanguageCode = positionToLanguageSelection(position)
            val currentLanguageCode = currentLanguageSelectionCode()
            if (newLanguageCode != currentLanguageCode) {
                showRestartDialog(currentLanguageCode, newLanguageCode)
            }
        }
        binding.spinnerLanguage.post { setIsUpdatingSpinner(false) }
    }

    private fun setupSwitches() {
        setupAllFilesResourceButton()
        binding.rowEnableFavorites.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableFavorites = isChecked))
        }
        // S0473: opt-in statistics. Routed through a dedicated VM method (not updateSettings) so the
        // off-toggle also wipes detailed activity; the VM does the work off the UI thread.
        binding.rowEnableStatistics.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            if (viewModel.settings.value.enableStatistics == isChecked) return@setOnCheckedChangeListener
            viewModel.setStatisticsCollectionEnabled(isChecked)
        }
        // S0028: Multi-window toggle. Relocated from VideoSettings to General → Interface (bottom of section).
        binding.rowAllowSeparateWindow.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(allowSeparateWindow = isChecked))
        }
        binding.rowDefaultGridMode.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.defaultGridMode == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(defaultGridMode = isChecked))
        }
        binding.rowHideGridActionButtons.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.hideGridActionButtons == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(hideGridActionButtons = isChecked))
        }
        binding.rowFileOpsInOverflowMenu.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.fileOpsInOverflowMenu == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(fileOpsInOverflowMenu = isChecked))
        }
        binding.rowCompactElements?.let { row ->
            row.setOnCheckedChangeListener { isChecked ->
                if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
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
                        com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs
                            .setCompact(fragment.requireContext(), isChecked)
                        LocaleHelper.markReturnToSettings(fragment.requireContext())
                        LocaleHelper.restartApp(fragment.requireActivity())
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        setIsUpdatingSpinner(true)
                        row.setCheckedSilently(current.useCompactElements)
                        setIsUpdatingSpinner(false)
                        dialog.dismiss()
                    }
                    .show()
            }
        }
        // S0911: main-window programs panel toggle (moved from Operations > Additional Programs).
        binding.rowShowProgramsPanel.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.showProgramsPanelInMainWindow == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(showProgramsPanelInMainWindow = isChecked))
        }
        // S0911: main-window streams panel toggle (moved from Media > Streams).
        binding.rowShowStreamsPanel.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.showStreamsPanelInMainWindow == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(showStreamsPanelInMainWindow = isChecked))
        }
        // S0160: resource ops overflow toggle
        binding.rowResourceOpsInOverflowMenu?.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            if (current.resourceOpsInOverflowMenu == isChecked) return@setOnCheckedChangeListener
            viewModel.updateSettings(current.copy(resourceOpsInOverflowMenu = isChecked))
        }
        binding.rowAllFiles.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) {
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
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(showHiddenFiles = isChecked))
        }
        binding.rowShowSubfoldersAsItems.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
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
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            applyRemoteSourceToggle(
                row = binding.rowSourceSmb,
                enabled = isChecked,
                affectedTypes = listOf(ResourceType.SMB),
            ) { it.copy(smbEnabled = isChecked) }
        }
        binding.rowSourceFtp.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            applyRemoteSourceToggle(
                row = binding.rowSourceFtp,
                enabled = isChecked,
                affectedTypes = listOf(ResourceType.SFTP, ResourceType.FTP),
            ) { it.copy(sftpEnabled = isChecked, ftpEnabled = isChecked) }
        }
        binding.rowSourceCloud.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
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
        transform: (com.sza.fastmediasorter.domain.model.AppSettings) -> com.sza.fastmediasorter.domain.model.AppSettings,
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
                setIsUpdatingSpinner(true)
                row.setCheckedSilently(true)
                setIsUpdatingSpinner(false)
                dialog.dismiss()
            }
            .show()
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
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(fragment.requireContext(), R.string.tooltip_default_credentials_title, R.string.tooltip_default_credentials_message)
        }
        binding.iconHelpGridSize.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(fragment.requireContext(), R.string.tooltip_grid_size_title, R.string.tooltip_grid_size_message)
        }
    }

    // S0567: Network Parallelism migrated to SettingsInputRow (numeric, free-form). The fixed-option
    // dropdown is dropped (ADR-1); the value commits on focus loss / IME done with the same 1..32 clamp.
    private fun setupNetworkParallelism() {
        binding.actvNetworkParallelism.text = fragment.getString(R.string.number_format, viewModel.settings.value.networkParallelism)
        binding.actvNetworkParallelism.setOnCommitListener { value ->
            if (getIsUpdatingSpinner()) return@setOnCommitListener
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
            if (getIsUpdatingSpinner()) return@setOnCommitListener
            val sizeMb = value.toString().toIntOrNull()
            if (sizeMb != null && sizeMb in 512..16384) {
                if (viewModel.settings.value.cacheSizeMb != sizeMb) cacheHelper.showCacheSizeRestartDialog(sizeMb)
            } else {
                binding.actvCacheSizeLimit.text = fragment.getString(R.string.number_format, viewModel.settings.value.cacheSizeMb)
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_cache_size_range_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupIconSizeInput() {
        val iconSizeOptions = (32..256 step 8).map { it.toString() }.toTypedArray()
        val iconSizeAdapter = ArrayAdapter(fragment.requireContext(), android.R.layout.simple_dropdown_item_1line, iconSizeOptions)
        binding.etIconSize.setAdapter(iconSizeAdapter)
        binding.etIconSize.setText(fragment.getString(R.string.number_format, viewModel.settings.value.defaultIconSize), false)
        binding.etIconSize.setOnItemClickListener { _, _, position, _ ->
            if (getIsUpdatingSpinner()) return@setOnItemClickListener
            val size = iconSizeOptions[position].toInt()
            val current = viewModel.settings.value
            if (current.defaultIconSize != size) {
                viewModel.updateSettings(current.copy(defaultIconSize = size))
            }
        }
        binding.etIconSize.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !getIsUpdatingSpinner()) {
                val size = binding.etIconSize.text.toString().toIntOrNull()
                if (size != null && size in 32..256 && (size - 32) % 8 == 0) {
                    val current = viewModel.settings.value
                    if (current.defaultIconSize != size) {
                        viewModel.updateSettings(current.copy(defaultIconSize = size))
                    }
                } else {
                    binding.etIconSize.setText(fragment.getString(R.string.number_format, viewModel.settings.value.defaultIconSize), false)
                }
            }
        }
    }

    private fun setupSyncSection() {
        binding.rowEnableBackgroundSync.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableBackgroundSync = isChecked))
        }
        binding.rowEnableThumbnailPreload.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableThumbnailPreload = isChecked))
            binding.layoutThumbnailPreloadWifiOnly.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.rowThumbnailPreloadWifiOnly.setOnCheckedChangeListener { isChecked ->
            if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(thumbnailPreloadWifiOnly = isChecked))
        }
        val syncIntervalOptions = arrayOf("5", "15", "60", "120", "300")
        val syncAdapter = android.widget.ArrayAdapter(fragment.requireContext(), android.R.layout.simple_dropdown_item_1line, syncIntervalOptions)
        binding.actvSyncInterval?.let { syncIntervalView ->
            syncIntervalView.setAdapter(syncAdapter)
            val currentMinutes = viewModel.settings.value.backgroundSyncIntervalHours * 60
            syncIntervalView.setText(fragment.getString(R.string.number_format, currentMinutes), false)
            syncIntervalView.setOnItemClickListener { _, _, position, _ ->
                if (getIsUpdatingSpinner()) return@setOnItemClickListener
                val minutes = syncIntervalOptions[position].toInt()
                val hours = (minutes / 60.0).toInt().coerceAtLeast(1)
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(backgroundSyncIntervalHours = hours))
            }
            syncIntervalView.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && !getIsUpdatingSpinner()) {
                    val minutes = syncIntervalView.text.toString().toIntOrNull()
                    if (minutes != null && minutes >= 5) {
                        val hours = (minutes / 60.0).toInt().coerceAtLeast(1)
                        viewModel.updateSettings(viewModel.settings.value.copy(backgroundSyncIntervalHours = hours))
                    } else {
                        val previousMinutes = viewModel.settings.value.backgroundSyncIntervalHours * 60
                        syncIntervalView.setText(fragment.getString(R.string.number_format, previousMinutes), false)
                        Toast.makeText(fragment.requireContext(), R.string.slide_interval_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        binding.btnSyncNow.setOnClickListener {
            if (viewModel.manualNetworkSyncState.value.inProgress) viewModel.cancelManualNetworkSync()
            else viewModel.startManualNetworkSync()
        }
    }

    private fun setupDefaultCredentials() {
        val currentSettings = viewModel.settings.value
        lastCommittedDefaultUser = currentSettings.defaultUser
        lastCommittedDefaultPassword = currentSettings.defaultPassword

        // Clear any programmatic filters - these fields accept any character including Cyrillic.
        binding.etDefaultUser.filters = arrayOf()
        binding.etDefaultPassword.filters = arrayOf()
        binding.etDefaultUser.isFocusableInTouchMode = true
        binding.etDefaultPassword.isFocusableInTouchMode = true

        binding.etDefaultUser.setText(lastCommittedDefaultUser)
        binding.etDefaultUser.imeOptions = EditorInfo.IME_ACTION_NEXT
        // No setOnClickListener on til/et - overriding performClick() breaks Chrome OS IME
        // connection: ARC establishes keyboard routing inside the system click handler, and a
        // custom listener replaces it.  TextInputLayout already forwards container clicks to the
        // inner EditText automatically, so no click listeners are needed here.
        installTapFocusBridge(binding.tilDefaultUser, binding.etDefaultUser)
        binding.etDefaultUser.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == binding.etDefaultUser.imeOptions) {
                commitDefaultUserIfChanged()
                binding.etDefaultPassword.requestFocus()
                true
            } else {
                false
            }
        }
        binding.etDefaultUser.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commitDefaultUserIfChanged()
        }

        binding.etDefaultPassword.setText(lastCommittedDefaultPassword)
        binding.etDefaultPassword.imeOptions = EditorInfo.IME_ACTION_DONE
        installTapFocusBridge(binding.tilDefaultPassword, binding.etDefaultPassword)
        binding.etDefaultPassword.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == binding.etDefaultPassword.imeOptions) {
                commitDefaultPasswordIfChanged()
                view.clearFocus()
                val imm = fragment.requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                true
            } else {
                false
            }
        }
        binding.etDefaultPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commitDefaultPasswordIfChanged()
        }
    }

    private fun installTapFocusBridge(container: View, editor: TextInputEditText) {
        val listener = View.OnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP && !editor.hasFocus()) {
                focusEditorFromTap(editor)
            }
            false
        }
        container.setOnTouchListener(listener)
        editor.setOnTouchListener(listener)
    }

    private fun focusEditorFromTap(editor: TextInputEditText) {
        editor.requestFocusFromTouch()
        editor.requestFocus()
        editor.setSelection(editor.text?.length ?: 0)

        // Non-Chrome OS devices in this screen can miss the editor-focus hand-off after a box tap.
        // Keep ARC on the native click path and only add explicit IME assist for other devices.
        if (ChromeOsCompat.isChromeOs(fragment.requireContext())) return

        editor.post {
            if (!editor.isAttachedToWindow) return@post
            val imm = fragment.requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun commitDefaultUserIfChanged() {
        val newUser = binding.etDefaultUser.text.toString()
        if (lastCommittedDefaultUser == newUser) return

        lastCommittedDefaultUser = newUser
        val current = viewModel.settings.value
        val ownerTrigger = BuildConfig.OWNER_TRIGGER
        if (current.defaultUser != newUser) {
            viewModel.updateSettings(current.copy(defaultUser = newUser))
        }
        if (ownerTrigger.isNotEmpty() && newUser.equals(ownerTrigger, ignoreCase = true)) {
            MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.import_resources_title)
                .setMessage(R.string.import_resources_message)
                .setPositiveButton(R.string.yes) { _, _ -> viewModel.importSzaResources(fragment.requireContext()) }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    private fun commitDefaultPasswordIfChanged() {
        val newPassword = binding.etDefaultPassword.text.toString()
        if (lastCommittedDefaultPassword == newPassword) return

        lastCommittedDefaultPassword = newPassword
        val current = viewModel.settings.value
        if (current.defaultPassword != newPassword) {
            viewModel.updateSettings(current.copy(defaultPassword = newPassword))
        }
    }

    private fun setupLinkButtons() {
        binding.btnUserGuide.setOnClickListener {
            openUrl(when (LocaleHelper.getLanguage(fragment.requireContext())) {
                "ru" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/index-ru.html"
                "uk" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/index-uk.html"
                else -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/"
            }, "No browser found to open documentation")
        }
        binding.btnHowToGuides.setOnClickListener {
            openUrl(when (LocaleHelper.getLanguage(fragment.requireContext())) {
                "ru" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/HOW_TO_RU.html"
                "uk" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/HOW_TO_UK.html"
                else -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/HOW_TO.html"
            }, "No browser found to open documentation")
        }
        // S0994: PC-side companion publish-folders guide, shown only when companion import is available (lite/vr hide it).
        binding.btnCompanionPublishGuide.isVisible = viewModel.isCompanionImportAvailable
        binding.btnCompanionPublishGuide.setOnClickListener {
            Timber.d("S0994: open companion publish-folders guide from settings")
            openUrl(
                com.sza.fastmediasorter.ui.common.support.SupportIntentFactory.companionPublishGuideUrl(),
                fragment.getString(R.string.settings_no_browser_for_docs),
            )
        }
        binding.btnOpenWelcome.setOnClickListener {
            fragment.startActivity(Intent(fragment.requireContext(), WelcomeActivity::class.java))
        }
        binding.btnPrivacyPolicy.setOnClickListener {
            openUrl(when (LocaleHelper.getLanguage(fragment.requireContext())) {
                "ru" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/PRIVACY_POLICY.ru.html"
                "uk" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/PRIVACY_POLICY.uk.html"
                else -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/PRIVACY_POLICY.html"
            }, "No browser found to open Privacy Policy")
        }
        binding.btnOpenSourceLicenses.setOnClickListener {
            fragment.parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, com.sza.fastmediasorter.ui.settings.fragments.OpenSourceLicensesFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupActionButtons() {
        binding.btnResetSettings.setOnClickListener { resetHelper.showResetSettingsConfirmation() }
        binding.btnResetGeneralSection.setOnClickListener { resetHelper.showResetGeneralSectionConfirmation() }

        binding.headerDebugSettings.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        binding.containerDebugSettings.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE

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

    private fun positionToLanguageSelection(position: Int): String = when (position) {
        1 -> "en"
        2 -> "ru"
        3 -> "uk"
        else -> LocaleHelper.FOLLOW_SYSTEM_LANGUAGE
    }

    private fun languageSelectionToPosition(languageCode: String): Int = when {
        LocaleHelper.isFollowSystemLanguage(languageCode) -> 0
        LocaleHelper.resolveSupportedLanguageCode(languageCode) == "ru" -> 2
        LocaleHelper.resolveSupportedLanguageCode(languageCode) == "uk" -> 3
        else -> 1
    }

    private fun currentLanguageSelectionCode(): String {
        return if (LocaleHelper.isFollowingSystemLanguage(fragment.requireContext())) {
            LocaleHelper.FOLLOW_SYSTEM_LANGUAGE
        } else {
            LocaleHelper.resolveSupportedLanguageCode(viewModel.settings.value.language)
        }
    }

    private fun languageDisplayName(languageCode: String): String {
        return if (LocaleHelper.isFollowSystemLanguage(languageCode)) {
            fragment.getString(R.string.language_default)
        } else {
            LocaleHelper.getLanguageName(languageCode)
        }
    }

    private fun showRestartDialog(previousLanguageCode: String, newLanguageCode: String) {
        val languageName = languageDisplayName(newLanguageCode)
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.restart_app_title)
            .setMessage(fragment.getString(R.string.restart_app_message, languageName))
            .setPositiveButton(R.string.restart) { _, _ ->
                val currentSettings = viewModel.settings.value
                viewModel.updateSettings(currentSettings.copy(language = newLanguageCode))
                LocaleHelper.markReturnToSettings(fragment.requireContext())
                LocaleHelper.changeLanguage(fragment.requireActivity(), newLanguageCode)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                setIsUpdatingSpinner(true)
                binding.spinnerLanguage.setSelection(languageSelectionToPosition(previousLanguageCode))
                binding.spinnerLanguage.post { setIsUpdatingSpinner(false) }
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun openUrl(url: String, notFoundMessage: String) {
        try {
            fragment.startActivity(Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse(url) })
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(fragment.requireContext(), notFoundMessage, Toast.LENGTH_SHORT).show()
        }
    }
}
