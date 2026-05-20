package com.sza.fastmediasorter.ui.settings.helpers

import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.ui.dialog.MaterialProgressDialog
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

class GeneralSettingsObserversHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
    private val getIsUpdatingSpinner: () -> Boolean,
    private val setIsUpdatingSpinner: (Boolean) -> Unit,
) {
    private var manualSyncProgressDialog: MaterialProgressDialog? = null

    fun observeData() {
        fragment.viewLifecycleOwner.collectOnLifecycle(viewModel.settings) { settings ->
            val languagePosition = when (settings.language) {
                "en" -> 0; "ru" -> 1; "uk" -> 2; else -> 0
            }
            if (binding.spinnerLanguage.selectedItemPosition != languagePosition) {
                setIsUpdatingSpinner(true)
                binding.spinnerLanguage.setSelection(languagePosition, false)
                binding.spinnerLanguage.post { setIsUpdatingSpinner(false) }
            }

            setIsUpdatingSpinner(true)

            if (binding.rowEnableFavorites.isChecked != settings.enableFavorites)
                binding.rowEnableFavorites.setCheckedSilently(settings.enableFavorites)
            // S0028: Multi-window toggle observation. Lives in General → Interface (bottom).
            if (binding.rowAllowSeparateWindow.isChecked != settings.allowSeparateWindow)
                binding.rowAllowSeparateWindow.setCheckedSilently(settings.allowSeparateWindow)
            if (binding.rowSmallControls.isChecked != settings.showSmallControls)
                binding.rowSmallControls.setCheckedSilently(settings.showSmallControls)
            if (binding.rowCompactElements?.isChecked != settings.useCompactElements)
                binding.rowCompactElements?.setCheckedSilently(settings.useCompactElements)
            // S0160: resource ops overflow toggle
            if (binding.rowResourceOpsInOverflowMenu?.isChecked != settings.resourceOpsInOverflowMenu)
                binding.rowResourceOpsInOverflowMenu?.setCheckedSilently(settings.resourceOpsInOverflowMenu)

            if (binding.rowAllFiles.isChecked != settings.allFiles) {
                Timber.d("GeneralSettings: observeData updating rowAllFiles: ${binding.rowAllFiles.isChecked} -> ${settings.allFiles}")
                binding.rowAllFiles.setCheckedSilently(settings.allFiles)
            }

            binding.layoutShowHiddenFiles.visibility = if (settings.allFiles) View.VISIBLE else View.GONE
            if (binding.rowShowHiddenFiles.isChecked != settings.showHiddenFiles)
                binding.rowShowHiddenFiles.setCheckedSilently(settings.showHiddenFiles)
            if (binding.rowShowSubfoldersAsItems.isChecked != settings.showSubfoldersAsItems)
                binding.rowShowSubfoldersAsItems.setCheckedSilently(settings.showSubfoldersAsItems)

            if (binding.rowEnableBackgroundSync.isChecked != settings.enableBackgroundSync)
                binding.rowEnableBackgroundSync.setCheckedSilently(settings.enableBackgroundSync)

            binding.rowEnableThumbnailPreload?.let { row ->
                if (row.isChecked != settings.enableThumbnailPreload) row.setCheckedSilently(settings.enableThumbnailPreload)
            }
            binding.layoutThumbnailPreloadWifiOnly?.visibility =
                if (settings.enableThumbnailPreload) View.VISIBLE else View.GONE
            binding.rowThumbnailPreloadWifiOnly?.let { row ->
                if (row.isChecked != settings.thumbnailPreloadWifiOnly) row.setCheckedSilently(settings.thumbnailPreloadWifiOnly)
            }

            setIsUpdatingSpinner(false)

            val currentParallelism = binding.actvNetworkParallelism.text.toString().toIntOrNull()
            if (currentParallelism != settings.networkParallelism)
                binding.actvNetworkParallelism.setText(fragment.getString(R.string.number_format, settings.networkParallelism), false)

            val currentCacheSize = binding.actvCacheSizeLimit.text.toString().toIntOrNull()
            if (currentCacheSize != settings.cacheSizeMb)
                binding.actvCacheSizeLimit.setText(fragment.getString(R.string.number_format, settings.cacheSizeMb), false)

            val syncMinutes = settings.backgroundSyncIntervalHours * 60
            binding.actvSyncInterval?.let { syncIntervalView ->
                if (syncIntervalView.text.toString() != syncMinutes.toString())
                    syncIntervalView.setText(fragment.getString(R.string.number_format, syncMinutes), false)
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
                    Toast.makeText(fragment.requireContext(), fragment.getString(R.string.sync_completed_successfully, state.successCount), Toast.LENGTH_SHORT).show()
                    viewModel.clearManualNetworkSyncTerminalState()
                }
                state.cancelled -> {
                    Toast.makeText(fragment.requireContext(), R.string.dialog_file_operation_progress_btnCancel_text, Toast.LENGTH_SHORT).show()
                    viewModel.clearManualNetworkSyncTerminalState()
                }
                state.errorMessage != null -> {
                    Toast.makeText(fragment.requireContext(), fragment.getString(R.string.sync_failed), Toast.LENGTH_LONG).show()
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
                    lastSyncTimestamp, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
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
            if (totalCount > 0) "${fragment.getString(R.string.sync_status_in_progress)} ($processedCount/$totalCount)"
            else fragment.getString(R.string.sync_status_in_progress)
        )
    }
}
