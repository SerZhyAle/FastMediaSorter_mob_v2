package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.util.deleteFilesConfirmText
import com.sza.fastmediasorter.util.deleteGoesToTrash
import com.sza.fastmediasorter.util.showBoundToHost
import timber.log.Timber

@android.annotation.SuppressLint("SetTextI18n")
internal class BrowseDeleteDialogManager(
    private val activity: AppCompatActivity,
    private val callbacks: BrowseDialogHelper.DialogCallbacks
) {
    /**
     * Show delete-confirmation dialog for [files].
     *
     * @param overridePaths When non-null, forwarded to [BrowseDialogHelper.DialogCallbacks.onDeleteConfirmed]
     *   verbatim - used by the per-file overflow menu to delete a single file without
     *   touching the global multiselect. When null, the callback resolves to the current
     *   global multiselect.
     */
    fun showDeleteConfirmation(
        files: List<MediaFile>,
        resource: MediaResource?,
        settings: AppSettings,
        overridePaths: Set<String>? = null
    ) {
        val fileCount = files.size
        if (fileCount == 0) {
            return
        }

        if (resource?.type?.isNetworkResource == true) {
            val prefs = activity.getSharedPreferences("NetworkDeletePrefs", Context.MODE_PRIVATE)
            val prefKey = "dont_show_network_delete_${resource.id}"
            if (!prefs.getBoolean(prefKey, false)) {
                showNetworkDeleteConfirmation(files, resource, fileCount, prefKey, overridePaths)
                return
            }
        }

        val shouldConfirmDelete = settings.enableSafeMode && settings.confirmDelete
        if (!shouldConfirmDelete) {
            callbacks.onDeleteConfirmed(overridePaths)
            return
        }

        val dirCount = files.count { it.isDirectory }
        val toTrash = deleteGoesToTrash(settings.useTrash, files.map { it.path })
        val message = when {
            dirCount == 1 && fileCount == 1 -> {
                val folderName = files.first { it.isDirectory }.name
                val folderConfirm = if (toTrash) {
                    R.string.delete_folder_trash_confirm
                } else {
                    R.string.delete_folder_confirm
                }
                activity.getString(folderConfirm, folderName)
            }

            dirCount > 0 && dirCount == fileCount -> {
                if (toTrash) {
                    val foldersPlural = R.plurals.delete_n_folders_trash_confirm_plural
                    activity.resources.getQuantityString(foldersPlural, dirCount, dirCount)
                } else {
                    activity.getString(R.string.delete_n_folders_confirm, dirCount)
                }
            }

            else -> activity.resources.deleteFilesConfirmText(fileCount, toTrash)
        }

        Timber.d("S4770: browse delete confirm shown, fileCount=$fileCount dirCount=$dirCount")
        Timber.d("S4771: browse delete confirm toTrash=$toTrash")
        MaterialAlertDialogBuilder(
            activity,
            R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive
        )
            .setTitle(R.string.confirm_delete_title)
            .setMessage(message)
            .setPositiveButton(R.string.delete) { _, _ ->
                callbacks.onDeleteConfirmed(overridePaths)
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundToHost(activity)
    }

    private fun showNetworkDeleteConfirmation(
        files: List<MediaFile>,
        resource: MediaResource,
        fileCount: Int,
        prefKey: String,
        overridePaths: Set<String>?
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_network_delete_confirmation, null)
        val tvDeleteMessage = view.findViewById<android.widget.TextView>(R.id.tvDeleteMessage)
        val tvFilesList = view.findViewById<android.widget.TextView>(R.id.tvFilesList)
        val tvMoreFilesCount = view.findViewById<android.widget.TextView>(R.id.tvMoreFilesCount)
        val tvResourceInfo = view.findViewById<android.widget.TextView>(R.id.tvResourceInfo)
        val cbDontShowAgain = view.findViewById<android.widget.CheckBox>(R.id.cbDontShowAgain)

        tvDeleteMessage.text = activity.getString(R.string.delete_n_files_from_network_title, fileCount)
        val displayLimit = 5
        val filesToDisplay = files.take(displayLimit)
        tvFilesList.text = buildString {
            filesToDisplay.forEachIndexed { index, file ->
                append("• ").append(file.name)
                if (index < filesToDisplay.lastIndex) {
                    append('\n')
                }
            }
        }

        if (fileCount > displayLimit) {
            tvMoreFilesCount.visibility = android.view.View.VISIBLE
            tvMoreFilesCount.text = activity.getString(
                R.string.and_n_more_files_network,
                fileCount - displayLimit
            )
        }

        val resourceName = resource.name.ifBlank { "Unknown" }
        tvResourceInfo.text = "$resourceName\n${resource.path}"
        cbDontShowAgain.text = activity.getString(R.string.dont_show_again_for_resource)

        MaterialAlertDialogBuilder(
            activity,
            R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive
        )
            .setView(view)
            .setPositiveButton(R.string.delete_permanently) { _, _ ->
                if (cbDontShowAgain.isChecked) {
                    val prefs = activity.getSharedPreferences("NetworkDeletePrefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean(prefKey, true).apply()
                }
                callbacks.onDeleteConfirmed(overridePaths)
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundToHost(activity)
    }
}
