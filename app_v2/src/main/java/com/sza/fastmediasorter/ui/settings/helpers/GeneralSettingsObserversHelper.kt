package com.sza.fastmediasorter.ui.settings.helpers

import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.core.util.PowerPolicyLevel
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BrowseSwipeDirection
import com.sza.fastmediasorter.domain.model.PowerSavingTrigger
import com.sza.fastmediasorter.ui.browse.helpers.BrowseSwipeActionCatalog
import com.sza.fastmediasorter.ui.dialog.MaterialProgressDialog
import com.sza.fastmediasorter.ui.dialog.UiLanguagePickerItems
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class GeneralSettingsObserversHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
    private val getIsUpdatingSpinner: () -> Boolean,
    private val setIsUpdatingSpinner: (Boolean) -> Unit,
    private val capabilityAvailability: CapabilityAvailability,
    // S2707: passed in rather than taken from the ViewModel, whose constructor detekt already excuses
    // at 22 parameters - a 23rd would grow accepted debt to carry one read-only device fact.
    private val batteryLevelUnavailable: StateFlow<Boolean>,
) {
    private var manualSyncProgressDialog: MaterialProgressDialog? = null

    /**
     * S1190: the language row shows a value, not a dropdown position - the set of languages is data now.
     * Whether the app follows the system is read from the platform rather than from the stored setting,
     * because clearing the per-app language leaves the previously stored value behind.
     */
    private fun updateLanguageRow(storedLanguage: String) {
        val context = fragment.requireContext()
        val selectionCode = if (LocaleHelper.isFollowingSystemLanguage(context)) {
            LocaleHelper.FOLLOW_SYSTEM_LANGUAGE
        } else {
            storedLanguage
        }
        binding.rowLanguage.setValue(UiLanguagePickerItems.label(context, selectionCode))
    }

    /**
     * S2536: selection plus the reason the mode is active right now.
     *
     * The subtitle names the CAUSE rather than only the fact. Without it a user whose animation just
     * stopped has no way to tell an economy from a fault, and the likeliest next step is a bug report
     * or a reinstall.
     */
    private fun bindPowerSavingRow(trigger: PowerSavingTrigger) {
        val row = binding.rowPowerSaving ?: return
        val position = trigger.ordinal
        if (row.getSelectedIndex() != position) row.setSelection(position)

        val context = fragment.requireContext()
        val threshold = trigger.thresholdPercent
        val state = when {
            // Ahead of the SAVING guard on purpose: on a device that reports no charge the threshold
            // can never raise the level, so the guard below would swallow the one message that says so.
            threshold != null && batteryLevelUnavailable.value ->
                context.getString(R.string.pref_power_saving_state_no_battery)
            AnimationPolicy.level != PowerPolicyLevel.SAVING -> null
            trigger == PowerSavingTrigger.ALWAYS -> context.getString(R.string.pref_power_saving_state_always)
            threshold != null -> context.getString(R.string.pref_power_saving_state_low_battery, threshold)
            // SAVING with neither of those means the platform raised it, not this app.
            else -> context.getString(R.string.pref_power_saving_state_system_saver)
        }
        Timber.d("S2707: power saving row noBattery=${batteryLevelUnavailable.value} threshold=$threshold")
        val base = context.getString(R.string.pref_power_saving_desc)
        Timber.d("S2536: settings row trigger=$trigger cause=${state != null} level=${AnimationPolicy.level}")
        row.setSubtitle(if (state == null) base else "$base\n$state")
    }

    fun observeData() {
        // S2707: the flag settles after the first battery broadcast, which can arrive once this
        // screen is already open, so the row is redrawn rather than bound once from the settings flow.
        fragment.viewLifecycleOwner.collectOnLifecycle(batteryLevelUnavailable) {
            bindPowerSavingRow(viewModel.settings.value.powerSavingTrigger)
        }

        fragment.viewLifecycleOwner.collectOnLifecycle(viewModel.settings) { settings ->
            updateLanguageRow(settings.language)

            setIsUpdatingSpinner(true)

            if (binding.rowEnableFavorites.isChecked != settings.enableFavorites) {
                binding.rowEnableFavorites.setCheckedSilently(settings.enableFavorites)
            }
            binding.layoutFavoritesImportExport.visibility =
                if (settings.enableFavorites) View.VISIBLE else View.GONE
            // S0473: opt-in statistics toggle initial/observed state.
            if (binding.rowEnableStatistics.isChecked != settings.enableStatistics) {
                binding.rowEnableStatistics.setCheckedSilently(settings.enableStatistics)
            }
            // S1045: secure-sensitive-screens toggle initial/observed state.
            if (binding.rowSecureSensitiveScreens.isChecked != settings.secureSensitiveScreens) {
                binding.rowSecureSensitiveScreens.setCheckedSilently(settings.secureSensitiveScreens)
            }
            // S0028: Multi-window toggle observation. Lives in General → Interface (bottom).
            if (binding.rowAllowSeparateWindow.isChecked != settings.allowSeparateWindow) {
                binding.rowAllowSeparateWindow.setCheckedSilently(settings.allowSeparateWindow)
            }
            bindBrowseListRows(settings)
            if (binding.rowCompactElements?.isChecked != settings.useCompactElements) {
                binding.rowCompactElements?.setCheckedSilently(settings.useCompactElements)
            }
            if (binding.rowDisableAnimations?.isChecked != settings.disableAnimations) {
                binding.rowDisableAnimations?.setCheckedSilently(settings.disableAnimations)
            }
            bindPowerSavingRow(settings.powerSavingTrigger)
            // S0911: main-window programs panel toggle (moved from Operations > Additional Programs).
            if (binding.rowShowProgramsPanel.isChecked != settings.showProgramsPanelInMainWindow) {
                binding.rowShowProgramsPanel.setCheckedSilently(settings.showProgramsPanelInMainWindow)
            }
            // S0911: main-window streams panel toggle (moved from Media > Streams). Visibility
            // replicates the gate it had while nested in streamsDefaultsGroup - capability AND master toggle.
            if (binding.rowShowStreamsPanel.isChecked != settings.showStreamsPanelInMainWindow) {
                binding.rowShowStreamsPanel.setCheckedSilently(settings.showStreamsPanelInMainWindow)
            }
            binding.rowShowStreamsPanel.visibility =
                if (capabilityAvailability.isStreamsAvailable() && settings.enableStreams) View.VISIBLE else View.GONE
            // S0160: resource ops overflow toggle
            if (binding.rowResourceOpsInOverflowMenu?.isChecked != settings.resourceOpsInOverflowMenu) {
                binding.rowResourceOpsInOverflowMenu?.setCheckedSilently(settings.resourceOpsInOverflowMenu)
            }

            bindFileVisibilityRows(settings)
            bindSourceGroupRows(settings)

            if (binding.rowEnableBackgroundSync.isChecked != settings.enableBackgroundSync) {
                binding.rowEnableBackgroundSync.setCheckedSilently(settings.enableBackgroundSync)
            }

            bindThumbnailPreloadRows(settings)

            setIsUpdatingSpinner(false)

            // S0567: SettingsInputRow exposes a `text` property (was AutoCompleteTextView.setText).
            val currentParallelism = binding.actvNetworkParallelism.text.toString().toIntOrNull()
            if (currentParallelism != settings.networkParallelism) {
                binding.actvNetworkParallelism.text = fragment.getString(R.string.number_format, settings.networkParallelism)
            }

            val currentCacheSize = binding.actvCacheSizeLimit.text.toString().toIntOrNull()
            if (currentCacheSize != settings.cacheSizeMb) {
                binding.actvCacheSizeLimit.text = fragment.getString(R.string.number_format, settings.cacheSizeMb)
            }

            val currentIconSize = binding.etIconSize.text.toString().toIntOrNull()
            if (currentIconSize != settings.defaultIconSize) {
                binding.etIconSize.setText(fragment.getString(R.string.number_format, settings.defaultIconSize), false)
            }

            val syncMinutes = settings.backgroundSyncIntervalHours * 60
            binding.actvSyncInterval?.let { syncIntervalView ->
                if (syncIntervalView.text.toString() != syncMinutes.toString()) {
                    syncIntervalView.setText(fragment.getString(R.string.number_format, syncMinutes), false)
                }
            }
        }
    }

    private fun bindBrowseListRows(settings: AppSettings) {
        if (binding.rowDefaultGridMode.isChecked != settings.defaultGridMode) {
            binding.rowDefaultGridMode.setCheckedSilently(settings.defaultGridMode)
        }
        if (binding.rowHideGridActionButtons.isChecked != settings.hideGridActionButtons) {
            binding.rowHideGridActionButtons.setCheckedSilently(settings.hideGridActionButtons)
        }
        if (binding.rowFileOpsInOverflowMenu.isChecked != settings.fileOpsInOverflowMenu) {
            binding.rowFileOpsInOverflowMenu.setCheckedSilently(settings.fileOpsInOverflowMenu)
        }
        // S2533: no re-fire guard - SettingsDropdownRow.setSelection does not invoke the
        // selection listener, unlike the toggle rows above.
        val swipeActions = BrowseSwipeActionCatalog.orderedForPicker()
        binding.rowBrowseSwipeLeftAction.setSelection(
            swipeActions.indexOf(BrowseSwipeDirection.LEFT.actionOf(settings)),
        )
        binding.rowBrowseSwipeRightAction.setSelection(
            swipeActions.indexOf(BrowseSwipeDirection.RIGHT.actionOf(settings)),
        )
    }

    private fun bindFileVisibilityRows(settings: AppSettings) {
        if (binding.rowAllFiles.isChecked != settings.allFiles) {
            Timber.d("GeneralSettings: rowAllFiles ${binding.rowAllFiles.isChecked} -> ${settings.allFiles}")
            binding.rowAllFiles.setCheckedSilently(settings.allFiles)
        }
        binding.layoutShowHiddenFiles.visibility = if (settings.allFiles) View.VISIBLE else View.GONE
        if (binding.rowShowHiddenFiles.isChecked != settings.showHiddenFiles) {
            binding.rowShowHiddenFiles.setCheckedSilently(settings.showHiddenFiles)
        }
        if (binding.rowShowSubfoldersAsItems.isChecked != settings.showSubfoldersAsItems) {
            binding.rowShowSubfoldersAsItems.setCheckedSilently(settings.showSubfoldersAsItems)
        }
    }

    // S0391: group toggles mirror the per-source flags - a group reads ON when any member is ON.
    private fun bindSourceGroupRows(settings: AppSettings) {
        val smbGroup = settings.smbEnabled
        val ftpGroup = settings.sftpEnabled || settings.ftpEnabled
        val cloudGroup = settings.googleDriveEnabled || settings.oneDriveEnabled || settings.dropboxEnabled
        if (binding.rowSourceSmb.isChecked != smbGroup) {
            binding.rowSourceSmb.setCheckedSilently(smbGroup)
        }
        if (binding.rowSourceFtp.isChecked != ftpGroup) {
            binding.rowSourceFtp.setCheckedSilently(ftpGroup)
        }
        if (binding.rowSourceCloud.isChecked != cloudGroup) {
            binding.rowSourceCloud.setCheckedSilently(cloudGroup)
        }
    }

    private fun bindThumbnailPreloadRows(settings: AppSettings) {
        binding.rowEnableThumbnailPreload.let { row ->
            if (row.isChecked != settings.enableThumbnailPreload) {
                row.setCheckedSilently(
                    settings.enableThumbnailPreload
                )
            }
        }
        binding.layoutThumbnailPreloadWifiOnly.visibility =
            if (settings.enableThumbnailPreload) View.VISIBLE else View.GONE
        binding.rowThumbnailPreloadWifiOnly.let { row ->
            if (row.isChecked != settings.thumbnailPreloadWifiOnly) {
                row.setCheckedSilently(
                    settings.thumbnailPreloadWifiOnly
                )
            }
        }
    }

    fun observeManualNetworkSyncState() {
        fragment.viewLifecycleOwner.collectOnLifecycle(viewModel.manualNetworkSyncState) { state ->
            if (state.inProgress) {
                binding.btnSyncNow.text = fragment.getString(R.string.cancel)
                showOrUpdateManualSyncProgressDialog(state.processedCount, state.totalCount)
                return@collectOnLifecycle
            }
            binding.btnSyncNow.text = fragment.getString(R.string.sync_now)
            dismissManualSyncProgressDialog()
            when {
                state.completed -> {
                    refreshLastSyncStatus()
                    Toast.makeText(
                        fragment.requireContext(),
                        fragment.getString(R.string.sync_completed_successfully, state.successCount),
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.clearManualNetworkSyncTerminalState()
                }
                state.cancelled -> {
                    Toast.makeText(
                        fragment.requireContext(),
                        R.string.dialog_file_operation_progress_btnCancel_text,
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.clearManualNetworkSyncTerminalState()
                }
                state.errorMessage != null -> {
                    Toast.makeText(
                        fragment.requireContext(),
                        fragment.getString(R.string.sync_failed),
                        Toast.LENGTH_LONG
                    ).show()
                    viewModel.clearManualNetworkSyncTerminalState()
                }
            }
        }
    }

    fun refreshLastSyncStatus() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val lastSyncTimestamp = viewModel.getLastNetworkSyncTimestamp()
            if (!fragment.isAdded || fragment.view == null) return@launch
            val statusText = if (lastSyncTimestamp == null) {
                fragment.getString(R.string.never_synced)
            } else {
                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    lastSyncTimestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
                fragment.getString(R.string.last_sync_time, relativeTime)
            }
            binding.tvSyncLastStatus?.text = statusText
        }
    }

    fun dismissManualSyncProgressDialog() {
        manualSyncProgressDialog?.dismiss()
        manualSyncProgressDialog = null
    }

    private fun showOrUpdateManualSyncProgressDialog(processedCount: Int, totalCount: Int) {
        if (!fragment.isAdded) return
        val dialog = manualSyncProgressDialog ?: MaterialProgressDialog(fragment.requireContext()).apply {
            setTitle(fragment.getString(R.string.sync_now))
            setProgressStyle(MaterialProgressDialog.STYLE_HORIZONTAL)
            show()
            manualSyncProgressDialog = this
        }
        val safeMax = maxOf(1, totalCount)
        dialog.max = safeMax
        dialog.progress = processedCount.coerceIn(0, safeMax)
        dialog.setMessage(
            if (totalCount > 0) {
                "${fragment.getString(R.string.sync_status_in_progress)} ($processedCount/$totalCount)"
            } else {
                fragment.getString(R.string.sync_status_in_progress)
            }
        )
    }
}
