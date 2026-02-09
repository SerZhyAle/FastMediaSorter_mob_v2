package com.sza.fastmediasorter.ui.cloudfolders

import android.content.Intent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityDropboxFolderPickerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class DropboxFolderPickerActivity : BaseActivity<ActivityDropboxFolderPickerBinding>() {

    private val viewModel: DropboxFolderPickerViewModel by viewModels()
    private lateinit var folderAdapter: DropboxFolderAdapter

    override fun getViewBinding(): ActivityDropboxFolderPickerBinding {
        return ActivityDropboxFolderPickerBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // Guard: Cloud storage is not supported by this flavor
        if (!BuildConfig.SUPPORT_CLOUD) {
            Timber.d("DropboxFolderPickerActivity: Cloud not supported (SUPPORT_CLOUD=false)")
            finish()
            return
        }
        
        binding.toolbar.setNavigationOnClickListener {
            handleBackNavigation()
        }
        
        // Handle system back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        folderAdapter = DropboxFolderAdapter(
            onFolderSelect = { folder ->
                viewModel.selectFolder(folder)
            },
            onFolderNavigate = { folder ->
                viewModel.navigateIntoFolder(folder)
            },
            onNavigateBack = {
                viewModel.navigateBack()
            },
            isRootLevel = {
                viewModel.state.value.currentPath.size == 1
            }
        )

        binding.rvFolders.adapter = folderAdapter
        
        binding.cbAddAsDestination.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleDestinationFlag()
        }

        binding.cbScanSubdirectories.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleScanSubdirectoriesFlag()
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
                    
                    // Update toolbar title with current path
                    val pathString = state.currentPath.joinToString(" / ") { it.name }
                    binding.toolbar.title = pathString
                    
                    // Update checkboxes
                    binding.cbAddAsDestination.isChecked = state.addAsDestination
                    binding.cbScanSubdirectories.isChecked = state.scanSubdirectories
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is DropboxFolderPickerEvent.ShowError -> {
                            Toast.makeText(this@DropboxFolderPickerActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is DropboxFolderPickerEvent.FolderSelected -> {
                            Toast.makeText(this@DropboxFolderPickerActivity, 
                                getString(R.string.resource_added), 
                                Toast.LENGTH_SHORT).show()
                            
                            // Navigate to main activity
                            val intent = Intent(this@DropboxFolderPickerActivity, 
                                com.sza.fastmediasorter.ui.main.MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun handleBackNavigation() {
        if (!viewModel.navigateBack()) {
            finish()
        }
    }
}
