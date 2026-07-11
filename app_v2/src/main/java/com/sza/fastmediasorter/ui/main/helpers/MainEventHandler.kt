package com.sza.fastmediasorter.ui.main.helpers

import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.error.ErrorSeverity
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.addresource.AddResourceActivity
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.main.MainEvent
import com.sza.fastmediasorter.ui.main.MainViewModel
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.resourceeditor.ResourceEditorActivity
import com.sza.fastmediasorter.util.AppErrorNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** MainActivity event-bus dispatcher; extracted from observeData() to keep the host Activity below the 1000-LOC budget. */
internal class MainEventHandler(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val viewModel: MainViewModel,
    private val settingsRepository: SettingsRepository,
    private val passwordManager: ResourcePasswordManager,
    private val onOpenSettings: () -> Unit,
    private val onRecordLastPlayed: (Long) -> Unit,
) {

    fun handle(event: MainEvent) {
        when (event) {
            is MainEvent.ShowError -> showError(event.message, event.details)
            is MainEvent.ShowInfo -> showInfo(event.message, event.details)
            is MainEvent.ShowMessage ->
                Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
            is MainEvent.ShowResourceMessage ->
                Toast.makeText(activity, activity.getString(event.resId, *event.args), Toast.LENGTH_SHORT).show()
            is MainEvent.RequestPassword -> passwordManager.checkResourcePassword(
                resource = event.resource,
                forSlideshow = event.forSlideshow,
                onPasswordValidated = { resourceId, forSlideshow ->
                    viewModel.proceedAfterPasswordCheck(resourceId, forSlideshow)
                },
            )
            is MainEvent.NavigateToBrowse -> navigateSlideAnim(
                BrowseActivity.createIntent(activity, event.resourceId, event.skipAvailabilityCheck)
            )
            is MainEvent.NavigateToFavorites -> navigateSlideAnim(
                BrowseActivity.createIntent(activity, -100L /* FAVORITES_RESOURCE_ID */, true)
            )
            is MainEvent.NavigateToPlayerSlideshow -> {
                val intent = PlayerActivity.createPanelIntent(
                    activity, event.resourceId, initialIndex = 0, skipAvailabilityCheck = true,
                ).apply { putExtra("slideshow_mode", true) }
                onRecordLastPlayed(event.resourceId)
                navigateSlideAnim(intent)
            }
            is MainEvent.NavigateToPlayerRandomMusic -> {
                // Random music is audio playback - always 2D (immersive VR removed in S0241).
                val intent = PlayerActivity.createPanelIntent(
                    activity, event.resourceId, initialIndex = 0, skipAvailabilityCheck = true,
                    isPlaying = true, shuffleOnStart = true,
                )
                onRecordLastPlayed(event.resourceId)
                navigateSlideAnim(intent)
            }
            is MainEvent.NavigateToEditResource -> {
                val resource = viewModel.state.value.resources.find { it.id == event.resourceId }
                if (resource != null && !resource.accessPin.isNullOrBlank()) {
                    passwordManager.checkResourcePinForEdit(resource)
                } else {
                    activity.startActivity(ResourceEditorActivity.createEditIntent(activity, event.resourceId))
                }
            }
            is MainEvent.NavigateToAddResource -> activity.startActivity(
                AddResourceActivity.createIntent(activity, preselectedTab = event.preselectedTab)
            )
            is MainEvent.NavigateToAddResourceCopy -> {
                val resource = viewModel.state.value.resources.find { it.id == event.copyResourceId }
                val intent = ResourceEditorActivity.createCopyIntent(activity, resourceId = event.copyResourceId)
                if (resource != null && !resource.accessPin.isNullOrBlank()) {
                    passwordManager.checkResourcePin(resource) { activity.startActivity(intent) }
                } else {
                    activity.startActivity(intent)
                }
            }
            MainEvent.NavigateToSettings -> onOpenSettings()
            is MainEvent.ScanProgress -> {
                binding.scanProgressLayout.visibility = View.VISIBLE
                binding.tvScanDetail.text = activity.getString(R.string.files_scanned_count, event.scannedCount)
                event.currentFile?.let { fileName ->
                    binding.tvScanProgress.text = activity.getString(R.string.scanning_progress, fileName)
                }
            }
            MainEvent.ScanComplete -> binding.scanProgressLayout.visibility = View.GONE
            MainEvent.ConfirmRescanWithVirtualResources -> {
                if (activity.isFinishing || activity.isDestroyed) return
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.rescan_all_virtual_warning_title)
                    .setMessage(R.string.rescan_all_virtual_warning_message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.forceRescanAllResources() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            is MainEvent.ShareResourceFile -> shareResourceFile(event.filePath)
            is MainEvent.ShareCompanionConfigFile -> shareCompanionConfigFile(event.filePath)
        }
    }

    /** Show error: detailed copyable ScrollableTextDialog when showDetailedErrors is on, else a critical notifier. */
    private fun showError(message: String, details: String?) {
        activity.lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            if (settings.showDetailedErrors) {
                ScrollableTextDialog.show(
                    context = activity,
                    title = activity.getString(R.string.error),
                    message = message,
                    details = details
                )
            } else {
                AppErrorNotifier.show(
                    activity = activity,
                    message = message,
                    severity = ErrorSeverity.CRITICAL,
                    showDetailedErrors = false
                )
            }
        }
    }

    /** Show an info message: ScrollableTextDialog when showDetailedErrors is on, else a short toast. */
    private fun showInfo(message: String, details: String?) {
        activity.lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            if (settings.showDetailedErrors) {
                ScrollableTextDialog.show(
                    context = activity,
                    title = activity.getString(R.string.information),
                    message = message,
                    details = details
                )
            } else {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** S0422: share an exported resource file via the system share sheet with the vendor MIME type. */
    private fun shareResourceFile(filePath: String) {
        try {
            val file = java.io.File(filePath)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                activity, "${activity.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = com.sza.fastmediasorter.domain.model.ResourceShareFormat.MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.resource_share_export_title)))
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to share resource file")
            Toast.makeText(activity, R.string.resource_share_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** S0984: share an exported `.fmscfg` access file with the vendor MIME for a reliable app-to-app SEND. */
    private fun shareCompanionConfigFile(filePath: String) {
        try {
            val file = java.io.File(filePath)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                activity, "${activity.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = COMPANION_CONFIG_MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.sftp_share_export_title)))
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to share companion config file")
            Toast.makeText(activity, R.string.sftp_share_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateSlideAnim(intent: Intent) {
        activity.startActivity(intent)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private companion object {
        // Must stay in sync with the CompanionConfigImportActivity SEND intent-filter in the manifest.
        const val COMPANION_CONFIG_MIME = "application/vnd.fms.companion-config+json"
    }
}
