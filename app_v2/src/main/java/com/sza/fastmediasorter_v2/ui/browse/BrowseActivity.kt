package com.sza.fastmediasorter_v2.ui.browse

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter_v2.domain.model.DisplayMode
import com.sza.fastmediasorter_v2.domain.model.SortMode
import com.sza.fastmediasorter_v2.ui.player.PlayerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class BrowseActivity : BaseActivity<ActivityBrowseBinding>() {

    private val viewModel: BrowseViewModel by viewModels()
    private lateinit var mediaFileAdapter: MediaFileAdapter

    override fun getViewBinding(): ActivityBrowseBinding {
        return ActivityBrowseBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        mediaFileAdapter = MediaFileAdapter(
            onFileClick = { file ->
                viewModel.openFile(file)
            },
            onFileLongClick = { file ->
                viewModel.selectFile(file.path)
            },
            onSelectionChanged = { file, _ ->
                viewModel.selectFile(file.path)
            },
            onPlayClick = { file ->
                viewModel.openFile(file)
            }
        )

        binding.rvMediaFiles.adapter = mediaFileAdapter

        binding.btnSort.setOnClickListener {
            showSortDialog()
        }

        binding.btnFilter.setOnClickListener {
            Toast.makeText(this, "Filter - Coming Soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnToggleView.setOnClickListener {
            viewModel.toggleDisplayMode()
        }

        binding.btnCopy.setOnClickListener {
            Toast.makeText(this, "Copy - Coming Soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnMove.setOnClickListener {
            Toast.makeText(this, "Move - Coming Soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnRename.setOnClickListener {
            showRenameDialog()
        }

        binding.btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }

        binding.btnPlay.setOnClickListener {
            val firstFile = viewModel.state.value.mediaFiles.firstOrNull()
            if (firstFile != null) {
                viewModel.openFile(firstFile)
            }
        }
        
        binding.btnSlideshow.setOnClickListener {
            startSlideshow()
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    mediaFileAdapter.submitList(state.mediaFiles)
                    mediaFileAdapter.setSelectedPaths(state.selectedFiles)

                    state.resource?.let { resource ->
                        binding.toolbar.title = resource.name
                        binding.tvResourceInfo.text = buildResourceInfo(state)
                    }

                    binding.tvEmpty.isVisible = state.mediaFiles.isEmpty() && !viewModel.loading.value

                    val hasSelection = state.selectedFiles.isNotEmpty()
                    val isWritable = state.resource?.isWritable ?: false
                    
                    binding.btnCopy.isVisible = hasSelection
                    binding.btnMove.isVisible = hasSelection && isWritable
                    binding.btnRename.isVisible = hasSelection && isWritable
                    binding.btnDelete.isVisible = hasSelection && isWritable

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
                            Toast.makeText(this@BrowseActivity, event.message, Toast.LENGTH_LONG).show()
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

    private fun buildResourceInfo(state: BrowseState): String {
        val resource = state.resource ?: return ""
        val selected = if (state.selectedFiles.isEmpty()) {
            ""
        } else {
            " • ${state.selectedFiles.size} selected"
        }
        return "${resource.name} • ${resource.path}$selected"
    }

    private fun updateDisplayMode(mode: DisplayMode) {
        binding.rvMediaFiles.layoutManager = when (mode) {
            DisplayMode.LIST -> LinearLayoutManager(this)
            DisplayMode.GRID -> GridLayoutManager(this, 3)
        }
    }

    private fun showSortDialog() {
        val sortModes = SortMode.values()
        val items = sortModes.map { it.name }.toTypedArray()
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
        Toast.makeText(this, "Rename dialog - Coming Soon", Toast.LENGTH_SHORT).show()
        // TODO: Show RenameDialog with selected files
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

    companion object {
        const val EXTRA_RESOURCE_ID = "resourceId"

        fun createIntent(context: Context, resourceId: Long): Intent {
            return Intent(context, BrowseActivity::class.java).apply {
                putExtra(EXTRA_RESOURCE_ID, resourceId)
            }
        }
    }
}
