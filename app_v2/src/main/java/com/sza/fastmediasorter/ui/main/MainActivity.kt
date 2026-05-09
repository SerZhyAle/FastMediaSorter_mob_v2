package com.sza.fastmediasorter.ui.main

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.sza.fastmediasorter.core.xr.VrPanelSizePreference
import com.sza.fastmediasorter.core.xr.XrDeviceDetector
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.input.GamepadInputManager
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.domain.model.GamepadAction
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.addresource.AddResourceActivity
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.entry.VrTaskTransition
import com.sza.fastmediasorter.ui.resourceeditor.ResourceEditorActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.welcome.WelcomeActivity
import com.sza.fastmediasorter.ui.welcome.WelcomeViewModel
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.ui.main.helpers.KeyboardNavigationHandler
import com.sza.fastmediasorter.ui.main.helpers.MainChromeOsBannerManager
import com.sza.fastmediasorter.ui.main.helpers.MainLayoutChromeManager
import com.sza.fastmediasorter.ui.main.helpers.MainResourceTabsManager
import com.sza.fastmediasorter.ui.main.helpers.MainResumePlaybackHelper
import com.sza.fastmediasorter.ui.main.helpers.MainStoragePermissionsHelper
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.InputSurface
import com.sza.fastmediasorter.domain.usecase.ClearResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.GetResumeStateUseCase
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import com.sza.fastmediasorter.core.error.ErrorSeverity
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.util.AppErrorNotifier
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
    private lateinit var resumeHelper: MainResumePlaybackHelper
    private lateinit var permissionsHelper: MainStoragePermissionsHelper
    private lateinit var bannerManager: MainChromeOsBannerManager
    private lateinit var tabsManager: MainResourceTabsManager
    private lateinit var layoutChrome: MainLayoutChromeManager

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Result reflected next onResume via hasFullLocalPermissions() */ }

    // S0043: settings-permission launcher removed — Manage Storage intent is now launched via
    // SettingsIntentLauncher (which carries setLaunchBounds for XR / freeform / foldable).
    // Result is delivered through onActivityResult below and forwarded to permissionsHelper.
    
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

    @Inject
    lateinit var gamepadInputManager: GamepadInputManager

    @Inject
    lateinit var keyBindingManager: KeyBindingManager

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // On VR headsets, force landscape orientation so the panel opens correctly.
        // IMPORTANT: Do NOT set window.attributes.width/height here — fixed pixel values
        // lock the window size in HorizonOS and prevent the user from resizing it.
        // Initial large size is handled by <layout> in vr/AndroidManifest.xml (and main
        // AndroidManifest.xml for non-vr flavors). HorizonOS then natively persists the
        // user-adjusted size across sessions — no manual SharedPrefs restore needed.
        if (XrDeviceDetector.isXrHeadset(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Timber.d("MainActivity: VR headset detected — forcing landscape orientation")
        }

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

        // If AudioPlaybackService is already running when MainActivity is freshly created
        // (Android 8.x OEM ROMs can clear the activity back stack while keeping the foreground
        // service alive), restore the user to the player that is currently playing.
        // FLAG_ACTIVITY_REORDER_TO_FRONT: brings an existing PlayerActivity to the top of the
        // stack without creating a duplicate instance; creates a new one if not present.
        if (intent?.action == Intent.ACTION_MAIN
            && AudioPlaybackService.isRunning
            && AudioPlaybackService.currentResourceId > 0L) {
            Timber.d("MainActivity: service active on fresh launch — restoring PlayerActivity (resourceId=${AudioPlaybackService.currentResourceId})")
            binding.root.post {
                val playerIntent = PlayerActivity.createIntent(
                    context = this,
                    resourceId = AudioPlaybackService.currentResourceId,
                    initialIndex = AudioPlaybackService.currentInitialIndex,
                    skipAvailabilityCheck = true
                )
                if (VrTaskTransition.shouldEnterImmersiveTask(playerIntent)) {
                    // FLAG_ACTIVITY_REORDER_TO_FRONT is meaningless once enterImmersive
                    // forces a fresh task via FLAG_ACTIVITY_NEW_TASK, so skip it here.
                    VrTaskTransition.enterImmersive(this, playerIntent)
                } else {
                    playerIntent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(playerIntent)
                }
            }
            // MainActivity continues loading as the back-stack root; do NOT finish().
        }

        // Initialize helpers (binding is already available — set by BaseActivity.onCreate)
        resumeHelper = MainResumePlaybackHelper(
            activity = this,
            binding = binding,
            settingsRepository = settingsRepository,
            resourceRepository = resourceRepository,
            getResumeStateUseCase = getResumeStateUseCase,
            clearResumeStateUseCase = clearResumeStateUseCase
        )
        permissionsHelper = MainStoragePermissionsHelper(
            activity = this,
            storagePermissionLauncher = storagePermissionLauncher
        )
        bannerManager = MainChromeOsBannerManager(this)
        layoutChrome = MainLayoutChromeManager(
            activity = this,
            binding = binding,
            isResourceGridMode = { viewModel.state.value.isResourceGridMode }
        )

        // Resume playback logic — only for standard launcher start with killed process
        if (resumeHelper.shouldAttemptResume(intent)) {
            resumeHelper.attemptResumePlayback()
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
        if (intent?.action == ACTION_RESUME_PLAYER) {
            // Notification body tap: reopen the player for the currently playing audio track.
            // The resource/index are stored in AudioPlaybackService companion by PlayerMediaLoaderManager.
            binding.root.post { openAudioPlayerFromNotification() }
        }
        
        // Initialize keyboard navigation handler
        keyboardNavigationHandler = KeyboardNavigationHandler(
            context = this,
            recyclerView = binding.rvResources,
            viewModel = viewModel,
            onDeleteConfirmation = { resource -> showDeleteConfirmation(resource) },
            onAddResourceClick = { binding.btnAddResource.performClick() },
            onFilterClick = { binding.btnFilter.performClick() },
            onExit = {
                stopAudioPlaybackService()
                finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
            },
            onShowHelp = {
                InputHelpDialogFragment.show(supportFragmentManager, InputSurface.MAIN)
            },
            onEditResourceClick = { resource ->
                if (!resource.accessPin.isNullOrBlank()) {
                    passwordManager.checkResourcePinForEdit(resource)
                } else {
                    startActivity(ResourceEditorActivity.createEditIntent(this, resource.id))
                }
            }
        )
        
        // Initialize password manager for PIN-protected resources
        passwordManager = ResourcePasswordManager(
            context = this,
            layoutInflater = layoutInflater
        )
        
        // UI setup and resource loading deferred to setupViews() via BaseActivity.onCreate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Called when MainActivity is already running (singleTop/CLEAR_TOP) and receives a new intent.
        // Handles notification body tap while the app is in the foreground or background stack.
        if (intent.action == ACTION_RESUME_PLAYER) {
            openAudioPlayerFromNotification()
        }
    }

    /** Navigate to PlayerActivity for the currently playing audio resource.
     *  Called from notification contentIntent (ACTION_RESUME_PLAYER) via onCreate/onNewIntent. */
    private fun openAudioPlayerFromNotification() {
        val resourceId = AudioPlaybackService.currentResourceId
        val index = AudioPlaybackService.currentInitialIndex
        if (resourceId <= 0L) {
            Timber.w("openAudioPlayerFromNotification: no valid resourceId stored, ignoring")
            return
        }
        Timber.d("openAudioPlayerFromNotification: resourceId=$resourceId index=$index")
        val playerIntent = PlayerActivity.createIntent(this, resourceId, index, skipAvailabilityCheck = true)
        if (VrTaskTransition.shouldEnterImmersiveTask(playerIntent)) {
            VrTaskTransition.enterImmersive(this, playerIntent)
        } else {
            startActivity(playerIntent)
        }
    }

    override fun onResumeWithViews() {
        // Restore previous tab if returning from Favorites Browse — keeps the active tab
        // sticky after the user navigates back from the Favorites Browse screen.
        if (viewModel.state.value.previousTab != null) {
            viewModel.restorePreviousTab()
        }

        // Sync TabLayout with ViewModel state. FAVORITES is action-only (opens Browse), not a filter.
        val currentTab = viewModel.state.value.activeResourceTab
        if (currentTab != ResourceTab.FAVORITES) {
            val tabPosition = getTabIndexForResourceTab(currentTab)
            if (binding.tabResourceTypes.selectedTabPosition != tabPosition) {
                binding.tabResourceTypes.selectTab(binding.tabResourceTypes.getTabAt(tabPosition))
            }
        }

        if (isReturningFromAnotherActivity) {
            viewModel.refreshResources()
        }

        permissionsHelper.checkLocalPermissionsOnStartup()
        bannerManager.showIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        isReturningFromAnotherActivity = true
    }

    // S0043: Manage Storage intent is launched via SettingsIntentLauncher (which carries
    // setLaunchBounds for XR / freeform / foldable). Result arrives here and is forwarded
    // to permissionsHelper for re-evaluation.
    @Deprecated("Required for Settings panel bounds — see S0043")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PermissionHelper.REQUEST_CODE_MANAGE_STORAGE) {
            permissionsHelper.onSettingsResult()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // On VR headsets, HorizonOS delivers window resize events via onConfigurationChanged.
        // Persist the new size so it is restored on the next cold start.
        if (!XrDeviceDetector.isXrHeadset(this)) return
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val sz = android.graphics.Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(sz)
            android.graphics.Rect(0, 0, sz.x, sz.y)
        }
        Timber.d("MainActivity: VR panel resize detected — ${bounds.width()}x${bounds.height()}")
        VrPanelSizePreference.save(this, bounds.width(), bounds.height())
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
            onIconClick = { resource ->
                viewModel.startSlideshowFor(resource)
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
        layoutChrome.updateToolbarButtonLabels(resources.configuration)
        
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
        
        collectOnLifecycle(viewModel.state) { state ->
            resourceAdapter.submitList(state.resources)
            resourceAdapter.setSelectedResource(state.selectedResource?.id)
            resourceAdapter.setViewMode(state.isResourceGridMode)

            if (state.resources.isEmpty()) {
                // Reset the shared focus state when the list disappears so the
                // next keyboard action can restore focus deterministically.
                keyboardNavigationHandler.clearFocus()
            }

            // Update layout manager based on mode and screen size
            layoutChrome.updateLayoutManagerForScreenSize()

            // Update toggle button icon
            if (state.isResourceGridMode) {
                binding.btnToggleView.setIconResource(R.drawable.ic_view_list)
            } else {
                binding.btnToggleView.setIconResource(R.drawable.ic_view_grid)
            }

            binding.btnToggleView.isVisible = true

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

        collectOnLifecycle(viewModel.loading) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        // Handle navigation progress (connection test during resource open)
        collectOnLifecycle(viewModel.state) { state ->
            binding.navigationProgressLayout.isVisible = state.isNavigating
            if (state.isNavigating && state.navigationMessage != null) {
                binding.tvNavigationMessage.text = state.navigationMessage
            }
        }

        collectOnLifecycle(viewModel.error) { errorMessage ->
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
        
        collectOnLifecycle(viewModel.events) { event ->
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
                    if (VrTaskTransition.shouldEnterImmersiveTask(intent)) {
                        VrTaskTransition.enterImmersive(this@MainActivity, intent)
                    } else {
                        startActivity(intent)
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
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
                    if (VrTaskTransition.shouldEnterImmersiveTask(intent)) {
                        VrTaskTransition.enterImmersive(this@MainActivity, intent)
                    } else {
                        startActivity(intent)
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
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
                    if (isFinishing || isDestroyed) return@collectOnLifecycle
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
        // Observe settings to show/hide Favorites button
        collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            binding.btnFavorites.visibility = if (settings.enableFavorites) {
                View.VISIBLE
            } else {
                View.GONE
            }
            resourceAdapter.setUseCompactElements(settings.useCompactElements)
            layoutChrome.applyCompactToolbar(settings.useCompactElements)
            layoutChrome.refreshGridSpacing()
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
    
    /** Recalculates grid layout, toolbar labels and tabs after screen rotation. */
    override fun onLayoutConfigurationChanged(newConfig: Configuration) {
        layoutChrome.updateToolbarButtonLabels(newConfig)
        layoutChrome.updateLayoutManagerForScreenSize()

        // Recreate tabs to apply new inline/stacked label configuration
        binding.tabResourceTypes.removeAllTabs()
        tabsManager.createTabs()
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
                AppErrorNotifier.show(
                    activity = this@MainActivity,
                    message = message,
                    severity = ErrorSeverity.CRITICAL,
                    showDetailedErrors = false
                )
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Gamepad buttons first. D-pad / left stick focus moves are left to Android's
        // default focus search (super), which already works with focusable="true" items.
        val action = gamepadInputManager.handleKeyEvent(event, GamepadInputManager.Surface.BROWSER)
        if (action is GamepadAction.BrowserAction && routeBrowserGamepadAction(action)) return true
        if (event.action == KeyEvent.ACTION_DOWN) {
            val commandId = keyBindingManager.resolveKeyAction(event.keyCode, event.metaState, com.sza.fastmediasorter.domain.input.InputSurface.BROWSER)
            if (commandId != null && routeMainCommandId(commandId)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun routeMainCommandId(commandId: String): Boolean {
        return if (::keyboardNavigationHandler.isInitialized) {
            keyboardNavigationHandler.dispatchCommandId(commandId)
        } else false
    }

    private fun routeBrowserGamepadAction(action: GamepadAction.BrowserAction): Boolean {
        when (action) {
            is GamepadAction.BrowserAction.Select -> {
                // Trigger a click on whatever has focus — delegates to the adapter/button listeners.
                currentFocus?.performClick() ?: return false
            }
            is GamepadAction.BrowserAction.Back -> onBackPressedDispatcher.onBackPressed()
            is GamepadAction.BrowserAction.MultiSelect -> {
                // Resource list is single-select; fall back to Select semantics for consistency.
                currentFocus?.performClick() ?: return false
            }
            is GamepadAction.BrowserAction.ContextMenu -> {
                // Long-press the focused resource row to surface its menu.
                currentFocus?.performLongClick() ?: return false
            }
            is GamepadAction.BrowserAction.Search -> binding.btnFilter.performClick()
            is GamepadAction.BrowserAction.SwitchTab -> {
                val tabs = binding.tabResourceTypes
                val count = tabs.tabCount
                if (count <= 1) return false
                val current = tabs.selectedTabPosition.coerceAtLeast(0)
                val next = if (action.forward) (current + 1) % count else (current - 1 + count) % count
                tabs.selectTab(tabs.getTabAt(next))
            }
        }
        return true
    }
    
    private fun setupResourceTypeTabs() {
        tabsManager = MainResourceTabsManager(
            tabLayout = binding.tabResourceTypes,
            configuration = resources.configuration,
            onTabSelected = { tab -> viewModel.setActiveTab(tab) },
            onFavoritesReselected = { viewModel.openFavorites() },
            getActiveTab = { viewModel.state.value.activeResourceTab },
            getPreviousTab = { viewModel.state.value.previousTab }
        )
        tabsManager.createTabs()
        tabsManager.setupListener()
    }

    private fun getTabIndexForResourceTab(tab: ResourceTab): Int =
        tabsManager.getTabIndexForResourceTab(tab)

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
        /** Sent by AudioPlaybackService notification contentIntent (tapping the notification body).
         *  Routes the user back to PlayerActivity for the currently playing audio resource. */
        const val ACTION_RESUME_PLAYER = "com.sza.fastmediasorter.ACTION_RESUME_PLAYER"
        const val EXTRA_SHORTCUT_RESOURCE_ID = "shortcut_resource_id"
    }
}
