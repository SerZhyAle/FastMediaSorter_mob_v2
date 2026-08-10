package com.sza.fastmediasorter.ui.addresource

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.compat.ChromeOsCompat
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.core.util.formatFileSize
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.databinding.ActivityAddResourceBinding
import com.sza.fastmediasorter.domain.model.PermissionTask
import com.sza.fastmediasorter.domain.model.StorageVolumeInfo
import com.sza.fastmediasorter.ui.common.permissions.permissionRationale
import com.sza.fastmediasorter.ui.common.permissions.permissionRationaleShort
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager
import com.sza.fastmediasorter.util.showBoundToHost
import kotlinx.coroutines.launch
import timber.log.Timber

internal class AddResourceScanManager(
    private val activity: AddResourceActivity,
    private val binding: ActivityAddResourceBinding,
    private val viewModel: AddResourceViewModel,
    private val folderPickerLauncher: ActivityResultLauncher<Uri?>,
    private val mediaCapabilities: MediaCapabilities
) {

    // S0535: folder-picker sections go through the unified orchestrator + consolidated store.
    private val sectionsManager by lazy { CollapsibleSectionsManager(activity) }

    fun loadSshKeyFromFile(uri: Uri) {
        try {
            activity.contentResolver.openInputStream(uri)?.use { inputStream ->
                binding.etSftpPrivateKey.setText(inputStream.bufferedReader().use { it.readText() })
                Toast.makeText(activity, activity.getString(R.string.ssh_key_loaded), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load SSH key from file")
            Toast.makeText(activity, activity.getString(R.string.sftp_key_load_error), Toast.LENGTH_SHORT).show()
        }
    }

    fun handleSelectedFolderUri(uri: Uri, displayPath: String? = null) {
        Timber.i("Selected folder URI: $uri")
        if (uri.scheme == "content") {
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                Timber.d("Persistable permission granted for: $uri")
            } catch (e: SecurityException) {
                Timber.w(e, "Could not take persistable permission")
            }
        }
        val folderName = displayPath?.substringAfterLast("/") ?: uri.lastPathSegment ?: "Folder"
        Timber.d("Adding folder to resources list: name=$folderName, uri=$uri")
        viewModel.addManualFolder(uri, binding.etLocalPinCode.text?.toString()?.trim().takeUnless { it.isNullOrBlank() })
    }

    // ========== Folder Selection Dialog ==========

    fun showFolderSelectionDialog() {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_folder_selection, null)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.select_folder)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        val etManualPath = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etManualPath)

        val virtualButtons = listOf(
            R.id.btnVirtualRecent to LocalMediaScanner.VIRTUAL_PATH_RECENT,
            R.id.btnVirtualAllMusic to LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
            R.id.btnVirtualAllVideo to LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
            R.id.btnVirtualCameraPhotos to LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS,
            R.id.btnVirtualAllImages to LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
            R.id.btnVirtualAllDocs to LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS
        )

        fun applyVirtualButtonStates(existingVirtualPaths: Set<String>) {
            virtualButtons.forEach { (btnId, path) ->
                dialogView.findViewById<com.google.android.material.button.MaterialButton>(btnId)?.apply {
                    isEnabled = path !in existingVirtualPaths
                    alpha = if (isEnabled) 1f else 0.5f
                }
            }
        }

        applyVirtualButtonStates(emptySet())

        virtualButtons.forEach { (btnId, path) ->
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(btnId)?.setOnClickListener {
                viewModel.addVirtualResource(path)
                dialog.dismiss()
            }
        }

        activity.lifecycleScope.launch {
            applyVirtualButtonStates(viewModel.getExistingVirtualPaths())
        }

        if (!mediaCapabilities.supportsAudio) dialogView.findViewById<android.view.View>(R.id.btnVirtualAllMusic)?.isVisible = false
        if (!mediaCapabilities.supportsImages) {
            dialogView.findViewById<android.view.View>(R.id.btnVirtualCameraPhotos)?.isVisible = false
            dialogView.findViewById<android.view.View>(R.id.btnVirtualAllImages)?.isVisible = false
        }
        if (!mediaCapabilities.supportsDocuments) dialogView.findViewById<android.view.View>(R.id.btnVirtualAllDocs)?.isVisible = false

        val quickFolders = mapOf(
            R.id.btnRoot to "/storage/emulated/0",
            R.id.btnDCIM to "/storage/emulated/0/DCIM",
            R.id.btnPictures to "/storage/emulated/0/Pictures",
            R.id.btnDownload to "/storage/emulated/0/Download",
            R.id.btnDocuments to "/storage/emulated/0/Documents",
            R.id.btnWhatsApp to "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media",
            R.id.btnTelegram to "/storage/emulated/0/Android/media/org.telegram.messenger/Telegram",
            R.id.btnInstagram to "/storage/emulated/0/Android/media/com.instagram.android/Instagram"
        )

        val useSafOnly = ChromeOsCompat.needsSafFolderPicker(activity)

        quickFolders.forEach { (buttonId, path) ->
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(buttonId)?.setOnClickListener {
                Timber.i("Quick select: $path")
                if (buttonId == R.id.btnRoot) {
                    dialog.dismiss()
                    showFolderBrowserDialog(path)
                } else {
                    if (useSafOnly) folderPickerLauncher.launch(null)
                    else selectFolderByPath(path, dialog)
                }
            }
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnValidatePath)?.setOnClickListener {
            selectFolderByPath(etManualPath?.text?.toString()?.trim() ?: "", dialog)
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBrowseWithSAF)?.setOnClickListener {
            Timber.i("Using SAF picker")
            dialog.dismiss()
            folderPickerLauncher.launch(null)
        }

        setupCollapsibleFolderSections(dialogView)
        populateRemovableVolumes(dialogView, dialog, useSafOnly)

        dialog.showBoundToHost(activity)
    }

    /**
     * S1378: one button per connected removable volume, or nothing at all.
     *
     * The whole section - header included - stays gone while the registry reports no removable
     * volume, per the owner's §3.4 decision that an empty block is worse than no block.
     */
    private fun populateRemovableVolumes(dialogView: android.view.View, dialog: Dialog, useSafOnly: Boolean) {
        val header = dialogView.findViewById<CollapsibleSectionHeader>(R.id.headerRemovableVolumes) ?: return
        val container = dialogView.findViewById<android.widget.LinearLayout>(R.id.containerRemovableVolumes) ?: return
        activity.lifecycleScope.launch {
            Timber.d("S1378: building the removable-media section")
            val volumes = viewModel.getRemovableVolumes()
            if (volumes.isEmpty()) return@launch
            volumes.forEach { container.addView(removableVolumeButton(it, dialog, useSafOnly)) }
            header.isVisible = true
            container.isVisible = true
        }
    }

    private fun removableVolumeButton(
        volume: StorageVolumeInfo,
        dialog: Dialog,
        useSafOnly: Boolean,
    ): com.google.android.material.button.MaterialButton {
        val freeSpace = formatFileSize(volume.availableBytes)
        // Styled through a ContextThemeWrapper rather than the plain Material attribute: these
        // buttons sit next to the quick-folder ones and have to carry the project's own outlined
        // style, which only a themed context applies to a view built in code.
        val styled = android.view.ContextThemeWrapper(activity, R.style.Widget_FastMediaSorter_Button_Outlined)
        return com.google.android.material.button.MaterialButton(styled, null, 0).apply {
            text = activity.getString(R.string.removable_volume_button_label, volume.displayName, freeSpace)
            contentDescription = activity.getString(
                R.string.removable_volume_button_description,
                volume.displayName,
                freeSpace
            )
            icon = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_resource_removable)
            // Same input contract as the quick-folder buttons next to it: reachable by keyboard and
            // D-pad, not only by touch.
            isFocusable = true
            isClickable = true
            setOnClickListener { onRemovableVolumeSelected(volume, dialog, useSafOnly) }
        }
    }

    /**
     * A volume the app can read directly is opened as a path; anything else goes through the system
     * folder request, and strategic §3.1 requires the user to be told why before that dialog appears.
     */
    private fun onRemovableVolumeSelected(volume: StorageVolumeInfo, dialog: Dialog, useSafOnly: Boolean) {
        val mountPath = volume.mountPath
        val readable = !useSafOnly && mountPath != null && java.io.File(mountPath).canRead()
        if (readable) {
            selectFolderByPath(mountPath, dialog)
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setMessage(activity.getString(R.string.removable_volume_access_request, volume.displayName))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                dialog.dismiss()
                folderPickerLauncher.launch(null)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundToHost(activity)
    }

    /**
     * Wires the two top groups of the folder dialog to the unified collapsible orchestrator.
     * Short dialog -> default expanded (research 02 D4); state persists in the consolidated store.
     */
    private fun setupCollapsibleFolderSections(dialogView: android.view.View) {
        val sections = listOf(
            Triple(R.id.headerSpecialFolders, R.id.containerSpecialFolders, "folder_selection__special"),
            Triple(R.id.headerQuickFolders, R.id.containerQuickFolders, "folder_selection__quick"),
            Triple(R.id.headerRemovableVolumes, R.id.containerRemovableVolumes, "folder_selection__removable"),
        )
        sections.forEach { (headerId, containerId, key) ->
            val header = dialogView.findViewById<CollapsibleSectionHeader>(headerId) ?: return@forEach
            val container = dialogView.findViewById<android.view.View>(containerId) ?: return@forEach
            sectionsManager.register(header, container, key, defaultExpanded = true)
        }
    }

    fun selectFolderByPath(path: String, dialog: Dialog) {
        if (ChromeOsCompat.needsSafFolderPicker(activity)) {
            Timber.d("AddResourceScanManager: redirecting to SAF picker on Chrome OS")
            folderPickerLauncher.launch(null)
            return
        }
        Timber.w("FOLDER_PICKER: Attempting to select path: $path")
        if (path.isBlank()) {
            Toast.makeText(activity, R.string.folder_path_hint, Toast.LENGTH_SHORT).show()
            return
        }
        val dir = java.io.File(path)
        val isAndroidMedia = path.contains("/Android/media/")
        val hasAllFilesAccess = PermissionHelper.hasAllFilesAccessPermission(activity)

        when {
            !dir.exists() && !isAndroidMedia -> {
                Timber.e("FOLDER_PICKER: Path does not exist: $path")
                Toast.makeText(activity, activity.getString(R.string.folder_not_found), Toast.LENGTH_SHORT).show()
            }
            !dir.exists() && isAndroidMedia && !hasAllFilesAccess -> {
                Timber.e("FOLDER_PICKER: Android/media path requires MANAGE_EXTERNAL_STORAGE: $path")
                // S1436: the toast keeps the one short line policy allows it; the dialog that follows
                // carries the paragraph, and both now come from the same registry row.
                val short = activity.permissionRationaleShort(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                Toast.makeText(activity, short, Toast.LENGTH_LONG).show()
                showAllFilesAccessPermissionDialog()
            }
            !dir.exists() && isAndroidMedia && hasAllFilesAccess -> {
                Timber.w("FOLDER_PICKER: Adding Android/media path with permission: $path")
                handleSelectedFolderUri(Uri.fromFile(dir), path)
                Toast.makeText(activity, activity.getString(R.string.android_media_folder_added_warning), Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            !dir.isDirectory -> {
                Toast.makeText(activity, activity.getString(R.string.not_a_folder), Toast.LENGTH_SHORT).show()
            }
            !dir.canRead() && !isAndroidMedia -> {
                Toast.makeText(activity, activity.getString(R.string.cannot_read_folder), Toast.LENGTH_SHORT).show()
            }
            !dir.canRead() && isAndroidMedia && hasAllFilesAccess -> {
                Timber.w("FOLDER_PICKER: Adding non-readable Android/media path with permission: $path")
                handleSelectedFolderUri(Uri.fromFile(dir), path)
                Toast.makeText(activity, activity.getString(R.string.android_media_folder_added_warning), Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            else -> {
                Timber.i("FOLDER_PICKER: Path valid, selecting: $path")
                handleSelectedFolderUri(Uri.fromFile(dir), path)
                Toast.makeText(activity, activity.getString(R.string.folder_selected_successfully, dir.name), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }

    // ========== Folder Browser ==========

    fun showFolderBrowserDialog(startPath: String = "/storage/emulated/0") {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_folder_browser, null)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.browse_folders)
            .setView(dialogView)
            .create()

        val tvCurrentPath = dialogView.findViewById<android.widget.TextView>(R.id.tvCurrentPath)
        val rvFolders = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvFolders)
        val btnSelectCurrent = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSelectCurrent)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

        var currentPath = startPath
        tvCurrentPath?.text = currentPath

        val folders = mutableListOf<String>()
        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val view = activity.layoutInflater.inflate(R.layout.item_folder, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {}
            }
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val folderName = folders[position]
                holder.itemView.findViewById<android.widget.TextView>(R.id.tvFolderName)?.text = folderName
                holder.itemView.setOnClickListener {
                    if (folderName == "..") {
                        val parent = java.io.File(currentPath).parent
                        if (parent != null && parent.startsWith("/storage/emulated/0")) {
                            currentPath = parent
                            loadFolders(currentPath, folders, this)
                            tvCurrentPath?.text = currentPath
                        }
                    } else {
                        currentPath = "$currentPath/$folderName"
                        loadFolders(currentPath, folders, this)
                        tvCurrentPath?.text = currentPath
                    }
                }
            }
            override fun getItemCount() = folders.size
        }

        rvFolders?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
        rvFolders?.adapter = adapter
        loadFolders(currentPath, folders, adapter)

        btnSelectCurrent?.setOnClickListener { selectFolderByPath(currentPath, dialog) }
        btnCancel?.setOnClickListener { dialog.dismiss() }

        dialog.showBoundToHost(activity)
    }

    private fun loadFolders(path: String, folders: MutableList<String>, adapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>) {
        folders.clear()
        try {
            val dir = java.io.File(path)
            if (path != "/storage/emulated/0" && dir.parent != null) folders.add("..")
            val subDirs = dir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
                ?.sortedBy { it.name } ?: emptyList()
            folders.addAll(subDirs.map { it.name })
            adapter.notifyDataSetChanged()
            Timber.d("Loaded ${folders.size} folders from $path")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load folders from $path")
            Toast.makeText(activity, activity.getString(R.string.cannot_read_folder), Toast.LENGTH_SHORT).show()
        }
    }

    // ========== Permission Dialog ==========

    fun showAllFilesAccessPermissionDialog() {
        // S1436: the shell is unchanged - the wording is the registry's, with the folder-picking
        // addendum appended, and this file no longer keeps a copy of either.
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.permissions_required_title)
            .setMessage(
                activity.permissionRationale(
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                    PermissionTask.FOLDER_PICKING,
                )
            )
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                PermissionHelper.requestAllFilesAccessPermission(activity)
            }
            .setNeutralButton(R.string.continue_limited) { _, _ ->
                Toast.makeText(activity, R.string.folder_selection_limitations, Toast.LENGTH_LONG).show()
                folderPickerLauncher.launch(null)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setCancelable(true)
            .showBoundToHost(activity)
    }
}
