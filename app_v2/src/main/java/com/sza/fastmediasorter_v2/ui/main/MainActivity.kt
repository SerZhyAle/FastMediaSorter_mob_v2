package com.sza.fastmediasorter_v2.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.databinding.ActivityMainBinding
import com.sza.fastmediasorter_v2.domain.repository.SettingsRepository
import com.sza.fastmediasorter_v2.domain.usecase.ScheduleNetworkSyncUseCase
import com.sza.fastmediasorter_v2.ui.addresource.AddResourceActivity
import com.sza.fastmediasorter_v2.ui.browse.BrowseActivity
import com.sza.fastmediasorter_v2.ui.editresource.EditResourceActivity
import com.sza.fastmediasorter_v2.ui.settings.SettingsActivity
import com.sza.fastmediasorter_v2.ui.welcome.WelcomeActivity
import com.sza.fastmediasorter_v2.ui.welcome.WelcomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val viewModel: MainViewModel by viewModels()
    private val welcomeViewModel: WelcomeViewModel by viewModels()
    private lateinit var resourceAdapter: ResourceAdapter
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var scheduleNetworkSyncUseCase: ScheduleNetworkSyncUseCase

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Log app version
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            val versionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode
            Timber.d("App version: $versionName (code: $versionCode)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to get app version")
        }
        
        // Check if this is first launch
        if (!welcomeViewModel.isWelcomeCompleted()) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        
        // Schedule background network file sync (every 4 hours)
        scheduleNetworkSyncUseCase(intervalHours = 4, requiresNetwork = true)
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh resources list when returning from EditResourceActivity
        viewModel.refreshResources()
    }

    override fun setupViews() {
        resourceAdapter = ResourceAdapter(
            onItemClick = { resource ->
                // Simple click = select and open Browse
                viewModel.selectResource(resource)
                viewModel.startPlayer()
            },
            onItemLongClick = { resource ->
                // Long click = open Edit
                val intent = Intent(this, EditResourceActivity::class.java).apply {
                    putExtra("resourceId", resource.id)
                }
                startActivity(intent)
            },
            onEditClick = { resource ->
                val intent = Intent(this, EditResourceActivity::class.java).apply {
                    putExtra("resourceId", resource.id)
                }
                startActivity(intent)
            },
            onCopyFromClick = { resource ->
                viewModel.selectResource(resource)
                viewModel.copySelectedResource()
            },
            onDeleteClick = { resource ->
                showDeleteConfirmation(resource)
            },
            onMoveUpClick = { resource ->
                viewModel.moveResourceUp(resource)
            },
            onMoveDownClick = { resource ->
                viewModel.moveResourceDown(resource)
            }
        )
        
        binding.rvResources.adapter = resourceAdapter
        
        binding.btnStartPlayer.setOnClickListener {
            viewModel.startPlayer()
        }
        
        binding.btnAddResource.setOnClickListener {
            viewModel.addResource()
        }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        binding.btnFilter.setOnClickListener {
            val currentState = viewModel.state.value
            FilterResourceDialog.newInstance(
                sortMode = currentState.sortMode,
                resourceTypes = currentState.filterByType,
                mediaTypes = currentState.filterByMediaType,
                nameFilter = currentState.filterByName,
                onApply = { sortMode, filterByType, filterByMediaType, filterByName ->
                    viewModel.setSortMode(sortMode)
                    viewModel.setFilterByType(filterByType)
                    viewModel.setFilterByMediaType(filterByMediaType)
                    viewModel.setFilterByName(filterByName)
                }
            ).show(supportFragmentManager, "FilterResourceDialog")
        }
        
        binding.btnRefresh.setOnClickListener {
            viewModel.refreshResources()
        }
        
        binding.btnExit.setOnClickListener {
            finish()
        }
        
        binding.emptyStateView.setOnClickListener {
            viewModel.addResource()
        }
        
        binding.btnRetry.setOnClickListener {
            viewModel.clearError()
            viewModel.refreshResources()
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    resourceAdapter.submitList(state.resources)
                    resourceAdapter.setSelectedResource(state.selectedResource?.id)
                    
                    binding.btnStartPlayer.isEnabled = state.selectedResource != null
                    
                    // Use state.resources.size instead of adapter.itemCount 
                    // because submitList() updates itemCount asynchronously
                    val isEmpty = state.resources.isEmpty()
                    
                    // Update visibility based on state
                    val hasError = viewModel.error.value != null
                    binding.errorStateView.isVisible = hasError && isEmpty
                    binding.emptyStateView.isVisible = !hasError && isEmpty
                    binding.rvResources.isVisible = !isEmpty
                    
                    updateFilterWarning(state)
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
                viewModel.error.collect { errorMessage ->
                    // Show error state if error occurred and no resources loaded
                    val hasError = errorMessage != null
                    val isEmpty = viewModel.state.value.resources.isEmpty()
                    
                    binding.errorStateView.isVisible = hasError && isEmpty
                    binding.emptyStateView.isVisible = !hasError && isEmpty
                    binding.rvResources.isVisible = !isEmpty
                    
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
                        is MainEvent.ShowError -> {
                            showError(event.message, event.details)
                        }
                        is MainEvent.ShowMessage -> {
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is MainEvent.NavigateToBrowse -> {
                            startActivity(BrowseActivity.createIntent(
                                this@MainActivity, 
                                event.resourceId, 
                                event.skipAvailabilityCheck
                            ))
                        }
                        is MainEvent.NavigateToEditResource -> {
                            val intent = Intent(this@MainActivity, EditResourceActivity::class.java).apply {
                                putExtra("resourceId", event.resourceId)
                            }
                            startActivity(intent)
                        }
                        is MainEvent.NavigateToAddResource -> {
                            startActivity(Intent(this@MainActivity, AddResourceActivity::class.java))
                        }
                        MainEvent.NavigateToSettings -> {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        }
                        is MainEvent.ScanProgress -> {
                            binding.scanProgressLayout.visibility = View.VISIBLE
                            binding.tvScanDetail.text = "${event.scannedCount} files scanned"
                            event.currentFile?.let { fileName ->
                                binding.tvScanProgress.text = "Scanning: $fileName"
                            }
                        }
                        MainEvent.ScanComplete -> {
                            binding.scanProgressLayout.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }
    
    private fun updateFilterWarning(state: MainState) {
        val hasFilters = state.filterByType != null || 
                         state.filterByMediaType != null || 
                         !state.filterByName.isNullOrBlank()
        
        if (hasFilters) {
            val parts = mutableListOf<String>()
            
            state.filterByType?.let { types ->
                parts.add("Type: ${types.joinToString(", ")}")
            }
            
            state.filterByMediaType?.let { mediaTypes ->
                parts.add("Media: ${mediaTypes.joinToString(", ")}")
            }
            
            state.filterByName?.takeIf { it.isNotBlank() }?.let { name ->
                parts.add("Name: '$name'")
            }
            
            binding.tvFilterWarning.text = "Filters: ${parts.joinToString(" | ")}"
            binding.tvFilterWarning.isVisible = true
        } else {
            binding.tvFilterWarning.isVisible = false
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
            Timber.d("showError: showDetailedErrors=${settings.showDetailedErrors}, message=$message, details=$details")
            if (settings.showDetailedErrors) {
                // Use ErrorDialog with full details
                com.sza.fastmediasorter_v2.ui.dialog.ErrorDialog.show(
                    context = this@MainActivity,
                    title = getString(com.sza.fastmediasorter_v2.R.string.error),
                    message = message,
                    details = details
                )
            } else {
                // Simple toast for users who don't want details
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showDeleteConfirmation(resource: com.sza.fastmediasorter_v2.domain.model.MediaResource) {
        AlertDialog.Builder(this)
            .setTitle("Delete Resource")
            .setMessage("Are you sure you want to delete ${resource.name}?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteResource(resource)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
