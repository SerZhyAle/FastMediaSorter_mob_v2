package com.sza.fastmediasorter_v2.ui.cloudfolders

import android.content.Intent
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.databinding.ActivityGoogleDriveFolderPickerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GoogleDriveFolderPickerActivity : BaseActivity<ActivityGoogleDriveFolderPickerBinding>() {

    private val viewModel: GoogleDriveFolderPickerViewModel by viewModels()
    private lateinit var folderAdapter: GoogleDriveFolderAdapter

    override fun getViewBinding(): ActivityGoogleDriveFolderPickerBinding {
        return ActivityGoogleDriveFolderPickerBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        folderAdapter = GoogleDriveFolderAdapter(
            onFolderClick = { folder ->
                viewModel.selectFolder(folder)
            }
        )

        binding.rvFolders.adapter = folderAdapter

        binding.btnAddFolder.setOnClickListener {
            viewModel.addSelectedFolder()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadFolders()
        }

        // Initial load
        viewModel.loadFolders()
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    folderAdapter.submitList(state.folders)
                    
                    binding.progressBar.isVisible = state.isLoading && !binding.swipeRefresh.isRefreshing
                    binding.swipeRefresh.isRefreshing = state.isLoading && binding.swipeRefresh.isRefreshing
                    
                    binding.tvEmptyState.isVisible = state.folders.isEmpty() && !state.isLoading
                    binding.rvFolders.isVisible = state.folders.isNotEmpty()
                    
                    binding.btnAddFolder.isEnabled = state.selectedFolder != null
                    
                    state.selectedFolder?.let { folder ->
                        binding.tvSelectedFolder.text = getString(R.string.selected_folder, folder.name)
                        binding.tvSelectedFolder.isVisible = true
                    } ?: run {
                        binding.tvSelectedFolder.isVisible = false
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is GoogleDriveFolderPickerEvent.ShowError -> {
                            Toast.makeText(this@GoogleDriveFolderPickerActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is GoogleDriveFolderPickerEvent.FolderAdded -> {
                            setResult(RESULT_OK, Intent().apply {
                                putExtra(EXTRA_FOLDER_ID, event.folderId)
                                putExtra(EXTRA_FOLDER_NAME, event.folderName)
                            })
                            finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_FOLDER_ID = "folder_id"
        const val EXTRA_FOLDER_NAME = "folder_name"
    }
}
