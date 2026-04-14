package com.sza.fastmediasorter.ui.main

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.ui.main.helpers.KeyboardNavigationHandler
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.repository.ResumeStateRepositoryImpl
import com.sza.fastmediasorter.domain.usecase.ClearResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.GetResumeStateUseCase
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.utils.PermissionChecker
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@android.annotation.SuppressLint("SetTextI18n")
class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val viewModel: MainViewModel by viewModels()
    private val welcomeViewModel: WelcomeViewModel by viewModels()
    private lateinit var resourceAdapter: ResourceAdapter
    private lateinit var keyboardNavigationHandler: KeyboardNavigationHandler
    private lateinit var passwordManager: ResourcePasswordManager

    private var permissionCheckDoneThisSession = false
    private var gridSpacingDecoration: RecyclerView.ItemDecoration? = null
    private var compactElementsEnabled = false

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Result reflected next onResume via hasFullLocalPermissions() */ }

    private val settingsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Result reflected next onResume via hasFullLocalPermissions() */ }
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var smbClient: SmbClient
    
    @Inject
    lateinit var unifiedCache: UnifiedFileCache

    @Inject
    lateinit var getResumeStateUseCase: GetResumeStateUseCase

    @Inject
    lateinit var clearResumeStateUseCase: ClearResumeStateUseCase

    @Inject
    lateinit var resourceRepository: ResourceRepository

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

        // If restart was triggered from SettingsActivity, return user there
        if (LocaleHelper.consumeReturnToSettings(this)) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        // Resume playback logic — only for standard launcher start with killed process
        if (shouldAttemptResume()) {
            attemptResumePlayback()
        }

        // Check for widget actions
        if (intent?.action == ACTION_START_SLIDESHOW) {
            // Slight delay to ensure UI and ViewModel are ready
            binding.root.post {
                viewModel.startPlayer()
            }
        }
        if (intent?.action == ACTION_RANDOM_MUSIC) {
            binding.root.post {
                viewModel.startRandomMusicPlayback()
            }
        }
        if (intent?.action == ACTION_CAMERA_PHOTOS) {
            binding.root.post {
                viewModel.openCameraPhotos()
            }
        }
        if (intent?.action == ACTION_OPEN_FAVORITES) {
            binding.root.post {
                viewModel.openFavorites()
            }
        }
        if (intent?.action == ACTION_BROWSE_RESOURCE) {
            val resourceId = intent.getLongExtra(EXTRA_SHORTCUT_RESOURCE_ID, -1L)
            if (resourceId != -1L) {
                binding.root.post {
                    viewModel.openResourceDirect(resourceId)
                }
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
                stopAudioPlaybackService()
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

        checkLocalPermissionsOnStartup()
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
        // Apply edge-to-edge insets: RecyclerView bottom padding for nav bar
        applyEdgeToEdgeInsets()

        resourceAdapter = ResourceAdapter(
            onItemClick = { resource ->
                // Simple click = select and open Browse
                viewModel.selectResource(resource)
                viewModel.openBrowse(resource)
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
                viewModel.copySelectedResource(resource)
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
            stopAudioPlaybackService()
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
        
        // Log app version in background and show update Toast if needed
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val versionName = packageInfo.versionName
                val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
                Timber.d("App version: $versionName (code: $versionCode)")
                
                val prefs = getSharedPreferences("app_update_prefs", android.content.Context.MODE_PRIVATE)
                val lastSeenVersionName = prefs.getString("last_seen_version_name", null)
                
                if (lastSeenVersionName != null && lastSeenVersionName != versionName) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.app_updated_to, versionName), Toast.LENGTH_LONG).show()
                    }
                }
                
                if (lastSeenVersionName != versionName) {
                    prefs.edit().putString("last_seen_version_name", versionName).apply()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get app version")
            }
        }
    }

    private fun applyEdgeToEdgeInsets() {
        // RecyclerView needs bottom padding so last item isn't behind nav bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.rvResources) { view, insets ->
            val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBar.bottom)
            (view as? android.view.ViewGroup)?.clipToPadding = false
            insets
        }
        // setupViews() runs inside post{} — initial insets dispatch was already missed.
        androidx.core.view.ViewCompat.requestApplyInsets(binding.rvResources)
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
                    // - OR always in landscape (grid vs compact-icon modes look meaningfully different)
                    val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    binding.btnToggleView.isVisible = state.isResourceGridMode || state.resources.size > 10 || isLandscape
                    
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
                        is MainEvent.NavigateToPlayerRandomMusic -> {
                            val intent = PlayerActivity.createIntent(
                                this@MainActivity,
                                event.resourceId,
                                initialIndex = 0,
                                skipAvailabilityCheck = true,
                                isPlaying = true,
                                shuffleOnStart = true
                            )
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
                            startActivity(
                                AddResourceActivity.createIntent(
                                    this@MainActivity,
                                    preselectedTab = event.preselectedTab
                                )
                            )
                        }
                        is MainEvent.NavigateToAddResourceCopy -> {
                            val resource = viewModel.state.value.resources.find { it.id == event.copyResourceId }
                            if (resource != null && !resource.accessPin.isNullOrBlank()) {
                                passwordManager.checkResourcePin(resource) {
                                    startActivity(
                                        ResourceEditorActivity.createCopyIntent(
                                            this@MainActivity,
                                            resourceId = event.copyResourceId
                                        )
                                    )
                                }
                            } else {
                                startActivity(
                                    ResourceEditorActivity.createCopyIntent(
                                        this@MainActivity,
                                        resourceId = event.copyResourceId
                                    )
                                )
                            }
                        }
                        MainEvent.NavigateToSettings -> {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        }
                        is MainEvent.ScanProgress -> {
                            binding.scanProgressLayout.visibility = View.VISIBLE
                            binding.tvScanDetail.text = getString(R.string.files_scanned_count, event.scannedCount)
                            event.currentFile?.let { fileName ->
                                binding.tvScanProgress.text = getString(R.string.scanning_progress, fileName)
                            }
                        }
                        MainEvent.ScanComplete -> {
                            binding.scanProgressLayout.visibility = View.GONE
                        }
                        MainEvent.ConfirmRescanWithVirtualResources -> {
                            if (isFinishing || isDestroyed) return@collect
                            android.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle(R.string.rescan_all_virtual_warning_title)
                                .setMessage(R.string.rescan_all_virtual_warning_message)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    viewModel.forceRescanAllResources()
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
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
                    resourceAdapter.setUseCompactElements(settings.useCompactElements)
                    compactElementsEnabled = settings.useCompactElements
                    applyCompactToolbar(settings.useCompactElements)
                    refreshGridSpacing()
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
        refreshGridSpacing()
    }

    /**
     * Resize the control buttons bar to match compact mode.
     * In compact mode: zero vertical padding + reduced button height.
     * In normal mode: restore dimen-based values.
     */
    private fun applyCompactToolbar(compact: Boolean) {
        val barPad = if (compact) 0 else resources.getDimensionPixelSize(R.dimen.control_bar_padding)
        val btnH = resources.getDimensionPixelSize(
            if (compact) R.dimen.control_button_size_compact else R.dimen.control_button_size
        )
        binding.layoutControlButtons.setPadding(0, barPad, 0, barPad)
        for (i in 0 until binding.layoutControlButtons.childCount) {
            val child = binding.layoutControlButtons.getChildAt(i)
            val lp = child.layoutParams
            if (lp.height > 0) {
                lp.height = btnH
                child.layoutParams = lp
            }
        }
        binding.layoutControlButtons.requestLayout()
    }

    /**
     * Apply or remove inter-item spacing decoration for the resource grid.
     * Normal: 4dp per side; compact: 2dp per side. No decoration in list mode.
     */
    private fun refreshGridSpacing() {
        gridSpacingDecoration?.let { binding.rvResources.removeItemDecoration(it) }
        gridSpacingDecoration = null
        if (binding.rvResources.layoutManager is GridLayoutManager) {
            val spacingPx = ((if (compactElementsEnabled) 2 else 4) * resources.displayMetrics.density).toInt()
            val dec = object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect,
                    view: android.view.View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    outRect.set(spacingPx, spacingPx, spacingPx, spacingPx)
                }
            }
            gridSpacingDecoration = dec
            binding.rvResources.addItemDecoration(dec)
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
        if (isFinishing || isDestroyed) return
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

        // Adjust TabLayout mode and gravity for compact screens to avoid truncated labels
        val screenWidthDp = resources.configuration.screenWidthDp
        if (screenWidthDp < 480) {
            binding.tabResourceTypes.tabMode = TabLayout.MODE_SCROLLABLE
            binding.tabResourceTypes.tabGravity = TabLayout.GRAVITY_START
        } else {
            binding.tabResourceTypes.tabMode = TabLayout.MODE_FIXED
            binding.tabResourceTypes.tabGravity = TabLayout.GRAVITY_FILL
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

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Forward mouse wheel scroll events to the resource list.
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)
        ) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll != 0f) {
                val rv = binding.rvResources
                val scrollFactor = rv.context.resources.displayMetrics.density * 64f
                rv.scrollBy(0, (-vScroll * scrollFactor).toInt())
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun hasFullLocalPermissions(): Boolean {
        return PermissionHelper.checkStoragePermissions(this)
    }

    private fun wasStoragePermissionRequested(): Boolean =
        getSharedPreferences(PREFS_NAME_APP, MODE_PRIVATE)
            .getBoolean(KEY_STORAGE_PERMISSION_REQUESTED, false)

    private fun markStoragePermissionRequested() {
        getSharedPreferences(PREFS_NAME_APP, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STORAGE_PERMISSION_REQUESTED, true)
            .apply()
    }

    private fun checkLocalPermissionsOnStartup() {
        if (permissionCheckDoneThisSession) return
        permissionCheckDoneThisSession = true

        if (hasFullLocalPermissions()) return

        if (!wasStoragePermissionRequested()) {
            markStoragePermissionRequested()
        }
        showStoragePermissionRequestDialog()
    }

    private fun showStoragePermissionRequestDialog() {
        if (isFinishing || isDestroyed) return
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getString(R.string.permission_storage_rationale_r)
        } else {
            getString(R.string.permission_storage_rationale)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.permissions_required_title)
            .setMessage(message)
            .setPositiveButton(R.string.grant_permissions) { _, _ -> launchStoragePermissionFlow() }
            .setNegativeButton(R.string.continue_anyway, null)
            .setCancelable(true)
            .show()
    }

    private fun launchStoragePermissionFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
            } catch (_: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
            settingsPermissionLauncher.launch(intent)
        } else {
            val perms = PermissionHelper.getStoragePermissionsArray()
            storagePermissionLauncher.launch(perms)
        }
    }

    // === Resume Playback Logic ===

    private fun shouldAttemptResume(): Boolean {
        // Only for standard launcher icon start (ACTION_MAIN)
        if (intent?.action != Intent.ACTION_MAIN) return false
        // Skip if slideshow widget action
        if (intent?.action == ACTION_START_SLIDESHOW) return false
        // Skip if AudioPlaybackService is still running (process wasn't killed)
        if (AudioPlaybackService.isRunning) {
            Timber.d("MainActivity: Skipping resume — AudioPlaybackService is running")
            return false
        }
        // Skip if storage permissions are missing
        if (!PermissionHelper.hasStoragePermission(this)) {
            Timber.d("MainActivity: Skipping resume — no storage permissions")
            return false
        }
        return true
    }

    private fun attemptResumePlayback() {
        // Show loading overlay immediately
        binding.navigationProgressLayout.isVisible = true
        binding.tvNavigationMessage.text = getString(R.string.resume_checking)

        lifecycleScope.launch {
            try {
                val state = getResumeStateUseCase()
                if (state == null) {
                    Timber.d("MainActivity: No resume state found")
                    dismissResumeLoading()
                    return@launch
                }

                // TTL check
                val elapsed = System.currentTimeMillis() - state.savedAt
                if (elapsed > ResumeStateRepositoryImpl.RESUME_TTL_MS) {
                    Timber.d("MainActivity: Resume state expired (${elapsed}ms > TTL)")
                    clearResumeStateUseCase()
                    dismissResumeLoading()
                    Toast.makeText(this@MainActivity, R.string.resume_unavailable, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Load resource to determine type
                val resource = resourceRepository.getResourceById(state.resourceId)
                if (resource == null) {
                    Timber.w("MainActivity: Resume resource not found (id=${state.resourceId})")
                    clearResumeStateUseCase()
                    dismissResumeLoading()
                    return@launch
                }

                // Availability check with 5-second timeout
                val isAvailable = try {
                    kotlinx.coroutines.withTimeout(5000L) {
                        checkResourceAvailability(resource, state.filePath)
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Timber.w("MainActivity: Resume availability check timed out")
                    false
                }

                if (!isAvailable) {
                    Timber.d("MainActivity: Resume target unavailable")
                    clearResumeStateUseCase()
                    dismissResumeLoading()
                    Toast.makeText(this@MainActivity, R.string.resume_unavailable, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Determine effective isPlaying flag per resource/media type matrix
                val effectiveIsPlaying = when {
                    resource.type in setOf(ResourceType.SMB, ResourceType.FTP, ResourceType.SFTP)
                            && state.mediaType == MediaType.VIDEO -> false
                    resource.type == ResourceType.CLOUD
                            && state.mediaType == MediaType.VIDEO -> false
                    else -> state.isPlaying
                }

                // Navigate to target screen
                dismissResumeLoading()

                when (state.screenType) {
                    com.sza.fastmediasorter.domain.model.ScreenType.PLAYER -> {
                        val intent = PlayerActivity.createIntent(
                            context = this@MainActivity,
                            resourceId = state.resourceId,
                            skipAvailabilityCheck = true,
                            initialFilePath = state.filePath,
                            isPlaying = effectiveIsPlaying,
                            isSlideshowEnabled = state.isSlideshowEnabled
                        )
                        startActivity(intent)
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                    com.sza.fastmediasorter.domain.model.ScreenType.BROWSER -> {
                        val intent = BrowseActivity.createIntent(
                            context = this@MainActivity,
                            resourceId = state.resourceId,
                            skipAvailabilityCheck = true,
                            initialFolderPath = state.currentFolderPath,
                            initialFilePath = state.filePath,
                            isPlaying = effectiveIsPlaying
                        )
                        startActivity(intent)
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                }
                Timber.d("MainActivity: Resumed playback → ${state.screenType} for ${state.filePath}")
            } catch (e: Exception) {
                Timber.e(e, "MainActivity: Resume playback failed")
                try { clearResumeStateUseCase() } catch (_: Exception) {}
                dismissResumeLoading()
            }
        }
    }

    private suspend fun checkResourceAvailability(
        resource: com.sza.fastmediasorter.domain.model.MediaResource,
        filePath: String
    ): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            when (resource.type) {
                ResourceType.LOCAL -> {
                    java.io.File(filePath).exists()
                }
                ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP, ResourceType.CLOUD -> {
                    val result = resourceRepository.testConnection(resource)
                    result.isSuccess
                }
            }
        }
    }

    private fun dismissResumeLoading() {
        binding.navigationProgressLayout.isVisible = false
    }

    /**
     * Stop AudioPlaybackService before exiting the app.
     * Prevents OS from restarting the process due to foreground service being alive.
     * CRITICAL FIX: Without this, finishAffinity() + Process.killProcess() causes
     * the OS to restart the same process within 1 second (double startup bug).
     */
    private fun stopAudioPlaybackService() {
        try {
            val serviceIntent = Intent(this, AudioPlaybackService::class.java)
            stopService(serviceIntent)
            Timber.d("MainActivity: AudioPlaybackService stopped before exit")
        } catch (e: Exception) {
            Timber.w(e, "MainActivity: Failed to stop AudioPlaybackService before exit")
        }
    }

    companion object {
        const val ACTION_START_SLIDESHOW = "com.sza.fastmediasorter.ACTION_START_SLIDESHOW"
        const val ACTION_RANDOM_MUSIC = "com.sza.fastmediasorter.ACTION_RANDOM_MUSIC"
        const val ACTION_CAMERA_PHOTOS = "com.sza.fastmediasorter.ACTION_CAMERA_PHOTOS"
        const val ACTION_OPEN_FAVORITES = "com.sza.fastmediasorter.ACTION_OPEN_FAVORITES"
        const val ACTION_BROWSE_RESOURCE = "com.sza.fastmediasorter.ACTION_BROWSE_RESOURCE"
        const val EXTRA_SHORTCUT_RESOURCE_ID = "shortcut_resource_id"
        private const val PREFS_NAME_APP = "app_prefs"
        private const val KEY_STORAGE_PERMISSION_REQUESTED = "storage_permission_requested"
    }
}
