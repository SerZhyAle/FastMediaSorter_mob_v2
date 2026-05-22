package com.sza.fastmediasorter.ui.cloudfolders

import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.children
import androidx.core.view.isVisible
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityOnedriveFolderPickerBinding
import com.sza.fastmediasorter.databinding.ItemOnedriveFolderBinding
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.InputSurface
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class OneDriveFolderPickerActivity : BaseActivity<ActivityOnedriveFolderPickerBinding>() {

    companion object {
        const val EXTRA_ACCOUNT_EMAIL = "extra_account_email"
    }

    private val viewModel: OneDriveFolderPickerViewModel by viewModels()
    private lateinit var folderAdapter: CloudFolderAdapter
    // S0196 Phase 04: one-shot tag on the first non-empty folder list bind.
    private var firstListBoundLogged = false
    private var initialFolderFocusTransferred = false
    private val keyboardDelegate = CloudFolderPickerKeyboardDelegate(object : CloudFolderPickerKeyboardDelegate.Callback {
        override fun activateFocused(): Boolean {
            // Keyboard OpenCurrent must target the focused row/button, not always the first folder.
            return currentFocus?.performClick() == true
        }
        override fun navigateUp() { handleBackNavigation() }
        override fun refresh() { viewModel.loadFolders() }
        override fun cancel() { finish() }
        override fun showHelp() { InputHelpDialogFragment.show(supportFragmentManager, InputSurface.CLOUD_PICKER) }
    })

    override fun getViewBinding(): ActivityOnedriveFolderPickerBinding {
        return ActivityOnedriveFolderPickerBinding.inflate(layoutInflater)
    }

    // S0230 Phase 02 - TV initial focus on the folder list.
    override fun getInitialFocusView(): View? {
        val firstFolderItem = binding.rvFolders.findViewHolderForAdapterPosition(0)?.itemView
        val folderCount = if (::folderAdapter.isInitialized) folderAdapter.itemCount else -1
        return firstFolderItem
            ?: binding.toolbar.children.firstOrNull { it.isClickable }
            ?: binding.cbAddAsDestination
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

        folderAdapter = CloudFolderAdapter(
            inflate = { inflater, parent, attach ->
                CloudFolderItemBinding.OneDrive(ItemOnedriveFolderBinding.inflate(inflater, parent, attach))
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

        viewModel.loadFolders()
    }

    private fun applyEdgeToEdgeInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.rvFolders) { view, insets ->
            val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBar.bottom)
            (view as? android.view.ViewGroup)?.clipToPadding = false
            insets
        }
        // setupViews() runs inside post{} - initial insets dispatch was already missed.
        androidx.core.view.ViewCompat.requestApplyInsets(binding.rvFolders)
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state ->
            folderAdapter.submitList(state.folders)
            requestFirstFolderFocusIfNeeded(state.folders.isNotEmpty())

            binding.progressBar.isVisible = state.isLoading && !binding.swipeRefresh.isRefreshing
            binding.swipeRefresh.isRefreshing = state.isLoading && binding.swipeRefresh.isRefreshing

            binding.tvEmptyState.isVisible = state.folders.isEmpty() && !state.isLoading
            binding.rvFolders.isVisible = state.folders.isNotEmpty()

            val pathString = state.currentPath.joinToString(" / ") { it.name }
            binding.toolbar.title = pathString

            binding.cbAddAsDestination.isChecked = state.addAsDestination
            binding.cbScanSubdirectories.isChecked = state.scanSubdirectories

            // S0196 Phase 04: emit once after the first non-empty folder list is committed.
            if (!firstListBoundLogged && state.folders.isNotEmpty()) {
                firstListBoundLogged = true
                Timber.d("OneDriveFolderPickerActivity: primaryContentBound itemCount=${state.folders.size}")
            }
        }

        collectOnLifecycle(viewModel.events) { event ->
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

    private fun requestFirstFolderFocusIfNeeded(hasFolders: Boolean) {
        if (!hasFolders || initialFolderFocusTransferred || !shouldRequestInitialFocus()) return
        binding.rvFolders.post {
            val firstFolderItem = binding.rvFolders.findViewHolderForAdapterPosition(0)?.itemView
            if (firstFolderItem?.requestFocus() == true) {
                initialFolderFocusTransferred = true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyboardDelegate.handleKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun handleBackNavigation() {
        if (!viewModel.navigateBack()) {
            finish()
        }
    }
}
