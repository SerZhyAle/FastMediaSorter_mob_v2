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
import com.sza.fastmediasorter.databinding.ActivityOnedriveFolderPickerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class OneDriveFolderPickerActivity : BaseActivity<ActivityOnedriveFolderPickerBinding>() {

    companion object {
        const val EXTRA_ACCOUNT_EMAIL = "extra_account_email"
    }

    private val viewModel: OneDriveFolderPickerViewModel by viewModels()
    private lateinit var folderAdapter: OneDriveFolderAdapter

    override fun getViewBinding(): ActivityOnedriveFolderPickerBinding {
        return ActivityOnedriveFolderPickerBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // Guard: Cloud storage is not supported by this flavor
        if (!BuildConfig.SUPPORT_CLOUD) {
            Timber.d("OneDriveFolderPickerActivity: Cloud not supported (SUPPORT_CLOUD=false)")
            finish()
            return
        }

        // Apply edge-to-edge insets for CoordinatorLayout + AppBarLayout
        applyEdgeToEdgeInsets()

        binding.toolbar.setNavigationOnClickListener {
            handleBackNavigation()
        }
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        folderAdapter = OneDriveFolderAdapter(
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

        viewModel.loadFolders()
    }

    private fun applyEdgeToEdgeInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.rvFolders) { view, insets ->
            val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBar.bottom)
            (view as? android.view.ViewGroup)?.clipToPadding = false
            insets
        }
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
                    
                    val pathString = state.currentPath.joinToString(" / ") { it.name }
                    binding.toolbar.title = pathString
                    
                    binding.cbAddAsDestination.isChecked = state.addAsDestination
                    binding.cbScanSubdirectories.isChecked = state.scanSubdirectories
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is OneDriveFolderPickerEvent.ShowError -> {
                            Toast.makeText(this@OneDriveFolderPickerActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is OneDriveFolderPickerEvent.FolderSelected -> {
                            Toast.makeText(this@OneDriveFolderPickerActivity, 
                                getString(R.string.resource_added), 
                                Toast.LENGTH_SHORT).show()
                            
                            val intent = Intent(this@OneDriveFolderPickerActivity, 
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
