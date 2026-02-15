package com.sza.fastmediasorter.ui.main

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.addresource.AddResourceActivity
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.resourceeditor.ResourceEditorActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.welcome.WelcomeActivity
import com.sza.fastmediasorter.ui.welcome.WelcomeViewModel
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.ui.main.helpers.KeyboardNavigationHandler
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
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
    private lateinit var keyboardNavigationHandler: KeyboardNavigationHandler
    private lateinit var passwordManager: ResourcePasswordManager
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var smbClient: SmbClient
    
    @Inject
    lateinit var unifiedCache: UnifiedFileCache

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Log config changes to detect unexpected recreations
        Timber.d("MainActivity.onCreate: savedInstanceState=${savedInstanceState != null}, isChangingConfigurations=$isChangingConfigurations")
        
        // Fix old cloud paths format (cloud:/ → cloud://)
        MediaFilesCacheManager.fixCloudPaths()
        
        // Check if this is first launch (fast check)
        if (!welcomeViewModel.isWelcomeCompleted()) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        // Check for widget actions
        if (intent?.action == ACTION_START_SLIDESHOW) {
            // Slight delay to ensure UI and ViewModel are ready
            binding.root.post {
                viewModel.startPlayer()
            }
        }
        
        // Initialize keyboard navigation handler
        keyboardNavigationHandler = KeyboardNavigationHandler(
            context = this,
            recyclerView = binding.rvResources,
            viewModel = viewModel,
            onDeleteConfirmation = { resource -> showDeleteConfirmation(resource) },
            onAddResourceClick = { binding.btnAddResource.performClick() },
            onSettingsClick = { binding.btnSettings.performClick() },
            onFilterClick = { binding.btnFilter.performClick() },
            onExit = { 
                finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        )
        
        // Initialize password manager for PIN-protected resources
        passwordManager = ResourcePasswordManager(
            context = this,
            layoutInflater = layoutInflater
        )
        
        // UI setup and resource loading deferred to setupViews() via BaseActivity.onCreate()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Restore previous tab if returning from Favorites Browse
        // This ensures the tab that was active before opening Favorites is restored
        if (viewModel.state.value.previousTab != null) {
            viewModel.restorePreviousTab()
        }
        
        // Sync TabLayout with ViewModel state immediately on resume
        // Skip FAVORITES tab - it's action-only (opens Browse), not a filter
        val currentTab = viewModel.state.value.activeResourceTab
        if (currentTab != ResourceTab.FAVORITES) {
            val tabPosition = getTabIndexForResourceTab(currentTab)
            if (binding.tabResourceTypes.selectedTabPosition != tabPosition) {
                binding.tabResourceTypes.selectTab(binding.tabResourceTypes.getTabAt(tabPosition))
            }
        }
        
        // Only refresh when returning from another activity (not on first launch)
        if (isReturningFromAnotherActivity) {
            viewModel.refreshResources()
        }
    }
    
    override fun onPause() {
        super.onPause()
        isReturningFromAnotherActivity = true
    }
    
    override fun onDestroy() {
        Timber.d("MainActivity.onDestroy: isFinishing=$isFinishing, isChangingConfigurations=$isChangingConfigurations")
        super.onDestroy()
        
        // Clear UnifiedFileCache when app closes (network file cache)
        // (bitmap thumbnails remain in Glide cache)
        // Skip cleanup if just recreating (rotation, theme change, etc)
        if (isFinishing && !isChangingConfigurations) {
            try {
                val stats = unifiedCache.getCacheStats()
                unifiedCache.clearAll()
                Timber.d("MainActivity.onDestroy: Cleared UnifiedFileCache: ${stats.totalSizeMB} MB")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear UnifiedFileCache on app close")
            }
        }
    }
    
    private var isReturningFromAnotherActivity = false

    override fun setupViews() {
        resourceAdapter = ResourceAdapter(
            onItemClick = { resource ->
                // Simple click = select and open Browse
                viewModel.selectResource(resource)
                viewModel.openBrowse()
            },
            onItemLongClick = { resource ->
                // Long click = open Edit (check PIN first)
                if (!resource.accessPin.isNullOrBlank()) {
                    passwordManager.checkResourcePinForEdit(resource)
                } else {
                    startActivity(ResourceEditorActivity.createEditIntent(this, resource.id))
                }
            },
            onEditClick = { resource ->
                // Check PIN before editing
                if (!resource.accessPin.isNullOrBlank()) {
                    passwordManager.checkResourcePinForEdit(resource)
                } else {
                    startActivity(ResourceEditorActivity.createEditIntent(this, resource.id))
                }
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
        
        // Configure LayoutManager based on screen width (Tablet support)
        val screenWidthDp = resources.configuration.screenWidthDp
        if (screenWidthDp >= 600) {
            // Tablet / Large screen: Use Grid Layout with columns from resources
            val columnCount = resources.getInteger(R.integer.resource_grid_column_count)
            binding.rvResources.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, columnCount)
        } else {
            // Phone / Small screen: Use Linear Layout
            binding.rvResources.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        }
        
        binding.btnToggleView.setOnClickListenerDebounced {
            viewModel.toggleResourceViewMode()
        }
        
        // Enable item animations for add/remove/move operations
        binding.rvResources.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 300
            removeDuration = 300
            moveDuration = 300
            changeDuration = 300
        }
        
        binding.btnStartPlayer.setOnClickListenerDebounced {
            viewModel.startPlayer()
        }
        
        binding.btnAddResource.setOnClickListenerDebounced {
            viewModel.addResource()
        }
        
        binding.btnSettings.setOnClickListenerDebounced {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        binding.btnFilter.setOnClickListenerDebounced {
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
        
        binding.btnRefresh.setOnClickListenerDebounced {
            // Force SMB client reset before scanning resources
            smbClient.forceFullReset()
            // Clear failed video thumbnail cache to retry previously failed videos
            NetworkFileDataFetcher.clearFailedVideoCache()
            viewModel.scanAllResources()
        }
        
        binding.btnExit.setOnClickListenerDebounced {
            finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        
        binding.btnFavorites.setOnClickListenerDebounced {
            viewModel.openFavorites()
        }
        
        binding.emptyStateView.setOnClickListenerDebounced {
            viewModel.addResource()
        }
        
        binding.btnRetry.setOnClickListenerDebounced {
            viewModel.clearError()
            viewModel.scanAllResources()
        }
        
        // Setup resource type tabs
        setupResourceTypeTabs()
        
        // Set initial button labels based on current orientation
        updateToolbarButtonLabels(resources.configuration)
        
        // Load resources after UI is ready (deferred from onCreate via BaseActivity)
        viewModel.refreshResources()
        
        // Log app version in background (non-critical)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val versionName = packageManager.getPackageInfo(packageName, 0).versionName
                val versionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode
                Timber.d("App version: $versionName (code: $versionCode)")
            } catch (e: Exception) {
                Timber.e(e, "Failed to get app version")
            }
        }
    }

    override fun observeData() {
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    resourceAdapter.submitList(state.resources)
                    resourceAdapter.setSelectedResource(state.selectedResource?.id)
                    resourceAdapter.setViewMode(state.isResourceGridMode)
                    
                    // Update layout manager based on mode and screen size
                    updateLayoutManagerForScreenSize()
                    
                    // Update toggle button icon
                    if (state.isResourceGridMode) {
                        binding.btnToggleView.setIconResource(R.drawable.ic_view_list)
                    } else {
                        binding.btnToggleView.setIconResource(R.drawable.ic_view_grid)
                    }
                    
                    // Toggle button visibility logic:
                    // - Show when grid mode is active (to allow returning to list view)
                    // - OR when > 10 resources (to allow switching to grid view)
                    binding.btnToggleView.isVisible = state.isResourceGridMode || state.resources.size > 10
                    
                    // Enable Play button if any resources exist (auto-selects last used or first)
                    binding.btnStartPlayer.isEnabled = state.resources.isNotEmpty()
                    
                    // Use state.resources.size instead of adapter.itemCount 
                    // because submitList() updates itemCount asynchronously
                    val isEmpty = state.resources.isEmpty()
                    
                    // Update visibility based on state
                    val hasError = viewModel.error.value != null
                    binding.errorStateView.isVisible = hasError && isEmpty
                    binding.emptyStateView.isVisible = !hasError && isEmpty
                    binding.rvResources.isVisible = !isEmpty
                    
                    updateFilterWarning(state)
                    
                    // Sync TabLayout selection with ViewModel state
                    // Skip FAVORITES tab - it's action-only (opens Browse), not a filter
                    if (state.activeResourceTab != ResourceTab.FAVORITES) {
                        val tabPosition = getTabIndexForResourceTab(state.activeResourceTab)
                        if (binding.tabResourceTypes.selectedTabPosition != tabPosition) {
                            binding.tabResourceTypes.selectTab(binding.tabResourceTypes.getTabAt(tabPosition))
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
        
        // Handle navigation progress (connection test during resource open)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.navigationProgressLayout.isVisible = state.isNavigating
                    if (state.isNavigating && state.navigationMessage != null) {
                        binding.tvNavigationMessage.text = state.navigationMessage
                    }
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
                        is MainEvent.ShowInfo -> {
                            showInfo(event.message, event.details)
                        }
                        is MainEvent.ShowMessage -> {
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is MainEvent.ShowResourceMessage -> {
                            val message = getString(event.resId, *event.args)
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        }
                        is MainEvent.RequestPassword -> {
                            passwordManager.checkResourcePassword(
                                resource = event.resource,
                                forSlideshow = event.forSlideshow,
                                onPasswordValidated = { resourceId, forSlideshow ->
                                    viewModel.proceedAfterPasswordCheck(resourceId, forSlideshow)
                                }
                            )
                        }
                        is MainEvent.NavigateToBrowse -> {
                            startActivity(BrowseActivity.createIntent(
                                this@MainActivity, 
                                event.resourceId, 
                                event.skipAvailabilityCheck
                            ))
                            @Suppress("DEPRECATION")
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        }
                        is MainEvent.NavigateToFavorites -> {
                            startActivity(BrowseActivity.createIntent(
                                this@MainActivity,
                                -100L, // FAVORITES_RESOURCE_ID
                                true // skipAvailabilityCheck
                            ))
                            @Suppress("DEPRECATION")
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        }
                        is MainEvent.NavigateToPlayerSlideshow -> {
                            val intent = PlayerActivity.createIntent(
                                this@MainActivity,
                                event.resourceId,
                                initialIndex = 0,
                                skipAvailabilityCheck = true
                            ).apply {
                                putExtra("slideshow_mode", true)
                            }
                            startActivity(intent)
                            @Suppress("DEPRECATION")
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        }
                        is MainEvent.NavigateToEditResource -> {
                            // Check if resource has PIN protection before editing
                            val resource = viewModel.state.value.resources.find { it.id == event.resourceId }
                            if (resource != null && !resource.accessPin.isNullOrBlank()) {
                                passwordManager.checkResourcePinForEdit(resource)
                            } else {
                                startActivity(ResourceEditorActivity.createEditIntent(this@MainActivity, event.resourceId))
                            }
                        }
                        is MainEvent.NavigateToAddResource -> {
                            startActivity(AddResourceActivity.createIntent(this@MainActivity, preselectedTab = event.preselectedTab))
                        }
                        is MainEvent.NavigateToAddResourceCopy -> {
                            startActivity(ResourceEditorActivity.createCopyIntent(this@MainActivity, event.copyResourceId))
                        }
                        MainEvent.NavigateToSettings -> {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        }
                        is MainEvent.ScanProgress -> {
                            binding.scanProgressLayout.visibility = View.VISIBLE
                            binding.tvScanDetail.text = "${event.scannedCount} files scanned"
                            event.currentFile?.let { fileName ->
                                binding.tvScanProgress.text = getString(R.string.scanning_progress, fileName)
                            }
                        }
                        MainEvent.ScanComplete -> {
                            binding.scanProgressLayout.visibility = View.GONE
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            // Observe settings to show/hide Favorites button
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.getSettings().collect { settings ->
                    binding.btnFavorites.visibility = if (settings.enableFavorites) {
                        View.VISIBLE
                    } else {
                        View.GONE
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
            
            binding.tvFilterWarning.text = getString(R.string.filters_active, parts.joinToString(" | "))
            binding.tvFilterWarning.isVisible = true
        } else {
            binding.tvFilterWarning.isVisible = false
        }
    }
    
    /**
     * Handle configuration changes (screen rotation).
     * Recalculates grid layout based on new screen dimensions.
     */
    override fun onLayoutConfigurationChanged(newConfig: Configuration) {
        updateToolbarButtonLabels(newConfig)
        updateLayoutManagerForScreenSize()
        
        // Recreate tabs to apply new inline/stacked label configuration
        binding.tabResourceTypes.removeAllTabs()
        createTabs()
    }
    
    /**
     * Show or hide text labels on toolbar buttons depending on orientation.
     * In landscape: show icon + text. In portrait: show icon only.
     */
    private fun updateToolbarButtonLabels(config: Configuration) {
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        Timber.d("updateToolbarButtonLabels: isLandscape=$isLandscape")
        
        if (isLandscape) {
            binding.btnExit.text = getString(R.string.exit)
            binding.btnAddResource.text = getString(R.string.add)
            binding.btnFilter.text = getString(R.string.search)
            binding.btnRefresh.text = getString(R.string.refresh)
            binding.btnSettings.text = getString(R.string.settings)
            binding.btnToggleView.text = getString(R.string.toggle_view)
            binding.btnFavorites.text = getString(R.string.favorites)
            binding.btnStartPlayer.text = getString(R.string.slideshow)
        } else {
            binding.btnExit.text = null
            binding.btnAddResource.text = null
            binding.btnFilter.text = null
            binding.btnRefresh.text = null
            binding.btnSettings.text = null
            binding.btnToggleView.text = null
            binding.btnFavorites.text = null
            binding.btnStartPlayer.text = null
        }
    }
    
    /**
     * Updates RecyclerView layout manager based on current screen width.
     * Called on initial setup and after screen rotation.
     */
    private fun updateLayoutManagerForScreenSize() {
        val state = viewModel.state.value
        val screenWidthDp = resources.configuration.screenWidthDp
        val isWideScreen = screenWidthDp >= 600
        
        Timber.d("updateLayoutManagerForScreenSize: screenWidthDp=$screenWidthDp, isWideScreen=$isWideScreen, isGridMode=${state.isResourceGridMode}")
        
        if (state.isResourceGridMode) {
            // Compact Grid Mode - use resource-based column counts
            val spanCount = if (isWideScreen) {
                resources.getInteger(R.integer.grid_column_count_landscape)
            } else {
                resources.getInteger(R.integer.grid_column_count)
            }
            val currentLayoutManager = binding.rvResources.layoutManager
            if (currentLayoutManager !is GridLayoutManager || currentLayoutManager.spanCount != spanCount) {
                binding.rvResources.layoutManager = GridLayoutManager(this, spanCount)
            }
        } else {
            // Detailed List/Grid Mode
            if (isWideScreen) {
                // Wide screen (tablet or rotated phone): columns from resource
                val columnCount = resources.getInteger(R.integer.resource_grid_column_count)
                val currentLayoutManager = binding.rvResources.layoutManager
                if (currentLayoutManager !is GridLayoutManager || currentLayoutManager.spanCount != columnCount) {
                    binding.rvResources.layoutManager = GridLayoutManager(this, columnCount)
                }
            } else {
                // Phone portrait: List
                if (binding.rvResources.layoutManager !is LinearLayoutManager ||
                    binding.rvResources.layoutManager is GridLayoutManager) {
                    binding.rvResources.layoutManager = LinearLayoutManager(this)
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
            Timber.d("showError: showDetailedErrors=${settings.showDetailedErrors}, message=$message, details=$details")
            if (settings.showDetailedErrors) {
                // Use ErrorDialog with full details
                com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                    context = this@MainActivity,
                    title = getString(com.sza.fastmediasorter.R.string.error),
                    message = message,
                    details = details
                )
            } else {
                // Simple toast for users who don't want details
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Show informational message (not an error, just info about empty folders, etc.)
     * If showDetailedErrors=true: shows ErrorDialog with "Information" title
     * If showDetailedErrors=false: shows Toast
     */
    private fun showInfo(message: String, details: String?) {
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            Timber.d("showInfo: showDetailedErrors=${settings.showDetailedErrors}, message=$message, details=$details")
            if (settings.showDetailedErrors) {
                // Use ErrorDialog but with Information title
                com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                    context = this@MainActivity,
                    title = getString(com.sza.fastmediasorter.R.string.information),
                    message = message,
                    details = details
                )
            } else {
                // Simple toast for users who don't want details
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showDeleteConfirmation(resource: com.sza.fastmediasorter.domain.model.MediaResource) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_resource_title)
            .setMessage(getString(R.string.delete_resource_message, resource.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteResource(resource)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Delegate all keyboard navigation to helper
        return if (keyboardNavigationHandler.handleKeyDown(keyCode, event)) {
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }
    
    /**
     * Creates and adds tabs to the TabLayout.
     * This method is called during initial setup and after configuration changes (rotation).
     */
    private fun createTabs() {
        // Create and add base tabs (ALL, Local, SMB, S/FTP, Cloud)
        // Cloud tab is hidden in lite flavor (SUPPORT_CLOUD = false)
        val allTab = binding.tabResourceTypes.newTab().apply {
            setText(R.string.tab_all_resources)
            setIcon(R.drawable.ic_view_list)
        }
        binding.tabResourceTypes.addTab(allTab)
        
        val localTab = binding.tabResourceTypes.newTab().apply {
            setText(R.string.tab_local_resources)
            setIcon(R.drawable.ic_resource_local)
        }
        binding.tabResourceTypes.addTab(localTab)
        
        val smbTab = binding.tabResourceTypes.newTab().apply {
            setText(R.string.tab_smb_resources)
            setIcon(R.drawable.ic_resource_smb)
        }
        binding.tabResourceTypes.addTab(smbTab)
        
        val ftpTab = binding.tabResourceTypes.newTab().apply {
            setText(R.string.tab_ftp_sftp_resources)
            setIcon(R.drawable.ic_resource_ftp)
        }
        binding.tabResourceTypes.addTab(ftpTab)
        
        // Only add Cloud tab if SUPPORT_CLOUD is true (standard, photos, legacy flavors)
        if (com.sza.fastmediasorter.BuildConfig.SUPPORT_CLOUD) {
            val cloudTab = binding.tabResourceTypes.newTab().apply {
                setText(R.string.tab_cloud_resources)
                setIcon(R.drawable.ic_resource_cloud)
            }
            binding.tabResourceTypes.addTab(cloudTab)
        }
        
        // Set default selection based on ViewModel state
        val currentTab = viewModel.state.value.activeResourceTab
        val tabIndex = getTabIndexForResourceTab(currentTab)
        binding.tabResourceTypes.getTabAt(tabIndex)?.select()

        // Adjust TabLayout mode for compact screens to avoid truncated labels
        val screenWidthDp = resources.configuration.screenWidthDp
        binding.tabResourceTypes.tabMode = if (screenWidthDp < 480) {
            TabLayout.MODE_SCROLLABLE
        } else {
            TabLayout.MODE_FIXED
        }
    }
    
    private fun setupResourceTypeTabs() {
        // Create tabs
        createTabs()
        
        // Setup tab selection listener (called once during initial setup)
        binding.tabResourceTypes.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val resourceTab = getResourceTabForIndex(tab?.position ?: 0)
                viewModel.setActiveTab(resourceTab)
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // No action needed
            }
            
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Reopen Browse when Favorites tab tapped again
                val favoritesTabIndex = if (com.sza.fastmediasorter.BuildConfig.SUPPORT_CLOUD) 5 else 4
                if (tab?.position == favoritesTabIndex) {
                    viewModel.openFavorites()
                    binding.tabResourceTypes.post {
                        // Restore previous tab
                        val previousTab = viewModel.state.value.previousTab ?: ResourceTab.ALL
                        val selectedTabIndex = getTabIndexForResourceTab(previousTab)
                        val selectedTab = binding.tabResourceTypes.getTabAt(selectedTabIndex)
                        if (selectedTab != null && !selectedTab.isSelected) {
                            selectedTab.select()
                        }
                    }
                }
            }
        })
    }
    
    /**
     * Get tab index for ResourceTab, accounting for hidden Cloud tab in lite flavor.
     */
    private fun getTabIndexForResourceTab(tab: ResourceTab): Int {
        return when (tab) {
            ResourceTab.ALL -> 0
            ResourceTab.LOCAL -> 1
            ResourceTab.SMB -> 2
            ResourceTab.FTP_SFTP -> 3
            ResourceTab.CLOUD -> if (com.sza.fastmediasorter.BuildConfig.SUPPORT_CLOUD) 4 else 3
            ResourceTab.FAVORITES -> 0 // Should not happen, default to ALL
        }
    }
    
    /**
     * Get ResourceTab for tab index, accounting for hidden Cloud tab in lite flavor.
     */
    private fun getResourceTabForIndex(index: Int): ResourceTab {
        return if (com.sza.fastmediasorter.BuildConfig.SUPPORT_CLOUD) {
            when (index) {
                0 -> ResourceTab.ALL
                1 -> ResourceTab.LOCAL
                2 -> ResourceTab.SMB
                3 -> ResourceTab.FTP_SFTP
                4 -> ResourceTab.CLOUD
                else -> ResourceTab.ALL
            }
        } else {
            // Lite flavor: no Cloud tab
            when (index) {
                0 -> ResourceTab.ALL
                1 -> ResourceTab.LOCAL
                2 -> ResourceTab.SMB
                3 -> ResourceTab.FTP_SFTP
                else -> ResourceTab.ALL
            }
        }
    }
    companion object {
        const val ACTION_START_SLIDESHOW = "com.sza.fastmediasorter.ACTION_START_SLIDESHOW"
    }
}
