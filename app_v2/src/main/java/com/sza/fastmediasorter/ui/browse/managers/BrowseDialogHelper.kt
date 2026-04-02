package com.sza.fastmediasorter.ui.browse.managers

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogFilterBinding
import com.sza.fastmediasorter.databinding.DialogRenameMultipleBinding
import com.sza.fastmediasorter.databinding.ItemRenameFileBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.ui.dialog.ErrorDialog
import com.sza.fastmediasorter.ui.dialog.RenameDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Manages all dialog creation and user interactions in BrowseActivity.
 * Handles filter, sort, rename, copy, move, and delete confirmation dialogs.
 */
@android.annotation.SuppressLint("SetTextI18n")
class BrowseDialogHelper(
    private val activity: AppCompatActivity,
    private val callbacks: DialogCallbacks
) {
    
    interface DialogCallbacks {
        fun onFilterApplied(filter: FileFilter?)
        fun onSortModeSelected(sortMode: SortMode)
        fun onRenameConfirmed(oldName: String, newName: String)
        fun onRenameMultipleConfirmed(files: List<Pair<String, String>>)
        fun onDirectoryRenameConfirmed(oldPath: String, newName: String)
        fun onCopyDestinationSelected(destinationPath: String)
        fun onMoveDestinationSelected(destinationPath: String)
        fun onDeleteConfirmed(fileCount: Int)
        fun onCloudSignInRequested(provider: com.sza.fastmediasorter.data.cloud.CloudProvider)
        fun saveUndoOperation(undoOp: UndoOperation)
        fun reloadFiles()
        fun updateFile(oldPath: String, newFile: MediaFile)
        fun setIgnoringFileChanges(ignoring: Boolean)
        fun createMediaFileFromFile(file: File): MediaFile
        fun getFileOperationUseCase(): FileOperationUseCase
        fun getResourceName(): String?
        fun getLifecycleOwner(): LifecycleOwner
    }
    
    fun initialize() {
        // No initialization needed
    }
    
    fun cleanup() {
        // Dismiss any open dialogs if needed
    }
    
    fun showFilterDialog(currentFilter: FileFilter?, allowedMediaTypes: Set<MediaType>? = null) {
        val dialogBinding = DialogFilterBinding.inflate(LayoutInflater.from(activity))
        
        // Pre-fill current filter values
        dialogBinding.etFilterName.setText(currentFilter?.nameContains ?: "")
        
        // Media type checkboxes - only show types allowed by resource AND supported by flavor
        val allowed = allowedMediaTypes ?: MediaType.entries.toSet()
        val allTypesSelected = currentFilter?.mediaTypes == null
        
        // Configure each checkbox: hide if not allowed by resource OR not supported by flavor
        dialogBinding.cbFilterImage.apply {
            val vis = if (MediaType.IMAGE in allowed && BuildConfig.SUPPORT_IMAGES) android.view.View.VISIBLE else android.view.View.GONE
            (parent as android.view.View).visibility = vis
            visibility = vis
            isChecked = MediaType.IMAGE in allowed && BuildConfig.SUPPORT_IMAGES && (allTypesSelected || currentFilter?.mediaTypes?.contains(MediaType.IMAGE) == true)
        }
        dialogBinding.cbFilterVideo.apply {
            val vis = if (MediaType.VIDEO in allowed && BuildConfig.SUPPORT_VIDEO) android.view.View.VISIBLE else android.view.View.GONE
            (parent as android.view.View).visibility = vis
            visibility = vis
            isChecked = MediaType.VIDEO in allowed && BuildConfig.SUPPORT_VIDEO && (allTypesSelected || currentFilter?.mediaTypes?.contains(MediaType.VIDEO) == true)
        }
        dialogBinding.cbFilterAudio.apply {
            val vis = if (MediaType.AUDIO in allowed && BuildConfig.SUPPORT_AUDIO) android.view.View.VISIBLE else android.view.View.GONE
            (parent as android.view.View).visibility = vis
            visibility = vis
            isChecked = MediaType.AUDIO in allowed && BuildConfig.SUPPORT_AUDIO && (allTypesSelected || currentFilter?.mediaTypes?.contains(MediaType.AUDIO) == true)
        }
        dialogBinding.cbFilterGif.apply {
            val vis = if (MediaType.GIF in allowed && BuildConfig.SUPPORT_IMAGES) android.view.View.VISIBLE else android.view.View.GONE
            (parent as android.view.View).visibility = vis
            visibility = vis
            isChecked = MediaType.GIF in allowed && BuildConfig.SUPPORT_IMAGES && (allTypesSelected || currentFilter?.mediaTypes?.contains(MediaType.GIF) == true)
        }
        dialogBinding.cbFilterText.apply {
            val vis = if (MediaType.TEXT in allowed && BuildConfig.SUPPORT_DOCUMENTS) android.view.View.VISIBLE else android.view.View.GONE
            (parent as android.view.View).visibility = vis
            visibility = vis
            isChecked = MediaType.TEXT in allowed && BuildConfig.SUPPORT_DOCUMENTS && (allTypesSelected || currentFilter?.mediaTypes?.contains(MediaType.TEXT) == true)
        }
        dialogBinding.cbFilterPdf.apply {
            val vis = if (MediaType.PDF in allowed && BuildConfig.SUPPORT_DOCUMENTS) android.view.View.VISIBLE else android.view.View.GONE
            (parent as android.view.View).visibility = vis
            visibility = vis
            isChecked = MediaType.PDF in allowed && BuildConfig.SUPPORT_DOCUMENTS && (allTypesSelected || currentFilter?.mediaTypes?.contains(MediaType.PDF) == true)
        }
        dialogBinding.cbFilterEpub.apply {
            val vis = if (MediaType.EPUB in allowed && BuildConfig.ENABLE_EPUB) android.view.View.VISIBLE else android.view.View.GONE
            (parent as android.view.View).visibility = vis
            visibility = vis
            isChecked = MediaType.EPUB in allowed && BuildConfig.ENABLE_EPUB && (allTypesSelected || currentFilter?.mediaTypes?.contains(MediaType.EPUB) == true)
        }
        
        // Date pickers
        var minDate = currentFilter?.minDate
        var maxDate = currentFilter?.maxDate
        
        if (minDate != null) {
            dialogBinding.etMinDate.setText(formatDate(minDate))
        }
        if (maxDate != null) {
            dialogBinding.etMaxDate.setText(formatDate(maxDate))
        }
        
        dialogBinding.etMinDate.setOnClickListener {
            showDatePicker(minDate) { selectedDate ->
                minDate = selectedDate
                dialogBinding.etMinDate.setText(formatDate(selectedDate))
            }
        }
        
        dialogBinding.etMaxDate.setOnClickListener {
            showDatePicker(maxDate) { selectedDate ->
                maxDate = selectedDate
                dialogBinding.etMaxDate.setText(formatDate(selectedDate))
            }
        }
        
        // Size filters
        currentFilter?.minSizeMb?.let {
            dialogBinding.etMinSize.setText(activity.getString(R.string.string_format, it.toString()))
        }
        currentFilter?.maxSizeMb?.let {
            dialogBinding.etMaxSize.setText(activity.getString(R.string.string_format, it.toString()))
        }
        
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.filter)
            .setView(dialogBinding.root)
            .create()
        
        dialogBinding.btnClearFilter.setOnClickListener {
            callbacks.onFilterApplied(null)
            dialog.dismiss()
        }
        
        dialogBinding.btnCancelFilter.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.btnApplyFilter.setOnClickListener {
            val nameFilter = dialogBinding.etFilterName.text?.toString()?.trim()
            val minSizeText = dialogBinding.etMinSize.text?.toString()?.trim()
            val maxSizeText = dialogBinding.etMaxSize.text?.toString()?.trim()
            
            // Collect selected media types (only from visible checkboxes)
            val selectedTypes = mutableSetOf<MediaType>()
            if (dialogBinding.cbFilterImage.isChecked && dialogBinding.cbFilterImage.visibility == android.view.View.VISIBLE) selectedTypes.add(MediaType.IMAGE)
            if (dialogBinding.cbFilterVideo.isChecked && dialogBinding.cbFilterVideo.visibility == android.view.View.VISIBLE) selectedTypes.add(MediaType.VIDEO)
            if (dialogBinding.cbFilterAudio.isChecked && dialogBinding.cbFilterAudio.visibility == android.view.View.VISIBLE) selectedTypes.add(MediaType.AUDIO)
            if (dialogBinding.cbFilterGif.isChecked && dialogBinding.cbFilterGif.visibility == android.view.View.VISIBLE) selectedTypes.add(MediaType.GIF)
            if (dialogBinding.cbFilterText.isChecked && dialogBinding.cbFilterText.visibility == android.view.View.VISIBLE) selectedTypes.add(MediaType.TEXT)
            if (dialogBinding.cbFilterPdf.isChecked && dialogBinding.cbFilterPdf.visibility == android.view.View.VISIBLE) selectedTypes.add(MediaType.PDF)
            if (dialogBinding.cbFilterEpub.isChecked && dialogBinding.cbFilterEpub.visibility == android.view.View.VISIBLE) selectedTypes.add(MediaType.EPUB)
            
            // If all allowed types selected, set mediaTypes to null (no filter)
            val allAllowedSelected = selectedTypes == allowed
            
            val filter = FileFilter(
                nameContains = nameFilter?.ifBlank { null },
                minDate = minDate,
                maxDate = maxDate,
                minSizeMb = minSizeText?.toFloatOrNull(),
                maxSizeMb = maxSizeText?.toFloatOrNull(),
                mediaTypes = if (allAllowedSelected) null else selectedTypes.ifEmpty { null }
            )
            
            callbacks.onFilterApplied(if (filter.isEmpty()) null else filter)
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showDatePicker(currentDate: Long?, onDateSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        if (currentDate != null) {
            calendar.timeInMillis = currentDate
        }
        
        DatePickerDialog(
            activity,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                onDateSelected(calendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    /** Public utility to format date for display in filter summaries */
    fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return format.format(Date(timestamp))
    }
    
    fun showSortDialog(currentSortMode: SortMode) {
        val sortModes = SortMode.values().filter { 
            it != SortMode.DATE_TAKEN_ASC && it != SortMode.DATE_TAKEN_DESC 
        }
        
        val dialogBinding = com.sza.fastmediasorter.databinding.DialogSortBinding.inflate(LayoutInflater.from(activity))
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.sort_by_title)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            
        dialogBinding.rvSortOptions.layoutManager = androidx.recyclerview.widget.GridLayoutManager(activity, 2)
        dialogBinding.rvSortOptions.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sort_option, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }
            
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val mode = sortModes[position]
                val button = holder.itemView as com.google.android.material.button.MaterialButton
                button.text = getSortModeName(mode)
                
                // Highlight selected mode
                if (mode == currentSortMode) {
                    val colorPrimaryContainer = com.google.android.material.color.MaterialColors.getColor(button, com.google.android.material.R.attr.colorPrimaryContainer)
                    val colorOnPrimaryContainer = com.google.android.material.color.MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnPrimaryContainer)
                    button.setBackgroundColor(colorPrimaryContainer)
                    button.setTextColor(colorOnPrimaryContainer)
                } else {
                    val colorOnSurface = com.google.android.material.color.MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurface)
                    button.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    button.setTextColor(colorOnSurface)
                }
                
                button.setOnClickListener {
                    callbacks.onSortModeSelected(mode)
                    dialog.dismiss()
                }
            }
            
            override fun getItemCount() = sortModes.size
        }
        
        dialog.show()
    }
    
    private fun getSortModeName(mode: SortMode): String {
        return when (mode) {
            SortMode.MANUAL -> activity.getString(R.string.sort_mode_manual)
            SortMode.NAME_ASC -> activity.getString(R.string.sort_mode_name_asc)
            SortMode.NAME_DESC -> activity.getString(R.string.sort_mode_name_desc)
            SortMode.DATE_ASC -> activity.getString(R.string.sort_mode_date_asc)
            SortMode.DATE_DESC -> activity.getString(R.string.sort_mode_date_desc)
            SortMode.SIZE_ASC -> activity.getString(R.string.sort_mode_size_asc)
            SortMode.SIZE_DESC -> activity.getString(R.string.sort_mode_size_desc)
            SortMode.TYPE_ASC -> activity.getString(R.string.sort_mode_type_asc)
            SortMode.TYPE_DESC -> activity.getString(R.string.sort_mode_type_desc)
            SortMode.ARTIST_ASC -> activity.getString(R.string.sort_mode_artist_asc)
            SortMode.ARTIST_DESC -> activity.getString(R.string.sort_mode_artist_desc)
            SortMode.TITLE_ASC -> activity.getString(R.string.sort_mode_title_asc)
            SortMode.TITLE_DESC -> activity.getString(R.string.sort_mode_title_desc)
            SortMode.DURATION_ASC -> activity.getString(R.string.sort_mode_duration_asc)
            SortMode.DURATION_DESC -> activity.getString(R.string.sort_mode_duration_desc)
            SortMode.DATE_TAKEN_ASC -> activity.getString(R.string.sort_mode_date_taken_asc)
            SortMode.DATE_TAKEN_DESC -> activity.getString(R.string.sort_mode_date_taken_desc)
            SortMode.RANDOM -> activity.getString(R.string.sort_mode_random)
        }
    }
    
    fun showDeleteConfirmation(
        files: List<MediaFile>, 
        resource: com.sza.fastmediasorter.domain.model.MediaResource?, 
        settings: AppSettings
    ) {
        val fileCount = files.size
        if (fileCount == 0) return

        val isNetwork = resource?.type?.isNetworkResource == true
        
        if (isNetwork) {
            val prefs = activity.getSharedPreferences("NetworkDeletePrefs", Context.MODE_PRIVATE)
            val prefKey = "dont_show_network_delete_${resource?.id ?: 0}"
            val dontShowAgain = prefs.getBoolean(prefKey, false)
            
            if (!dontShowAgain) {
                showNetworkDeleteConfirmation(files, resource, fileCount, prefKey)
                return
            }
        }
        
        // Standard check
        val shouldConfirmDelete = settings.enableSafeMode && settings.confirmDelete
        
        if (shouldConfirmDelete) {
            val dirCount = files.count { it.isDirectory }
            val message = when {
                dirCount == 1 && fileCount == 1 -> {
                    val folderName = files.first { it.isDirectory }.name
                    activity.getString(R.string.delete_folder_confirm, folderName)
                }
                dirCount > 0 && dirCount == fileCount -> {
                    activity.getString(R.string.delete_n_folders_confirm, dirCount)
                }
                else -> activity.getString(R.string.confirm_delete_message, fileCount)
            }
            AlertDialog.Builder(activity)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(message)
                .setPositiveButton(R.string.delete) { _, _ ->
                    callbacks.onDeleteConfirmed(fileCount)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            // Skip confirmation - execute immediately
            callbacks.onDeleteConfirmed(fileCount)
        }
    }
    
    private fun showNetworkDeleteConfirmation(
        files: List<MediaFile>, 
        resource: com.sza.fastmediasorter.domain.model.MediaResource?, 
        fileCount: Int,
        prefKey: String
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
        val sb = java.lang.StringBuilder()
        filesToDisplay.forEachIndexed { index, file ->
            sb.append("• ").append(file.name)
            if (index < filesToDisplay.size - 1) sb.append("\n")
        }
        tvFilesList.text = sb.toString()
        
        if (fileCount > displayLimit) {
            tvMoreFilesCount.visibility = android.view.View.VISIBLE
            tvMoreFilesCount.text = activity.getString(R.string.and_n_more_files_network, fileCount - displayLimit)
        }
        
        val resourceName = resource?.name ?: "Unknown"
        val basePath = resource?.path ?: ""
        tvResourceInfo.text = "$resourceName\n$basePath"
        cbDontShowAgain.text = activity.getString(R.string.dont_show_again_for_resource)
        
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(R.string.delete_permanently) { _, _ ->
                if (cbDontShowAgain.isChecked) {
                    val prefs = activity.getSharedPreferences("NetworkDeletePrefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean(prefKey, true).apply()
                }
                callbacks.onDeleteConfirmed(fileCount)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            
        // Make positive button text error color
        val colorError = com.google.android.material.color.MaterialColors.getColor(view, androidx.appcompat.R.attr.colorError)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(colorError)
    }
    
    fun showErrorDialog(message: String, details: String?) {
        val dialogBuilder = AlertDialog.Builder(activity)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
        
        // Add "Show Details" button if details are available
        if (!details.isNullOrBlank()) {
            dialogBuilder.setNeutralButton(R.string.show_details) { _, _ ->
                showErrorDetailsDialog(details)
            }
        }
        
        dialogBuilder.show()
    }
    
    private fun showErrorDetailsDialog(details: String) {
        ErrorDialog.show(
            context = activity,
            title = activity.getString(R.string.error_details_title),
            message = details
        )
    }
    
    fun showCloudAuthenticationDialog(provider: com.sza.fastmediasorter.data.cloud.CloudProvider, resourceName: String) {
        val providerName = when (provider) {
            com.sza.fastmediasorter.data.cloud.CloudProvider.GOOGLE_DRIVE -> "Google Drive"
            com.sza.fastmediasorter.data.cloud.CloudProvider.DROPBOX -> "Dropbox"
            com.sza.fastmediasorter.data.cloud.CloudProvider.ONEDRIVE -> "OneDrive"
        }
        
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.authentication_required))
            .setMessage(activity.getString(R.string.cloud_auth_dialog_message, resourceName))
            .setPositiveButton(activity.getString(R.string.sign_in_now)) { _, _ ->
                callbacks.onCloudSignInRequested(provider)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(activity.getString(R.string.copy_error)) { _, _ ->
                copyToClipboard("$providerName authentication required for $resourceName")
            }
            .show()
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Error Details", text)
        clipboard.setPrimaryClip(clip)
    }
    
    fun showRenameDialog(selectedFiles: List<MediaFile>) {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(activity, R.string.no_files_selected, Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedFiles.size == 1 && selectedFiles.first().isDirectory) {
            showRenameDirectoryDialog(selectedFiles.first())
        } else if (selectedFiles.size == 1) {
            showRenameSingleDialog(selectedFiles.first().path)
        } else {
            showRenameMultipleDialog(selectedFiles.map { it.path })
        }
    }

    private fun showRenameDirectoryDialog(file: MediaFile) {
        val currentName = file.path.trimEnd('/').substringAfterLast('/')
        val editText = android.widget.EditText(activity).apply {
            setText(currentName)
            selectAll()
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.rename)
            .setView(editText)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) {
                    callbacks.onDirectoryRenameConfirmed(file.path, newName)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showRenameSingleDialog(filePath: String) {
        // Create File object that preserves network/cloud paths
        val file = if (filePath.startsWith("smb://") || 
                       filePath.startsWith("sftp://") || 
                       filePath.startsWith("ftp://") ||
                       filePath.startsWith("cloud://")) {
            object : File(filePath) {
                override fun getAbsolutePath(): String = filePath
                override fun getPath(): String = filePath
            }
        } else {
            File(filePath)
        }
        
        RenameDialog(
            context = activity,
            lifecycleOwner = callbacks.getLifecycleOwner(),
            files = listOf(file),
            sourceFolderName = callbacks.getResourceName() ?: "",
            fileOperationUseCase = callbacks.getFileOperationUseCase(),
            onComplete = { oldPath, newFile ->
                // Block FileObserver during programmatic update
                callbacks.setIgnoringFileChanges(true)
                
                // Instant update without full reload
                val mediaFile = callbacks.createMediaFileFromFile(newFile)
                callbacks.updateFile(oldPath, mediaFile)
                
                // Re-enable FileObserver after 200ms (enough for OS events)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    callbacks.setIgnoringFileChanges(false)
                }, 200)
            }
        ).show()
    }
    
    private fun showRenameMultipleDialog(filePaths: List<String>) {
        // Create File objects that preserve network/cloud paths
        val files = filePaths.map { path ->
            if (path.startsWith("smb://") || 
                path.startsWith("sftp://") || 
                path.startsWith("ftp://") ||
                path.startsWith("cloud://")) {
                object : File(path) {
                    override fun getAbsolutePath(): String = path
                    override fun getPath(): String = path
                }
            } else {
                File(path)
            }
        }
        val fileNames = files.map { it.name }.toMutableList()
        
        val dialogBinding = DialogRenameMultipleBinding.inflate(LayoutInflater.from(activity))
        
        val adapter = RenameFilesAdapter(fileNames)
        dialogBinding.rvFileNames.apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = adapter
        }
        
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.renaming_n_files_from_folder, files.size, callbacks.getResourceName() ?: ""))
            .setView(dialogBinding.root)
            .create()
        
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.btnApply.setOnClickListener {
            val newNames = adapter.getFileNames()
            var renamedCount = 0
            val errors = mutableListOf<String>()
            val renamedPairs = mutableMapOf<String, String>() // old path -> new path
            
            files.forEachIndexed { index, file ->
                val newName = newNames[index].trim()
                if (newName.isBlank() || newName == file.name) {
                    return@forEachIndexed
                }
                
                // For network paths, manually construct new path
                val filePath = file.path
                val newFile = if (filePath.startsWith("smb://") || filePath.startsWith("sftp://") || filePath.startsWith("ftp://")) {
                    val lastSlashIndex = filePath.lastIndexOf('/')
                    val parentPath = filePath.substring(0, lastSlashIndex)
                    val newPath = "$parentPath/$newName"
                    object : File(newPath) {
                        override fun getPath(): String = newPath
                        override fun getAbsolutePath(): String = newPath
                    }
                } else {
                    File(file.parent, newName)
                }
                
                if (newFile.exists()) {
                    errors.add(activity.getString(R.string.file_already_exists, newName))
                    return@forEachIndexed
                }
                
                try {
                    if (file.renameTo(newFile)) {
                        renamedCount++
                        renamedPairs[file.absolutePath] = newFile.absolutePath
                    } else {
                        errors.add("Failed to rename ${file.name}")
                    }
                } catch (e: Exception) {
                    errors.add("${file.name}: ${e.message}")
                }
            }
            
            // Save undo operation for renamed files
            if (renamedPairs.isNotEmpty()) {
                val undoOp = UndoOperation(
                    type = com.sza.fastmediasorter.domain.model.FileOperationType.RENAME,
                    sourceFiles = renamedPairs.keys.toList(),
                    destinationFolder = null,
                    copiedFiles = null,
                    oldNames = renamedPairs.toList()
                )
                callbacks.saveUndoOperation(undoOp)
            }
            
            callbacks.reloadFiles()
            
            if (renamedCount > 0) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.renamed_n_files, renamedCount),
                    Toast.LENGTH_SHORT
                ).show()
            }
            
            if (errors.isNotEmpty()) {
                Toast.makeText(
                    activity,
                    errors.joinToString("\n"),
                    Toast.LENGTH_LONG
                ).show()
            }
            
            dialog.dismiss()
        }
        
        dialog.show()
        
        // Show keyboard for first EditText after RecyclerView is laid out
        dialogBinding.rvFileNames.postDelayed({
            val firstViewHolder = dialogBinding.rvFileNames.findViewHolderForAdapterPosition(0)
            if (firstViewHolder is RenameFilesAdapter.ViewHolder) {
                firstViewHolder.binding.etFileName.requestFocus()
                val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(firstViewHolder.binding.etFileName, InputMethodManager.SHOW_IMPLICIT)
            }
        }, 200)
    }
    
    private inner class RenameFilesAdapter(
        private val fileNames: MutableList<String>
    ) : RecyclerView.Adapter<RenameFilesAdapter.ViewHolder>() {
        
        inner class ViewHolder(val binding: ItemRenameFileBinding) : RecyclerView.ViewHolder(binding.root) {
            private var textWatcher: TextWatcher? = null
            
            fun bind(fileName: String, position: Int) {
                // Remove old listener to prevent memory leaks
                textWatcher?.let { binding.etFileName.removeTextChangedListener(it) }
                
                // Only update text if it differs to prevent cursor issues
                if (binding.etFileName.text.toString() != fileName) {
                    binding.etFileName.setText(fileName)
                }
                
                // Create and add new listener
                textWatcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        fileNames[position] = s?.toString() ?: ""
                    }
                }
                binding.etFileName.addTextChangedListener(textWatcher)
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRenameFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(fileNames[position], position)
        }
        
        override fun getItemCount() = fileNames.size
        
        fun getFileNames() = fileNames.toList()
    }
}
