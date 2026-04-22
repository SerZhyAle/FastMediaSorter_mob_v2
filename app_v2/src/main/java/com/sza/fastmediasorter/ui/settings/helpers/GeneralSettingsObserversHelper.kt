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

            if (binding.switchPreventSleep.isChecked != settings.preventSleep)
                binding.switchPreventSleep.isChecked = settings.preventSleep
            if (binding.switchEnableFavorites.isChecked != settings.enableFavorites)
                binding.switchEnableFavorites.isChecked = settings.enableFavorites
            if (binding.switchSmallControls.isChecked != settings.showSmallControls)
                binding.switchSmallControls.isChecked = settings.showSmallControls
            if (binding.switchCompactElements?.isChecked != settings.useCompactElements)
                binding.switchCompactElements?.isChecked = settings.useCompactElements

            if (binding.switchAllFiles.isChecked != settings.allFiles) {
                Timber.d("GeneralSettings: observeData updating switchAllFiles: ${binding.switchAllFiles.isChecked} -> ${settings.allFiles}")
                binding.switchAllFiles.isChecked = settings.allFiles
            }

            binding.layoutShowHiddenFiles.visibility = if (settings.allFiles) View.VISIBLE else View.GONE
            if (binding.switchShowHiddenFiles.isChecked != settings.showHiddenFiles)
                binding.switchShowHiddenFiles.isChecked = settings.showHiddenFiles
            if (binding.switchShowSubfoldersAsItems.isChecked != settings.showSubfoldersAsItems)
                binding.switchShowSubfoldersAsItems.isChecked = settings.showSubfoldersAsItems
            if (binding.switchDefaultRememberFileList.isChecked != settings.defaultRememberFileList)
                binding.switchDefaultRememberFileList.isChecked = settings.defaultRememberFileList

            if (binding.switchEnableSafeMode.isChecked != settings.enableSafeMode)
                binding.switchEnableSafeMode.isChecked = settings.enableSafeMode
            binding.layoutConfirmDelete.visibility = if (settings.enableSafeMode) View.VISIBLE else View.GONE
            binding.layoutConfirmMove.visibility = if (settings.enableSafeMode) View.VISIBLE else View.GONE
            if (binding.switchConfirmDelete.isChecked != settings.confirmDelete)
                binding.switchConfirmDelete.isChecked = settings.confirmDelete
            if (binding.switchConfirmMove.isChecked != settings.confirmMove)
                binding.switchConfirmMove.isChecked = settings.confirmMove
            if (binding.switchEnableBackgroundSync.isChecked != settings.enableBackgroundSync)
                binding.switchEnableBackgroundSync.isChecked = settings.enableBackgroundSync

            binding.switchEnableThumbnailPreload?.let { sw ->
                if (sw.isChecked != settings.enableThumbnailPreload) sw.isChecked = settings.enableThumbnailPreload
            }
            binding.layoutThumbnailPreloadWifiOnly?.visibility =
                if (settings.enableThumbnailPreload) View.VISIBLE else View.GONE
            binding.switchThumbnailPreloadWifiOnly?.let { sw ->
                if (sw.isChecked != settings.thumbnailPreloadWifiOnly) sw.isChecked = settings.thumbnailPreloadWifiOnly
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
                    Toast.makeText(fragment.requireContext(), fragment.getString(R.string.sync_failed, state.errorMessage), Toast.LENGTH_LONG).show()
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
