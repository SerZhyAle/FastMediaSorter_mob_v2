package com.sza.fastmediasorter_v2.ui.browse

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter_v2.databinding.DialogFilterBinding
import com.sza.fastmediasorter_v2.databinding.DialogRenameMultipleBinding
import com.sza.fastmediasorter_v2.databinding.DialogRenameSingleBinding
import com.sza.fastmediasorter_v2.databinding.ItemRenameFileBinding
import com.sza.fastmediasorter_v2.domain.model.DisplayMode
import com.sza.fastmediasorter_v2.domain.model.FileFilter
import com.sza.fastmediasorter_v2.domain.model.SortMode
import com.sza.fastmediasorter_v2.domain.repository.SettingsRepository
import com.sza.fastmediasorter_v2.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter_v2.ui.dialog.CopyToDialog
import com.sza.fastmediasorter_v2.ui.dialog.MoveToDialog
import com.sza.fastmediasorter_v2.ui.player.PlayerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class BrowseActivity : BaseActivity<ActivityBrowseBinding>() {

    private val viewModel: BrowseViewModel by viewModels()
    private lateinit var mediaFileAdapter: MediaFileAdapter
    
    @Inject
    lateinit var fileOperationUseCase: FileOperationUseCase
    
    @Inject
    lateinit var getDestinationsUseCase: GetDestinationsUseCase
    
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun getViewBinding(): ActivityBrowseBinding {
        return ActivityBrowseBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        mediaFileAdapter = MediaFileAdapter(
            onFileClick = { file ->
                viewModel.openFile(file)
            },
            onFileLongClick = { file ->
                // According to specification: long press selects range
                viewModel.selectFileRange(file.path)
            },
            onSelectionChanged = { file, _ ->
                viewModel.selectFile(file.path)
            },
            onPlayClick = { file ->
                viewModel.openFile(file)
            }
        )

        binding.rvMediaFiles.apply {
            adapter = mediaFileAdapter
            // Increase view cache size for smoother scrolling
            setItemViewCacheSize(20)
            // Set drawing cache enabled for better scrolling performance
            setHasFixedSize(true)
        }

        binding.btnSort.setOnClickListener {
            showSortDialog()
        }

        binding.btnFilter.setOnClickListener {
            showFilterDialog()
        }

        binding.btnToggleView.setOnClickListener {
            viewModel.toggleDisplayMode()
        }
        
        binding.btnSelectAll.setOnClickListener {
            viewModel.selectAll()
        }
        
        binding.btnDeselectAll.setOnClickListener {
            viewModel.clearSelection()
        }

        binding.btnCopy.setOnClickListener {
            showCopyDialog()
        }

        binding.btnMove.setOnClickListener {
            showMoveDialog()
        }

        binding.btnRename.setOnClickListener {
            showRenameDialog()
        }

        binding.btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }
        
        binding.btnUndo.setOnClickListener {
            viewModel.undoLastOperation()
        }

        binding.btnPlay.setOnClickListener {
            val firstFile = viewModel.state.value.mediaFiles.firstOrNull()
            if (firstFile != null) {
                viewModel.openFile(firstFile)
            }
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    mediaFileAdapter.submitList(state.mediaFiles)
                    mediaFileAdapter.setSelectedPaths(state.selectedFiles)

                    state.resource?.let { resource ->
                        mediaFileAdapter.setCredentialsId(resource.credentialsId)
                        binding.tvResourceInfo.text = buildResourceInfo(state)
                    }

                    // Show filter warning at bottom
                    if (state.filter != null && !state.filter.isEmpty()) {
                        binding.tvFilterWarning.isVisible = true
                        binding.tvFilterWarning.text = buildFilterDescription(state.filter)
                    } else {
                        binding.tvFilterWarning.isVisible = false
                    }

                    // Show empty state with context-appropriate message
                    val isEmpty = state.mediaFiles.isEmpty() && !viewModel.loading.value
                    binding.tvEmpty.isVisible = isEmpty
                    if (isEmpty) {
                        binding.tvEmpty.text = if (state.filter != null && !state.filter.isEmpty()) {
                            getString(R.string.no_files_match_criteria)
                        } else {
                            getString(R.string.no_media_files_found)
                        }
                    }

                    val hasSelection = state.selectedFiles.isNotEmpty()
                    val isWritable = state.resource?.isWritable ?: false
                    
                    binding.btnCopy.isVisible = hasSelection
                    binding.btnMove.isVisible = hasSelection && isWritable
                    binding.btnRename.isVisible = hasSelection && isWritable
                    binding.btnDelete.isVisible = hasSelection && isWritable
                    binding.btnUndo.isVisible = state.lastOperation != null

                    updateDisplayMode(state.displayMode)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loading.collect { isLoading ->
                    binding.progressBar.isVisible = isLoading
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is BrowseEvent.ShowError -> {
                            showError(event.message, event.details)
                        }
                        is BrowseEvent.ShowMessage -> {
                            Toast.makeText(this@BrowseActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is BrowseEvent.NavigateToPlayer -> {
                            val resourceId = viewModel.state.value.resource?.id ?: 0L
                            startActivity(PlayerActivity.createIntent(
                                this@BrowseActivity,
                                resourceId,
                                event.fileIndex
                            ))
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Show error message respecting showDetailedErrors setting
     * If showDetailedErrors=true: shows ErrorDialog with copyable text and detailed info
     * If showDetailedErrors=false: shows Toast (short notification)
     */
    private fun showError(message: String, details: String?) {
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            if (settings.showDetailedErrors) {
                // Use ErrorDialog with full details
                com.sza.fastmediasorter_v2.ui.dialog.ErrorDialog.show(
                    context = this@BrowseActivity,
                    title = getString(R.string.error),
                    message = message,
                    details = details
                )
            } else {
                // Simple toast for users who don't want details
                Toast.makeText(this@BrowseActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    @Deprecated("Use showError() instead - respects showDetailedErrors setting")
    private fun showErrorDialog(message: String, details: String?) {
        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
        
        // Add "Show Details" button if details are available
        if (!details.isNullOrBlank()) {
            dialogBuilder.setNeutralButton("Show Details") { _, _ ->
                showErrorDetailsDialog(details)
            }
        }
        
        dialogBuilder.show()
    }
    
    private fun showErrorDetailsDialog(details: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Error Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy") { _, _ ->
                copyToClipboard(details)
            }
            .show()
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Error Details", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun buildResourceInfo(state: BrowseState): String {
        val resource = state.resource ?: return ""
        val selected = if (state.selectedFiles.isEmpty()) {
            ""
        } else {
            " • ${state.selectedFiles.size} selected"
        }
        
        // Add file count if available
        val fileCount = when {
            state.totalFileCount != null -> " (${state.totalFileCount} files)"
            else -> " (counting...)"
        }
        
        return "${resource.name}$fileCount • ${resource.path}$selected"
    }

    private fun buildFilterDescription(filter: FileFilter): String {
        val parts = mutableListOf<String>()
        
        filter.nameContains?.let {
            parts.add("name contains '$it'")
        }
        
        if (filter.minDate != null && filter.maxDate != null) {
            parts.add("created ${formatDate(filter.minDate)} - ${formatDate(filter.maxDate)}")
        } else if (filter.minDate != null) {
            parts.add("created after ${formatDate(filter.minDate)}")
        } else if (filter.maxDate != null) {
            parts.add("created before ${formatDate(filter.maxDate)}")
        }
        
        if (filter.minSizeMb != null && filter.maxSizeMb != null) {
            parts.add("size ${filter.minSizeMb} - ${filter.maxSizeMb} MB")
        } else if (filter.minSizeMb != null) {
            parts.add("size >= ${filter.minSizeMb} MB")
        } else if (filter.maxSizeMb != null) {
            parts.add("size <= ${filter.maxSizeMb} MB")
        }
        
        return "⚠ Filter active: " + parts.joinToString(", ")
    }

    private fun updateDisplayMode(mode: DisplayMode) {
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            val iconSize = settings.defaultIconSize
            
            // Update adapter mode
            mediaFileAdapter.setGridMode(
                enabled = mode == DisplayMode.GRID,
                iconSize = iconSize
            )
            
            // Update toggle button icon
            binding.btnToggleView.setImageResource(
                when (mode) {
                    DisplayMode.LIST -> R.drawable.ic_view_grid // Show grid icon when in list mode
                    DisplayMode.GRID -> R.drawable.ic_view_list // Show list icon when in grid mode
                }
            )
            
            // Update layout manager
            binding.rvMediaFiles.layoutManager = when (mode) {
                DisplayMode.LIST -> LinearLayoutManager(this@BrowseActivity)
                DisplayMode.GRID -> GridLayoutManager(this@BrowseActivity, 3)
            }
        }
    }

    private fun showFilterDialog() {
        val dialogBinding = DialogFilterBinding.inflate(LayoutInflater.from(this))
        val currentFilter = viewModel.state.value.filter
        
        // Pre-fill current filter values
        dialogBinding.etFilterName.setText(currentFilter?.nameContains ?: "")
        
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
            dialogBinding.etMinSize.setText(it.toString())
        }
        currentFilter?.maxSizeMb?.let {
            dialogBinding.etMaxSize.setText(it.toString())
        }
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.filter)
            .setView(dialogBinding.root)
            .create()
        
        dialogBinding.btnClearFilter.setOnClickListener {
            viewModel.setFilter(null)
            dialog.dismiss()
        }
        
        dialogBinding.btnCancelFilter.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.btnApplyFilter.setOnClickListener {
            val nameFilter = dialogBinding.etFilterName.text?.toString()?.trim()
            val minSizeText = dialogBinding.etMinSize.text?.toString()?.trim()
            val maxSizeText = dialogBinding.etMaxSize.text?.toString()?.trim()
            
            val filter = FileFilter(
                nameContains = nameFilter?.ifBlank { null },
                minDate = minDate,
                maxDate = maxDate,
                minSizeMb = minSizeText?.toFloatOrNull(),
                maxSizeMb = maxSizeText?.toFloatOrNull()
            )
            
            viewModel.setFilter(if (filter.isEmpty()) null else filter)
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showDatePicker(currentDate: Long?, onDateSelected: (Long) -> Unit) {
        val calendar = java.util.Calendar.getInstance()
        if (currentDate != null) {
            calendar.timeInMillis = currentDate
        }
        
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                onDateSelected(calendar.timeInMillis)
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    private fun formatDate(timestamp: Long): String {
        val format = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        return format.format(java.util.Date(timestamp))
    }

    private fun showSortDialog() {
        val sortModes = SortMode.values()
        val items = sortModes.map { getSortModeName(it) }.toTypedArray()
        val currentIndex = sortModes.indexOf(viewModel.state.value.sortMode)

        AlertDialog.Builder(this)
            .setTitle("Sort by")
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                viewModel.setSortMode(sortModes[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun getSortModeName(mode: SortMode): String {
        return when (mode) {
            SortMode.MANUAL -> "Manual Order"
            SortMode.NAME_ASC -> "Name (A-Z)"
            SortMode.NAME_DESC -> "Name (Z-A)"
            SortMode.DATE_ASC -> "Date (Old first)"
            SortMode.DATE_DESC -> "Date (New first)"
            SortMode.SIZE_ASC -> "Size (Small first)"
            SortMode.SIZE_DESC -> "Size (Large first)"
            SortMode.TYPE_ASC -> "Type (A-Z)"
            SortMode.TYPE_DESC -> "Type (Z-A)"
        }
    }

    private fun showDeleteConfirmation() {
        val count = viewModel.state.value.selectedFiles.size
        AlertDialog.Builder(this)
            .setTitle("Delete Files")
            .setMessage("Are you sure you want to delete $count file(s)?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSelectedFiles()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showRenameDialog() {
        val selectedPaths = viewModel.state.value.selectedFiles.toList()
        if (selectedPaths.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedFiles = viewModel.state.value.mediaFiles.filter { 
            it.path in selectedPaths 
        }
        
        if (selectedFiles.size == 1) {
            showRenameSingleDialog(selectedFiles.first().path)
        } else {
            showRenameMultipleDialog(selectedFiles.map { it.path })
        }
    }
    
    private fun showRenameSingleDialog(filePath: String) {
        val file = java.io.File(filePath)
        val currentName = file.name
        
        val dialogBinding = DialogRenameSingleBinding.inflate(LayoutInflater.from(this))
        dialogBinding.etFileName.setText(currentName)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.renaming_files)
            .setView(dialogBinding.root)
            .create()
        
        // Set yellow background per specification
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.btnApply.setOnClickListener {
            val newName = dialogBinding.etFileName.text?.toString()?.trim()
            if (newName.isNullOrBlank()) {
                Toast.makeText(this, "File name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (newName == currentName) {
                dialog.dismiss()
                return@setOnClickListener
            }
            
            val newFile = java.io.File(file.parent, newName)
            if (newFile.exists()) {
                Toast.makeText(
                    this, 
                    getString(R.string.file_already_exists, newName), 
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            
            try {
                if (file.renameTo(newFile)) {
                    // Save undo operation
                    val undoOp = com.sza.fastmediasorter_v2.domain.model.UndoOperation(
                        type = com.sza.fastmediasorter_v2.domain.model.FileOperationType.RENAME,
                        sourceFiles = listOf(file.absolutePath),
                        destinationFolder = null,
                        copiedFiles = null,
                        oldNames = listOf(file.absolutePath to newFile.absolutePath)
                    )
                    viewModel.saveUndoOperation(undoOp)
                    
                    viewModel.reloadFiles()
                    Toast.makeText(
                        this,
                        getString(R.string.renamed_n_files, 1),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.rename_failed, "Unknown error"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    getString(R.string.rename_failed, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        dialog.show()
    }
    
    private fun showRenameMultipleDialog(filePaths: List<String>) {
        val files = filePaths.map { java.io.File(it) }
        val fileNames = files.map { it.name }.toMutableList()
        
        val dialogBinding = DialogRenameMultipleBinding.inflate(LayoutInflater.from(this))
        
        val adapter = RenameFilesAdapter(fileNames)
        dialogBinding.rvFileNames.apply {
            layoutManager = LinearLayoutManager(this@BrowseActivity)
            this.adapter = adapter
        }
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.renaming_n_files_from_folder, files.size, viewModel.state.value.resource?.name ?: ""))
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
                
                val newFile = java.io.File(file.parent, newName)
                if (newFile.exists()) {
                    errors.add(getString(R.string.file_already_exists, newName))
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
                val undoOp = com.sza.fastmediasorter_v2.domain.model.UndoOperation(
                    type = com.sza.fastmediasorter_v2.domain.model.FileOperationType.RENAME,
                    sourceFiles = renamedPairs.keys.toList(),
                    destinationFolder = null,
                    copiedFiles = null,
                    oldNames = renamedPairs.toList()
                )
                viewModel.saveUndoOperation(undoOp)
            }
            
            viewModel.reloadFiles()
            
            if (renamedCount > 0) {
                Toast.makeText(
                    this,
                    getString(R.string.renamed_n_files, renamedCount),
                    Toast.LENGTH_SHORT
                ).show()
            }
            
            if (errors.isNotEmpty()) {
                Toast.makeText(
                    this,
                    errors.joinToString("\n"),
                    Toast.LENGTH_LONG
                ).show()
            }
            
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private inner class RenameFilesAdapter(
        private val fileNames: MutableList<String>
    ) : RecyclerView.Adapter<RenameFilesAdapter.ViewHolder>() {
        
        inner class ViewHolder(val binding: ItemRenameFileBinding) : RecyclerView.ViewHolder(binding.root)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRenameFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.binding.etFileName.setText(fileNames[position])
            holder.binding.etFileName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    fileNames[position] = s?.toString() ?: ""
                }
            })
        }
        
        override fun getItemCount() = fileNames.size
        
        fun getFileNames() = fileNames.toList()
    }
    
    private fun startSlideshow() {
        val state = viewModel.state.value
        val startIndex = if (state.selectedFiles.isNotEmpty()) {
            state.mediaFiles.indexOfFirst { it.path == state.selectedFiles.first() }
        } else {
            0
        }
        
        if (startIndex >= 0) {
            val resourceId = state.resource?.id ?: 0L
            val intent = PlayerActivity.createIntent(this, resourceId, startIndex).apply {
                putExtra("slideshow_mode", true)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "No files to play", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showCopyDialog() {
        val selectedPaths = viewModel.state.value.selectedFiles.toList()
        if (selectedPaths.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        val resource = viewModel.state.value.resource
        if (resource == null) {
            Toast.makeText(this, "Resource not loaded", Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedFiles = selectedPaths.map { File(it) }
        
        val dialog = CopyToDialog(
            context = this,
            sourceFiles = selectedFiles,
            sourceFolderName = resource.name,
            currentResourceId = resource.id,
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            overwriteFiles = false,
            onComplete = { undoOp ->
                undoOp?.let { viewModel.saveUndoOperation(it) }
                viewModel.reloadFiles()
                viewModel.clearSelection()
            }
        )
        dialog.show()
    }
    
    private fun showMoveDialog() {
        val selectedPaths = viewModel.state.value.selectedFiles.toList()
        if (selectedPaths.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        val resource = viewModel.state.value.resource
        if (resource == null) {
            Toast.makeText(this, "Resource not loaded", Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedFiles = selectedPaths.map { File(it) }
        
        val dialog = MoveToDialog(
            context = this,
            sourceFiles = selectedFiles,
            sourceFolderName = resource.name,
            currentResourceId = resource.id,
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            overwriteFiles = false,
            onComplete = { undoOp ->
                undoOp?.let { viewModel.saveUndoOperation(it) }
                viewModel.reloadFiles()
                viewModel.clearSelection()
            }
        )
        dialog.show()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh file list when returning from PlayerActivity
        // This ensures deleted/renamed files are properly reflected
        Timber.d("BrowseActivity.onResume: Refreshing file list")
        viewModel.reloadFiles()
    }

    companion object {
        const val EXTRA_RESOURCE_ID = "resourceId"

        fun createIntent(context: Context, resourceId: Long): Intent {
            return Intent(context, BrowseActivity::class.java).apply {
                putExtra(EXTRA_RESOURCE_ID, resourceId)
            }
        }
    }
}
