package com.sza.fastmediasorter.ui.main.helpers

import android.content.Intent
import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.ui.addresource.AddResourceActivity
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.main.MainEvent
import com.sza.fastmediasorter.ui.main.MainViewModel
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.resourceeditor.ResourceEditorActivity

/** MainActivity event-bus dispatcher; extracted from observeData() to keep the host Activity below the 1000-LOC budget. */
internal class MainEventHandler(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val viewModel: MainViewModel,
    private val passwordManager: ResourcePasswordManager,
    private val onShowError: (String, String?) -> Unit,
    private val onShowInfo: (String, String?) -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onRecordLastPlayed: (Long) -> Unit,
) {

    fun handle(event: MainEvent) {
        when (event) {
            is MainEvent.ShowError -> onShowError(event.message, event.details)
            is MainEvent.ShowInfo -> onShowInfo(event.message, event.details)
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

    private fun navigateSlideAnim(intent: Intent) {
        activity.startActivity(intent)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}
