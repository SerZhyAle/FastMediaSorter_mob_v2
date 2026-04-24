package com.sza.fastmediasorter.ui.cloudfolders

import android.content.Intent
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityGoogleDriveFolderPickerBinding
import com.sza.fastmediasorter.databinding.ItemGoogleDriveFolderBinding
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.InputSurface
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class GoogleDriveFolderPickerActivity : BaseActivity<ActivityGoogleDriveFolderPickerBinding>() {

    companion object {
        const val EXTRA_ACCOUNT_EMAIL = "extra_account_email"
        const val EXTRA_FOLDER_ID = "folder_id"
        const val EXTRA_FOLDER_NAME = "folder_name"
    }

    private val viewModel: GoogleDriveFolderPickerViewModel by viewModels()
    private lateinit var folderAdapter: CloudFolderAdapter
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

    private val reAuthLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Timber.i("Re-authorization successful, reloading folders")
                viewModel.loadFolders()
            } else {
                Timber.w("Re-authorization cancelled or failed")
                Toast.makeText(this, getString(R.string.google_drive_authentication_failed), Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun getViewBinding(): ActivityGoogleDriveFolderPickerBinding {
        return ActivityGoogleDriveFolderPickerBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // Guard: Cloud storage is not supported by this flavor
        if (!BuildConfig.SUPPORT_CLOUD) {
            Timber.d("GoogleDriveFolderPickerActivity: Cloud not supported (SUPPORT_CLOUD=false)")
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
                CloudFolderItemBinding.GDrive(ItemGoogleDriveFolderBinding.inflate(inflater, parent, attach))
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
        // RecyclerView needs bottom padding so last item isn't behind nav bar
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
                is GoogleDriveFolderPickerEvent.ShowError -> {
                    Toast.makeText(this@GoogleDriveFolderPickerActivity, event.message, Toast.LENGTH_SHORT).show()
                }
                is GoogleDriveFolderPickerEvent.FolderSelected -> {
                    Toast.makeText(
                        this@GoogleDriveFolderPickerActivity,
                        getString(R.string.resource_added),
                        Toast.LENGTH_SHORT
                    ).show()

                    val intent = Intent(
                        this@GoogleDriveFolderPickerActivity,
                        com.sza.fastmediasorter.ui.main.MainActivity::class.java
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                is GoogleDriveFolderPickerEvent.RequiresReAuth -> {
                    Timber.i("Launching re-authorization flow")
                    reAuthLauncher.launch(event.intent)
                }
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
