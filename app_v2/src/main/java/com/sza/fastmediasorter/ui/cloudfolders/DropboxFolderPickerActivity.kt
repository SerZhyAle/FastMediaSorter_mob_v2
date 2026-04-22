package com.sza.fastmediasorter.ui.cloudfolders

import android.content.Intent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.isVisible
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityDropboxFolderPickerBinding
import com.sza.fastmediasorter.databinding.ItemDropboxFolderBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class DropboxFolderPickerActivity : BaseActivity<ActivityDropboxFolderPickerBinding>() {

    companion object {
        const val EXTRA_ACCOUNT_EMAIL = "extra_account_email"
    }

    private val viewModel: DropboxFolderPickerViewModel by viewModels()
    private lateinit var folderAdapter: CloudFolderAdapter

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

        // Apply edge-to-edge insets for CoordinatorLayout + AppBarLayout
        applyEdgeToEdgeInsets()

        binding.toolbar.setNavigationOnClickListener {
            handleBackNavigation()
        }
        
        // Handle system back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        folderAdapter = CloudFolderAdapter(
            inflate = { inflater, parent, attach ->
                CloudFolderItemBinding.Dropbox(ItemDropboxFolderBinding.inflate(inflater, parent, attach))
            },
            onFolderSelect = { folder -> viewModel.selectFolder(folder) },
            onFolderNavigate = { folder -> viewModel.navigateIntoFolder(folder) },
            onNavigateBack = { viewModel.navigateBack() },
            isRootLevel = { viewModel.state.value.currentPath.size == 1 }
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

    private fun applyEdgeToEdgeInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.rvFolders) { view, insets ->
            val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBar.bottom)
            (view as? android.view.ViewGroup)?.clipToPadding = false
            insets
        }
        // setupViews() runs inside post{} — initial insets dispatch was already missed.
        androidx.core.view.ViewCompat.requestApplyInsets(binding.rvFolders)
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state ->
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

        collectOnLifecycle(viewModel.events) { event ->
            when (event) {
                is DropboxFolderPickerEvent.ShowError -> {
                    Toast.makeText(this@DropboxFolderPickerActivity, event.message, Toast.LENGTH_SHORT).show()
                }
                is DropboxFolderPickerEvent.FolderSelected -> {
                    Toast.makeText(this@DropboxFolderPickerActivity,
                        getString(R.string.resource_added),
                        Toast.LENGTH_SHORT).show()

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

    private fun handleBackNavigation() {
        if (!viewModel.navigateBack()) {
            finish()
        }
    }
}
