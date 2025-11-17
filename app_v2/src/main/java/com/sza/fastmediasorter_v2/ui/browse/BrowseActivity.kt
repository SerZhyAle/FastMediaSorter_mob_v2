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
import com.sza.fastmediasorter_v2.data.observer.MediaStoreObserver
import com.sza.fastmediasorter_v2.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter_v2.databinding.DialogCopyToBinding
import com.sza.fastmediasorter_v2.databinding.DialogFilterBinding
import com.sza.fastmediasorter_v2.databinding.DialogRenameMultipleBinding
import com.sza.fastmediasorter_v2.databinding.DialogRenameSingleBinding
import com.sza.fastmediasorter_v2.databinding.ItemRenameFileBinding
import com.sza.fastmediasorter_v2.domain.model.DisplayMode
import com.sza.fastmediasorter_v2.domain.model.FileFilter
import com.sza.fastmediasorter_v2.domain.model.MediaFile
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
    private lateinit var pagingMediaFileAdapter: PagingMediaFileAdapter
    private var usePagination = false
    private var mediaStoreObserver: MediaStoreObserver? = null
    
    // Flag to prevent duplicate file loading on first onResume after onCreate
    private var isFirstResume = true
    
    // Cache current display mode to avoid redundant updateDisplayMode() calls
    private var currentDisplayMode: DisplayMode? = null
    
    // Shared RecycledViewPool for optimizing ViewHolder reuse
    private val sharedViewPool = RecyclerView.RecycledViewPool().apply {
        // Set max recycled views for each view type
        // ViewType 0 = List item, ViewType 1 = Grid item
        setMaxRecycledViews(0, 30) // List view holders
        setMaxRecycledViews(1, 40) // Grid view holders (more needed for grid)
    }
    
    @Inject
    lateinit var fileOperationUseCase: FileOperationUseCase
    
    @Inject
    lateinit var getDestinationsUseCase: GetDestinationsUseCase
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    private var showVideoThumbnails = false // Cached setting value

    override fun getViewBinding(): ActivityBrowseBinding {
        return ActivityBrowseBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Setup standard adapter (for small lists)
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
            },
            getShowVideoThumbnails = { showVideoThumbnails }
        )
        
        // Setup paging adapter (for large lists 1000+)
        pagingMediaFileAdapter = PagingMediaFileAdapter(
            onFileClick = { file ->
                viewModel.openFile(file)
            },
            onFileLongClick = { file ->
                viewModel.selectFileRange(file.path)
            },
            onSelectionChanged = { file, _ ->
                viewModel.selectFile(file.path)
            },
            onPlayClick = { file ->
                viewModel.openFile(file)
            },
            getShowVideoThumbnails = { showVideoThumbnails }
        )
        
        // Add footer adapter for "Loading more..." indicator
        val loadStateAdapter = PagingLoadStateAdapter { pagingMediaFileAdapter.retry() }
        val adapterWithFooter = pagingMediaFileAdapter.withLoadStateFooter(loadStateAdapter)

        binding.rvMediaFiles.apply {
            // Set initial adapter based on usePagination flag
            adapter = if (usePagination) adapterWithFooter else mediaFileAdapter
            
            // Calculate optimal cache size based on screen size
            val displayMetrics = resources.displayMetrics
            val screenHeightDp = displayMetrics.heightPixels / displayMetrics.density
            // For list view: ~80dp per item, for grid: ~150dp per item
            // Cache 1.5 screens worth of items for smooth scrolling
            val optimalCacheSize = ((screenHeightDp / 80) * 1.5).toInt().coerceIn(10, 30)
            setItemViewCacheSize(optimalCacheSize)
            
            // Use shared RecycledViewPool for efficient ViewHolder reuse
            setRecycledViewPool(sharedViewPool)
            
            // Set fixed size for better performance (item size doesn't change)
            setHasFixedSize(true)
            
            // Enable aggressive prefetching for faster thumbnail loading
            // Prefetch 2 screens ahead for smooth scrolling and parallel thumbnail downloads
            layoutManager?.isItemPrefetchEnabled = true
            (layoutManager as? LinearLayoutManager)?.initialPrefetchItemCount = 8
            (layoutManager as? GridLayoutManager)?.initialPrefetchItemCount = 12
            
            Timber.d("RecyclerView optimizations: cacheSize=$optimalCacheSize, screenHeightDp=$screenHeightDp")
        }

        binding.btnSort.setOnClickListener {
            showSortDialog()
        }

        binding.btnFilter.setOnClickListener {
            showFilterDialog()
        }

        binding.btnRefresh.setOnClickListener {
            Timber.d("Manual refresh requested")
            viewModel.reloadFiles()
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
        
        binding.btnRetry.setOnClickListener {
            viewModel.clearError()
            viewModel.reloadFiles()
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    Timber.d("State collected: usePagination=${state.usePagination}, mediaFiles.size=${state.mediaFiles.size}, resource=${state.resource?.name}")
                    
                    // Switch between pagination and standard mode
                    if (state.usePagination != usePagination) {
                        usePagination = state.usePagination
                        switchAdapter(usePagination)
                    }
                    
                    // Handle pagination mode vs standard mode
                    if (!state.usePagination) {
                        // Only submit list if content actually changed
                        // Compare with last emitted list from ViewModel (survives Activity recreation)
                        val previousMediaFiles = viewModel.lastEmittedMediaFiles
                        val previousSize = previousMediaFiles?.size ?: -1
                        
                        val shouldSubmit = if (previousMediaFiles == null) {
                            // First load - always submit
                            Timber.d("shouldSubmit=true: First load (previousMediaFiles=null)")
                            true
                        } else if (state.mediaFiles === previousMediaFiles) {
                            // Exact same object reference - skip
                            Timber.d("shouldSubmit=false: Same reference (===)")
                            false
                        } else if (state.mediaFiles.size != previousSize) {
                            // Size changed - definitely submit
                            Timber.d("shouldSubmit=true: Size changed (${state.mediaFiles.size} != $previousSize)")
                            true
                        } else {
                            // Same size, different reference - check if content actually changed
                            // Compare first and last items' paths as quick heuristic
                            val prevList = previousMediaFiles // Capture for null-safety
                            val contentChanged = if (state.mediaFiles.isEmpty()) {
                                Timber.d("List is empty, contentChanged=false")
                                false
                            } else {
                                val firstPathCurrent = state.mediaFiles.first().path
                                val firstPathPrev = prevList?.firstOrNull()?.path
                                val lastPathCurrent = state.mediaFiles.last().path
                                val lastPathPrev = prevList?.lastOrNull()?.path
                                val firstDiff = firstPathCurrent != firstPathPrev
                                val lastDiff = lastPathCurrent != lastPathPrev
                                Timber.d("Content check: first=$firstPathCurrent vs $firstPathPrev (diff=$firstDiff), last=$lastPathCurrent vs $lastPathPrev (diff=$lastDiff)")
                                firstDiff || lastDiff
                            }
                            Timber.d("shouldSubmit=contentChanged=$contentChanged (size=${state.mediaFiles.size}, prevSize=$previousSize)")
                            contentChanged
                        }
                        
                        if (shouldSubmit) {
                            viewModel.markListAsSubmitted(state.mediaFiles)
                            
                            // Standard mode - submit full list to MediaFileAdapter
                            val previousListSize = mediaFileAdapter.itemCount
                            
                            Timber.d("Submitting ${state.mediaFiles.size} files to adapter (previous size: $previousListSize)")
                            
                            mediaFileAdapter.submitList(state.mediaFiles) {
                            Timber.d("Adapter list submitted successfully, current itemCount=${mediaFileAdapter.itemCount}")
                            
                            // Update empty state AFTER adapter updates itemCount
                            val isLoading = viewModel.loading.value
                            val itemCount = mediaFileAdapter.itemCount
                            
                            if (isLoading && itemCount == 0) {
                                // Loading in progress - show "Loading..." message
                                binding.tvEmpty.isVisible = true
                                binding.tvEmpty.text = getString(R.string.loading)
                                Timber.d("Empty state: showing loading message (isLoading=true, itemCount=0)")
                            } else if (!isLoading && itemCount == 0) {
                                // Loading complete, no files - show "No files found"
                                binding.tvEmpty.isVisible = true
                                binding.tvEmpty.text = if (state.filter != null && !state.filter.isEmpty()) {
                                    getString(R.string.no_files_match_criteria)
                                } else {
                                    getString(R.string.no_media_files_found)
                                }
                                Timber.d("Empty state: no files found (isLoading=false, itemCount=0)")
                            } else {
                                // Files loaded - hide empty state
                                binding.tvEmpty.isVisible = false
                                Timber.d("Empty state: hidden (itemCount=$itemCount)")
                            }
                            Timber.d("UI visibility: rvMediaFiles.isVisible=${binding.rvMediaFiles.isVisible}, tvEmpty.isVisible=${binding.tvEmpty.isVisible}")
                            
                            // Scroll to last viewed file after list is submitted
                            if (previousListSize == 0 && state.mediaFiles.isNotEmpty()) {
                                state.resource?.lastViewedFile?.let { lastViewedPath ->
                                    val position = state.mediaFiles.indexOfFirst { it.path == lastViewedPath }
                                    if (position >= 0) {
                                        binding.rvMediaFiles.post {
                                            binding.rvMediaFiles.scrollToPosition(position)
                                            Timber.d("Scrolled to last viewed file at position $position")
                                        }
                                    }
                                }
                            }
                        }
                        } else {
                            Timber.d("Skipping submitList: list unchanged (size=${state.mediaFiles.size}, sameRef=${state.mediaFiles === previousMediaFiles})")
                        }
                        mediaFileAdapter.setSelectedPaths(state.selectedFiles)
                        state.resource?.let { resource ->
                            mediaFileAdapter.setCredentialsId(resource.credentialsId)
                        }
                    } else {
                        // Pagination mode - flow is handled separately below
                        pagingMediaFileAdapter.setSelectedPaths(state.selectedFiles)
                        state.resource?.let { resource ->
                            pagingMediaFileAdapter.setCredentialsId(resource.credentialsId)
                        }
                    }

                    state.resource?.let { resource ->
                        binding.tvResourceInfo.text = buildResourceInfo(state)
                    }

                    // Show filter warning at bottom
                    if (state.filter != null && !state.filter.isEmpty()) {
                        binding.tvFilterWarning.isVisible = true
                        binding.tvFilterWarning.text = buildFilterDescription(state.filter)
                    } else {
                        binding.tvFilterWarning.isVisible = false
                    }

                    val hasSelection = state.selectedFiles.isNotEmpty()
                    val isWritable = state.resource?.isWritable ?: false
                    
                    binding.btnCopy.isVisible = hasSelection
                    binding.btnMove.isVisible = hasSelection && isWritable
                    binding.btnRename.isVisible = hasSelection && isWritable
                    binding.btnDelete.isVisible = hasSelection && isWritable
                    binding.btnUndo.isVisible = state.lastOperation != null

                    // Only update display mode if it actually changed
                    if (state.displayMode != currentDisplayMode) {
                        currentDisplayMode = state.displayMode
                        updateDisplayMode(state.displayMode)
                    }
                }
            }
        }
        
        // Subscribe to pagingDataFlow for large datasets
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pagingDataFlow.collect { flow ->
                    flow?.collect { pagingData ->
                        Timber.d("Submitting pagingData to adapter")
                        pagingMediaFileAdapter.submitData(pagingData)
                    }
                }
            }
        }
        
        // Handle paging adapter LoadState
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                pagingMediaFileAdapter.loadStateFlow.collect { loadStates ->
                    // Show loading indicator during initial load or refresh
                    val isLoading = loadStates.refresh is androidx.paging.LoadState.Loading
                    binding.progressBar.isVisible = isLoading
                    
                    // Show error state if initial load failed
                    val isError = loadStates.refresh is androidx.paging.LoadState.Error
                    if (isError) {
                        val error = (loadStates.refresh as androidx.paging.LoadState.Error).error
                        Timber.e(error, "Paging load error")
                        showError("Failed to load files", error.stackTraceToString(), error)
                    }
                    
                    // Log append state for debugging
                    when (loadStates.append) {
                        is androidx.paging.LoadState.Loading -> {
                            Timber.d("Loading more files...")
                        }
                        is androidx.paging.LoadState.Error -> {
                            val error = (loadStates.append as androidx.paging.LoadState.Error).error
                            Timber.e(error, "Error loading more files")
                        }
                        is androidx.paging.LoadState.NotLoading -> {
                            if (loadStates.append.endOfPaginationReached) {
                                Timber.d("Reached end of pagination")
                            }
                        }
                    }
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

        // Observe settings changes
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.getSettings().collect { settings ->
                    showVideoThumbnails = settings.showVideoThumbnails
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.error.collect { errorMessage ->
                    // Show error state if error occurred and no files loaded
                    val hasError = errorMessage != null
                    val isEmpty = mediaFileAdapter.itemCount == 0
                    
                    binding.errorStateView.isVisible = hasError && isEmpty
                    binding.tvEmpty.isVisible = !hasError && isEmpty
                    
                    if (hasError && isEmpty) {
                        binding.tvErrorMessage.text = errorMessage
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is BrowseEvent.ShowError -> {
                            showError(event.message, event.details, event.exception)
                        }
                        is BrowseEvent.ShowMessage -> {
                            Toast.makeText(this@BrowseActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is BrowseEvent.ShowUndoToast -> {
                            // Show toast with undo action hint
                            val message = "Files ${event.operationType}. Tap UNDO to revert."
                            Toast.makeText(this@BrowseActivity, message, Toast.LENGTH_LONG).show()
                        }
                        is BrowseEvent.NavigateToPlayer -> {
                            val resourceId = viewModel.state.value.resource?.id ?: 0L
                            // Pass skipAvailabilityCheck to prevent redundant checks
                            val skipCheck = intent.getBooleanExtra(EXTRA_SKIP_AVAILABILITY_CHECK, false)
                            startActivity(PlayerActivity.createIntent(
                                this@BrowseActivity,
                                resourceId,
                                event.fileIndex,
                                skipCheck
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
    private fun showError(message: String, details: String?, exception: Throwable? = null) {
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            Timber.d("showError: showDetailedErrors=${settings.showDetailedErrors}, message=$message, hasDetails=${details != null}, hasException=${exception != null}")
            
            if (settings.showDetailedErrors) {
                // Use ErrorDialog with full details
                if (exception != null) {
                    // Show exception with stack trace
                    com.sza.fastmediasorter_v2.ui.dialog.ErrorDialog.show(
                        context = this@BrowseActivity,
                        title = getString(R.string.error),
                        throwable = exception
                    )
                } else if (details != null) {
                    // Show message with details
                    com.sza.fastmediasorter_v2.ui.dialog.ErrorDialog.show(
                        context = this@BrowseActivity,
                        title = getString(R.string.error),
                        message = message,
                        details = details
                    )
                } else {
                    // Show only message
                    com.sza.fastmediasorter_v2.ui.dialog.ErrorDialog.show(
                        context = this@BrowseActivity,
                        title = getString(R.string.error),
                        message = message
                    )
                }
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
        
        // Add sort mode display
        val sortMode = when (state.sortMode) {
            SortMode.NAME_ASC -> getString(R.string.sort_by_name_asc)
            SortMode.NAME_DESC -> getString(R.string.sort_by_name_desc)
            SortMode.DATE_ASC -> getString(R.string.sort_by_date_asc)
            SortMode.DATE_DESC -> getString(R.string.sort_by_date_desc)
            SortMode.SIZE_ASC -> getString(R.string.sort_by_size_asc)
            SortMode.SIZE_DESC -> getString(R.string.sort_by_size_desc)
            SortMode.TYPE_ASC -> getString(R.string.sort_by_type_asc)
            SortMode.TYPE_DESC -> getString(R.string.sort_by_type_desc)
            SortMode.MANUAL -> getString(R.string.sort_by_manual)
            SortMode.RANDOM -> getString(R.string.sort_by_random)
        }
        
        return "${resource.name}$fileCount • ${resource.path} • $sortMode$selected"
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

    private suspend fun updateDisplayMode(mode: DisplayMode) {
        Timber.d("updateDisplayMode: mode=$mode, usePagination=$usePagination")
        
        val settings = settingsRepository.getSettings().first()
        val iconSize = settings.defaultIconSize
        
        // Update adapter mode
        if (usePagination) {
            pagingMediaFileAdapter.setGridMode(
                enabled = mode == DisplayMode.GRID,
                iconSize = iconSize
            )
            Timber.d("updateDisplayMode: Updated pagingAdapter gridMode=${mode == DisplayMode.GRID}")
        } else {
            mediaFileAdapter.setGridMode(
                enabled = mode == DisplayMode.GRID,
                iconSize = iconSize
            )
            Timber.d("updateDisplayMode: Updated mediaFileAdapter gridMode=${mode == DisplayMode.GRID}")
        }
        
        // Update toggle button icon
        binding.btnToggleView.setImageResource(
            when (mode) {
                DisplayMode.LIST -> R.drawable.ic_view_grid // Show grid icon when in list mode
                DisplayMode.GRID -> R.drawable.ic_view_list // Show list icon when in grid mode
            }
        )
        
        // Update layout manager
        val newLayoutManager = when (mode) {
            DisplayMode.LIST -> LinearLayoutManager(this@BrowseActivity)
            DisplayMode.GRID -> {
                // Calculate span count dynamically based on screen width and icon size
                val displayMetrics = resources.displayMetrics
                val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
                val iconSizeDp = iconSize.toFloat()
                val cardPaddingDp = 8f // 4dp padding on each side (from card layout)
                val itemWidthDp = iconSizeDp + cardPaddingDp
                val spanCount = (screenWidthDp / itemWidthDp).toInt().coerceAtLeast(2)
                
                Timber.d("updateDisplayMode: Grid calculation - screenWidth=${screenWidthDp}dp, iconSize=${iconSizeDp}dp, spanCount=$spanCount")
                GridLayoutManager(this@BrowseActivity, spanCount)
            }
        }
        binding.rvMediaFiles.layoutManager = newLayoutManager
        Timber.d("updateDisplayMode: Layout manager updated to ${newLayoutManager::class.simpleName}")
    }
    
    /**
     * Switch between standard MediaFileAdapter and PagingMediaFileAdapter
     */
    private fun switchAdapter(usePagination: Boolean) {
        Timber.d("Switching to ${if (usePagination) "pagination" else "standard"} adapter")
        
        if (usePagination) {
            // Use adapter with footer for pagination
            val loadStateAdapter = PagingLoadStateAdapter { pagingMediaFileAdapter.retry() }
            binding.rvMediaFiles.adapter = pagingMediaFileAdapter.withLoadStateFooter(loadStateAdapter)
        } else {
            // Use standard adapter
            binding.rvMediaFiles.adapter = mediaFileAdapter
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
                val selectedMode = sortModes[which]
                
                // Warn if selecting non-NAME sorting for large folders (1000+ files)
                val fileCount = viewModel.state.value.totalFileCount ?: 0
                val isLargeFolder = fileCount >= 1000
                val isNonNameSort = selectedMode !in setOf(SortMode.NAME_ASC, SortMode.NAME_DESC)
                
                if (isLargeFolder && isNonNameSort) {
                    dialog.dismiss()
                    showLargeFolderSortWarning(selectedMode, fileCount)
                } else {
                    viewModel.setSortMode(selectedMode)
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showLargeFolderSortWarning(sortMode: SortMode, fileCount: Int) {
        AlertDialog.Builder(this)
            .setTitle("Performance Warning")
            .setMessage("This folder contains $fileCount files. Sorting by ${getSortModeName(sortMode)} requires loading all files at once, which may take a long time (30+ seconds).\n\nFor better performance, use Name sorting (instant pagination).\n\nContinue anyway?")
            .setPositiveButton("Continue") { _, _ ->
                viewModel.setSortMode(sortMode)
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
            SortMode.RANDOM -> "Random"
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
            // Pass skipAvailabilityCheck to prevent redundant checks
            val skipCheck = intent.getBooleanExtra(EXTRA_SKIP_AVAILABILITY_CHECK, false)
            val intent = PlayerActivity.createIntent(this, resourceId, startIndex, skipCheck).apply {
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
        
        // For network paths (SMB/SFTP), create File with URI-compatible scheme
        val selectedFiles = selectedPaths.map { path ->
            if (path.startsWith("smb://") || path.startsWith("sftp://")) {
                object : File(path) {
                    override fun getAbsolutePath(): String = path
                    override fun getPath(): String = path
                }
            } else {
                File(path)
            }
        }
        
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
        
        // For network paths (SMB/SFTP), create File with URI-compatible scheme
        val selectedFiles = selectedPaths.map { path ->
            if (path.startsWith("smb://") || path.startsWith("sftp://")) {
                object : File(path) {
                    override fun getAbsolutePath(): String = path
                    override fun getPath(): String = path
                }
            } else {
                File(path)
            }
        }
        
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
        
        // Skip reload on first onResume - files already loaded in ViewModel.init{}
        if (isFirstResume) {
            isFirstResume = false
            Timber.d("BrowseActivity.onResume: First resume, skipping reload (already loaded in init)")
        } else {
            // Don't automatically reload - user can use Refresh button if needed
            // FileObserver will catch file system changes automatically
            Timber.d("BrowseActivity.onResume: Returned from PlayerActivity, FileObserver will handle changes")
        }
        
        // Clear expired undo operations (older than 5 minutes)
        viewModel.clearExpiredUndoOperation()
        
        // Start MediaStore observer for local resources
        startMediaStoreObserver()
    }
    
    override fun onPause() {
        super.onPause()
        // Stop MediaStore observer to avoid unnecessary updates
        stopMediaStoreObserver()
    }
    
    private fun startMediaStoreObserver() {
        // Check if resource is local
        val resource = viewModel.state.value.resource
        if (resource?.type != com.sza.fastmediasorter_v2.domain.model.ResourceType.LOCAL) {
            return
        }
        
        try {
            mediaStoreObserver = MediaStoreObserver(
                context = this,
                onMediaStoreChanged = {
                    Timber.d("MediaStore changed, reloading files")
                    viewModel.reloadFiles()
                }
            )
            mediaStoreObserver?.startWatching()
            Timber.d("Started MediaStore observer")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start MediaStore observer")
        }
    }
    
    private fun stopMediaStoreObserver() {
        mediaStoreObserver?.stopWatching()
        mediaStoreObserver = null
        Timber.d("Stopped MediaStore observer")
    }

    companion object {
        const val EXTRA_RESOURCE_ID = "resourceId"
        const val EXTRA_SKIP_AVAILABILITY_CHECK = "skipAvailabilityCheck"

        fun createIntent(context: Context, resourceId: Long, skipAvailabilityCheck: Boolean = false): Intent {
            return Intent(context, BrowseActivity::class.java).apply {
                putExtra(EXTRA_RESOURCE_ID, resourceId)
                putExtra(EXTRA_SKIP_AVAILABILITY_CHECK, skipAvailabilityCheck)
            }
        }
    }
}
