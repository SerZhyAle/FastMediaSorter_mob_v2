package com.sza.fastmediasorter.ui.main

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.input.GamepadInputManager
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.core.memory.MemoryCheckpoint
import com.sza.fastmediasorter.core.memory.MemoryProbe
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.domain.model.GamepadAction
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.addresource.AddResourceActivity
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.calculator.CalculatorActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.resourceeditor.ResourceEditorActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.share.LinkAutoDownloadResultPresenter
import com.sza.fastmediasorter.ui.share.ShareDownloadResultBus
import com.sza.fastmediasorter.ui.welcome.WelcomeActivity
import com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator
import com.sza.fastmediasorter.ui.welcome.WelcomeViewModel
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.ui.main.helpers.KeyboardNavigationHandler
import com.sza.fastmediasorter.ui.main.helpers.MainChromeOsBannerManager
import com.sza.fastmediasorter.ui.main.helpers.MainLayoutChromeManager
import com.sza.fastmediasorter.ui.main.helpers.MainMiniGameMenuManager
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
    private lateinit var miniGameMenuManager: MainMiniGameMenuManager
    private var startupFullyDrawnReported = false
    private var isCalculatorEnabled = false
    private var isEmbeddedGameEnabled = false
    private var isCameraOcrEnabled = false

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Result reflected next onResume via hasFullLocalPermissions() */ }

    // S0043: settings-permission launcher removed - Manage Storage intent is now launched via SettingsIntentLauncher (which carries setLaunchBounds for XR / freeform / foldable). Result is delivered through onActivityResult below and forwarded to permissionsHelper.

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

    // S0202: receive terminal share-download outcomes from the LinkDownloadWorker so the user
    // sees a result even if the share Activity finished due to backgrounding/watchdog.
    @Inject
    lateinit var shareResultBus: ShareDownloadResultBus

    @Inject
    lateinit var shareResultPresenter: LinkAutoDownloadResultPresenter

    // S0207 Phase 01: MAIN_DRAWN memory checkpoint emitter.
    @Inject
    lateinit var memoryProbe: MemoryProbe

    // S0391: single availability node for remote sources; drives the resource-type tab strip.
    @Inject
    lateinit var remoteSourceGate: com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // S0207 Phase 01: post the MAIN_DRAWN measurement once the first frame is on screen. BaseActivity.onCreate has already called setContentView(binding.root) by the time we return from super.onCreate(), so binding.root is attached and post() runs after layout.
        binding.root.post {
            memoryProbe.record(MemoryCheckpoint.MAIN_DRAWN)
        }

        // Log config changes to detect unexpected recreations

        // S0202: subscribe to terminal share-download outcomes pushed by LinkDownloadWorker. The worker's foreground notification is the primary feedback channel; this collector is a fallback for when the user has the app foregrounded at the moment of completion (auth-required dialogs and open-in-player intents need an Activity context).
        collectOnLifecycle(shareResultBus.pending) { pending ->
            val isSuccess = pending.result is LinkAutoDownloadCoordinator.Result.Saved ||
                pending.result is LinkAutoDownloadCoordinator.Result.FellBackToDownloads ||
                pending.result is LinkAutoDownloadCoordinator.Result.BatchCompleted
            val isAuthGated = pending.result is LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly
            // Suppression: the worker already posted a result notification for these success kinds - skip the in-Activity toast to avoid double-feedback. SocialPreviewOnly's "Sign in" notification action is the primary path; the in-Activity dialog is also redundant.
            if (pending.notificationShown && (isSuccess || isAuthGated)) {
                return@collectOnLifecycle
            }
            runCatching {
                shareResultPresenter.present(
                    result = pending.result,
                    hostActivity = this@MainActivity,
                    isAuthRetry = false,
                )
            }.onFailure { Timber.w(it, "shareResultPresenter.present failed") }
        }

        // Fix old cloud paths format (cloud:/ → cloud://)
        MediaFilesCacheManager.fixCloudPaths()

        // Check if this is first launch (fast check)
        if (!welcomeViewModel.isWelcomeCompleted()) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        val returnToSettingsRequested = isReturnToSettingsIntent(intent)
        routeToSettingsIfRequested(intent)

        // If restart was triggered from SettingsActivity, return user there
        if (!returnToSettingsRequested && LocaleHelper.consumeReturnToSettings(this)) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        // If AudioPlaybackService is already running when MainActivity is freshly created (Android 8.x OEM ROMs can clear the activity back stack while keeping the foreground service alive), restore the user to the player that is currently playing. FLAG_ACTIVITY_REORDER_TO_FRONT: brings an existing PlayerActivity to the top of the stack without creating a duplicate instance; creates a new one if not present.
        if (!returnToSettingsRequested
            && intent?.action == Intent.ACTION_MAIN
            && AudioPlaybackService.isRunning
            && AudioPlaybackService.currentResourceId > 0L) {
            binding.root.post {
                // Audio playback is inherently a 2D surface. createPanelIntent is kept here as an explicit "open the flat 2D player" semantics marker - every flavor now routes to PlayerActivity directly (immersive VR removed in S0241).
                val playerIntent = PlayerActivity.createPanelIntent(
                    context = this,
                    resourceId = AudioPlaybackService.currentResourceId,
                    initialIndex = AudioPlaybackService.currentInitialIndex,
                    skipAvailabilityCheck = true
                )
                playerIntent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                recordLastPlayedResource(AudioPlaybackService.currentResourceId)
                startActivity(playerIntent)
            }
            // MainActivity continues loading as the back-stack root; do NOT finish().
        }

        // Initialize helpers (binding is already available - set by BaseActivity.onCreate)
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

        // Resume playback logic - only for standard launcher start with killed process
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
        if (intent?.action == ACTION_CAMERA_OCR_TRANSLATE) {
            lifecycleScope.launch {
                val enabled = settingsRepository.getSettings().first().cameraOcrTranslationEnabled
                if (enabled) {
                    startActivity(com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity.createIntent(this@MainActivity))
                } else if (isTaskRoot) {
                    finish()
                }
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

        // S0134: Favorites widget tap routes via extras (not actions). Onboarding extra (empty-state tap) opens Favorites tab + shows tooltip; plain favorites extra (list-tap on widget container) opens Favorites tab silently.
        val onboardingExtraKey = "open_favorites_onboarding"
        val openFavoritesOnboarding = intent?.getBooleanExtra(onboardingExtraKey, false) == true
        val openFavoritesPlain = intent?.getBooleanExtra("open_favorites", false) == true
        if (openFavoritesOnboarding) {
            binding.root.post {
                viewModel.openFavorites()
                com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                    this,
                    R.string.tooltip_favorites_title,
                    R.string.tooltip_favorites_message
                )
            }
            intent?.removeExtra(onboardingExtraKey)
            intent?.removeExtra("open_favorites")
        } else if (openFavoritesPlain) {
            binding.root.post { viewModel.openFavorites() }
            intent?.removeExtra("open_favorites")
        }

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
        setIntent(intent)
        if (routeToSettingsIfRequested(intent)) {
            return
        }
        // Called when MainActivity is already running (singleTop/CLEAR_TOP) and receives a new intent.
        // Handles notification body tap while the app is in the foreground or background stack.
        if (intent.action == ACTION_RESUME_PLAYER) {
            openAudioPlayerFromNotification()
        }
        if (intent.action == ACTION_CAMERA_OCR_TRANSLATE) {
            lifecycleScope.launch {
                val enabled = settingsRepository.getSettings().first().cameraOcrTranslationEnabled
                if (enabled) {
                    startActivity(com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity.createIntent(this@MainActivity))
                } else if (isTaskRoot) {
                    finish()
                }
            }
        }
    }

    private fun routeToSettingsIfRequested(intent: Intent?): Boolean {
        if (!isReturnToSettingsIntent(intent)) {
            return false
        }
        // XR exit must return through the canonical launcher task. Reopening Settings from here
        // keeps MainActivity as the task root so HorizonOS can restore the same panel next time.
        val settingsIntent = Intent(this, SettingsActivity::class.java)
        val initialTab = intent?.getIntExtra(EXTRA_RETURN_TO_SETTINGS_TAB, -1) ?: -1
        if (initialTab >= 0) {
            settingsIntent.putExtra(SettingsActivity.EXTRA_INITIAL_TAB, initialTab)
        }
        startActivity(settingsIntent)
        return true
    }

    private fun isReturnToSettingsIntent(intent: Intent?): Boolean {
        return intent?.getBooleanExtra(EXTRA_RETURN_TO_SETTINGS, false) == true
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
        // Audio playback is inherently a 2D surface - createPanelIntent makes that intent explicit at call sites. All flavors open PlayerActivity directly (immersive VR removed in S0241).
        val playerIntent = PlayerActivity.createPanelIntent(this, resourceId, index, skipAvailabilityCheck = true)
        recordLastPlayedResource(resourceId)
        startActivity(playerIntent)
    }

    override fun onResumeWithViews() {
        if (!startupFullyDrawnReported) {
            startupFullyDrawnReported = true
            binding.root.post {
                reportFullyDrawn()
            }
        }

        // S0289: if returning from PlayerActivity on a non-touch device, restore focus
        // to the resource row that was being played.
        if (isReturningFromAnotherActivity) {
            restoreFocusToLastPlayedResource()
        }

        // Restore previous tab if returning from Favorites Browse - keeps the active tab
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

    // S0043: Manage Storage intent is launched via SettingsIntentLauncher (which carries setLaunchBounds for XR / freeform / foldable). Result arrives here and is forwarded to permissionsHelper for re-evaluation.
    @Deprecated("Required for Settings panel bounds - see S0043")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PermissionHelper.REQUEST_CODE_MANAGE_STORAGE) {
            permissionsHelper.onSettingsResult()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clear UnifiedFileCache when app closes (network file cache) (bitmap thumbnails remain in Glide cache) Skip cleanup if just recreating (rotation, theme change, etc)
        if (isFinishing && !isChangingConfigurations) {
            try {
                val stats = unifiedCache.getCacheStats()
                unifiedCache.clearAll()
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear UnifiedFileCache on app close")
            }
        }
    }

    private var isReturningFromAnotherActivity = false

    // S0289: id of the resource that was last opened in PlayerActivity from this MainActivity instance. Used by onResumeWithViews() to restore focus to the matching list row when the user returns from the player on a TV / Quest3 / keyboard-controlled device.
    private var lastPlayedResourceId: Long? = null

    /** S0289 §2.1: initial focus on the big Play button when the Activity opens on a non-touch device. */
    override fun getInitialFocusView(): View? {
        Timber.d("S0289: main initial-focus / empty-state CTA reachable")
        return binding.btnStartPlayer
    }

    /** S0289 Phase 08: route mouse wheel through the shared activity helper. */
    override fun getMouseScrollTargetView(): View? = binding.rvResources

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastPlayedResourceId?.let { outState.putLong(KEY_LAST_PLAYED_RESOURCE_ID, it) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (savedInstanceState.containsKey(KEY_LAST_PLAYED_RESOURCE_ID)) {
            lastPlayedResourceId = savedInstanceState.getLong(KEY_LAST_PLAYED_RESOURCE_ID)
        }
    }

    /** S0289: record the resource id whenever a PlayerActivity launch is initiated from MainActivity. Called from every `startActivity(playerIntent)` site to give onResumeWithViews() enough context to restore focus on return. */
    private fun recordLastPlayedResource(resourceId: Long) {
        if (resourceId > 0L) lastPlayedResourceId = resourceId
    }

    /** S0289: rebuild the horizontal focus chain across only the currently-visible control-bar buttons. Skipped buttons (View.GONE) drop out of nextFocusLeft / nextFocusRight so the chain stays contiguous after a state-driven visibility flip. */
    private fun restitchControlBarFocusChain() {
        val candidates = listOf(
            binding.btnExit,
            binding.btnAddResource,
            binding.btnFilter,
            binding.btnRefresh,
            binding.btnMainDropdownMenu,
            binding.btnSettings,
            binding.btnToggleView,
            binding.btnFavorites,
            binding.btnStartPlayer
        ).filter { it.visibility == View.VISIBLE }
        if (candidates.isEmpty()) return
        candidates.forEachIndexed { i, btn ->
            val prev = if (i > 0) candidates[i - 1].id else View.NO_ID
            val next = if (i < candidates.lastIndex) candidates[i + 1].id else View.NO_ID
            btn.nextFocusLeftId = prev
            btn.nextFocusRightId = next
        }
    }

    private fun setupMainWindowDropdownMenu() {
        refreshMainWindowDropdownMenuVisibility()
        binding.btnMainDropdownMenu.setOnClickListenerDebounced {
            showMainWindowDropdownMenu()
        }
    }

    private fun refreshMainWindowDropdownMenuVisibility() {
        val shouldShowMenuButton = getMainWindowDropdownMenuItemCount() > 0
        val visibilityChanged = binding.layoutMainDropdownMenu.isVisible != shouldShowMenuButton ||
            binding.btnMainDropdownMenu.isVisible != shouldShowMenuButton
        if (visibilityChanged) {
            binding.layoutMainDropdownMenu.isVisible = shouldShowMenuButton
            binding.btnMainDropdownMenu.isVisible = shouldShowMenuButton
            restitchControlBarFocusChain()
        }
    }

    private fun getMainWindowDropdownMenuItemCount(): Int =
        (if (isCalculatorEnabled) 1 else 0) + (if (isCameraOcrEnabled) 1 else 0) + getMiniGameMenuItemCount()

    private fun getMiniGameMenuItemCount(): Int = miniGameMenuManager.itemCount(isEmbeddedGameEnabled)

    private fun showMainWindowDropdownMenu() {
        val popup = PopupMenu(this, binding.btnMainDropdownMenu)
        val itemCount = populateMainWindowDropdownMenu(popup)
        if (itemCount <= 0) {
            refreshMainWindowDropdownMenuVisibility()
            return
        }

        popup.setForceShowIcon(true)
        popup.setOnMenuItemClickListener { item ->
            if (miniGameMenuManager.handleMenuItem(item.itemId)) {
                true
            } else {
                when (item.itemId) {
                MENU_ITEM_CALCULATOR -> {
                    startActivity(CalculatorActivity.createIntent(this))
                    true
                }
                MENU_ITEM_CAMERA_OCR -> {
                    startActivity(com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity.createIntent(this))
                    true
                }
                else -> false
                }
            }
        }
        popup.show()
    }

    private fun populateMainWindowDropdownMenu(popup: PopupMenu): Int {
        popup.menu.clear()
        if (isCalculatorEnabled) {
            popup.menu.add(0, MENU_ITEM_CALCULATOR, 0, R.string.calculator_title)
                .setIcon(R.drawable.ic_calculator)
        }
        if (isCameraOcrEnabled) {
            popup.menu.add(0, MENU_ITEM_CAMERA_OCR, 0, R.string.setting_camera_ocr_translation_title)
                .setIcon(R.drawable.ic_camera_ocr_translate)
        }
        miniGameMenuManager.populate(popup, isEmbeddedGameEnabled, 1)
        return popup.menu.size()
    }

    /** S0289 §2: when the Activity resumes on a non-touch device with a known last-played resource id, request focus on the matching RecyclerView row so the user lands back where they came from after exiting the player. */
    private fun restoreFocusToLastPlayedResource() {
        if (!shouldRequestInitialFocus()) return
        val id = lastPlayedResourceId ?: return
        val resources = viewModel.state.value.resources
        val position = resources.indexOfFirst { it.id == id }
        if (position < 0) {
            return
        }
        binding.rvResources.post {
            binding.rvResources.scrollToPosition(position)
            val holder = binding.rvResources.findViewHolderForAdapterPosition(position)
            val view = holder?.itemView
            val restored = view?.requestFocus() == true
        }
    }

    override fun setupViews() {
        // Apply edge-to-edge insets: RecyclerView bottom padding for nav bar
        applyEdgeToEdgeInsets()

        miniGameMenuManager = MainMiniGameMenuManager(this)
        setupMainWindowDropdownMenu()
        restitchControlBarFocusChain()

        // S0293 Phase 08: capture effective multi-window availability at adapter construction time.
        // OR-composition (persistent preference OR runtime capability) - same rule as Browse / Player.
        val mainAllowSeparateWindow = kotlinx.coroutines.runBlocking {
            settingsRepository.getSettings().first().allowSeparateWindow
        } || com.sza.fastmediasorter.core.compat.MultiWindowCapabilityDetector.isMultiWindowActiveNow(this)

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
            },
            onMoveToTopClick = { resource -> viewModel.moveResourceToTop(resource) },
            onMoveToBottomClick = { resource -> viewModel.moveResourceToBottom(resource) },
            onScanClick = { resource ->
                viewModel.scanSingleResource(resource)
            },
            // S0293 Phase 08: visible only when multi-window is effectively available (preference OR runtime)
            isOpenInNewWindowVisible = mainAllowSeparateWindow,
            onOpenInNewWindowClick = { resource -> openResourceInNewWindow(resource.id) }
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
            openSettings()
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
        // setupViews() runs inside post{} - initial insets dispatch was already missed.
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

            if (state.isResourceGridMode) {
                binding.btnToggleView.setIconResource(R.drawable.ic_view_list)
            } else {
                binding.btnToggleView.setIconResource(R.drawable.ic_view_grid)
            }

            binding.btnToggleView.isVisible = true
            restitchControlBarFocusChain()

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

        val eventHandler = com.sza.fastmediasorter.ui.main.helpers.MainEventHandler(
            activity = this,
            binding = binding,
            viewModel = viewModel,
            passwordManager = passwordManager,
            onShowError = ::showError,
            onShowInfo = ::showInfo,
            onOpenSettings = ::openSettings,
            onRecordLastPlayed = ::recordLastPlayedResource,
        )
        collectOnLifecycle(viewModel.events) { eventHandler.handle(it) }
        // S0391: rebuild the resource-type tab strip when remote-source availability changes at
        // runtime (a Settings toggle), so a disabled source's tab disappears without an app restart.
        collectOnLifecycle(remoteSourceGate.enabledRemoteSources()) {
            tabsManager.createTabs()
        }
        // Observe settings to show/hide Favorites button
        collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            val calculatorEnabledChanged = isCalculatorEnabled != settings.enableCalculator
            val embeddedGameEnabledChanged = isEmbeddedGameEnabled != settings.embeddedGameEnabled
            val cameraOcrEnabledChanged = isCameraOcrEnabled != settings.cameraOcrTranslationEnabled
            isCalculatorEnabled = settings.enableCalculator
            isEmbeddedGameEnabled = settings.embeddedGameEnabled
            isCameraOcrEnabled = settings.cameraOcrTranslationEnabled
            binding.btnFavorites.visibility = if (settings.enableFavorites) {
                View.VISIBLE
            } else {
                View.GONE
            }
            resourceAdapter.setUseCompactElements(settings.useCompactElements)
            resourceAdapter.setOverflowModeEnabled(settings.resourceOpsInOverflowMenu) // S0160
            layoutChrome.applyCompactToolbar(settings.useCompactElements)
            layoutChrome.refreshGridSpacing()
            if (calculatorEnabledChanged || embeddedGameEnabledChanged || cameraOcrEnabledChanged) {
                refreshMainWindowDropdownMenuVisibility()
            }
            restitchControlBarFocusChain()
        }
    }

    // S0293 Phase 08: launch BrowseActivity for the given resource as a new task so the
    // platform places it in a separate window (Quest 3 panel / DeX desktop / ChromeOS).
    private fun openResourceInNewWindow(resourceId: Long) {
        val windowId = java.util.UUID.randomUUID().toString()
        val intent = Intent(this, BrowseActivity::class.java).apply {
            putExtra(BrowseActivity.EXTRA_RESOURCE_ID, resourceId)
            putExtra(BrowseActivity.EXTRA_WINDOW_ID, windowId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        startActivity(intent)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
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

    /** Show error message respecting showDetailedErrors setting If showDetailedErrors=true: shows ScrollableTextDialog with copyable text and detailed info If showDetailedErrors=false: shows Toast (short notification) */
    private fun showError(message: String, details: String?) {
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            if (settings.showDetailedErrors) {
                // Use ScrollableTextDialog with full details
                com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog.show(
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

    /** Show informational message (not an error, just info about empty folders, etc.) If showDetailedErrors=true: shows ScrollableTextDialog with "Information" title If showDetailedErrors=false: shows Toast */
    private fun showInfo(message: String, details: String?) {
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            if (settings.showDetailedErrors) {
                // Use ScrollableTextDialog but with Information title
                com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog.show(
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
                // Trigger a click on whatever has focus - delegates to the adapter/button listeners.
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
            gate = remoteSourceGate,
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

    /** Stop AudioPlaybackService before exiting the app. Prevents OS from restarting the process due to foreground service being alive. CRITICAL FIX: Without this, finishAffinity() + Process.killProcess() causes the OS to restart the same process within 1 second (double startup bug). */
    private fun stopAudioPlaybackService() {
        try {
            val serviceIntent = Intent(this, AudioPlaybackService::class.java)
            stopService(serviceIntent)
        } catch (e: Exception) {
            Timber.w(e, "MainActivity: Failed to stop AudioPlaybackService before exit")
        }
    }

    companion object {
        const val ACTION_START_SLIDESHOW = "com.sza.fastmediasorter.ACTION_START_SLIDESHOW"
        const val ACTION_RANDOM_MUSIC = "com.sza.fastmediasorter.ACTION_RANDOM_MUSIC"
        const val ACTION_CAMERA_PHOTOS = "com.sza.fastmediasorter.ACTION_CAMERA_PHOTOS"
        const val ACTION_CAMERA_OCR_TRANSLATE = "com.sza.fastmediasorter.action.CAMERA_OCR_TRANSLATE"
        const val ACTION_OPEN_FAVORITES = "com.sza.fastmediasorter.ACTION_OPEN_FAVORITES"
        const val ACTION_BROWSE_RESOURCE = "com.sza.fastmediasorter.ACTION_BROWSE_RESOURCE"
        private const val MENU_ITEM_CALCULATOR = 1
        private const val MENU_ITEM_CAMERA_OCR = 9
        /** Sent by AudioPlaybackService notification contentIntent (tapping the notification body).
         *  Routes the user back to PlayerActivity for the currently playing audio resource. */
        const val ACTION_RESUME_PLAYER = "com.sza.fastmediasorter.ACTION_RESUME_PLAYER"
        const val EXTRA_SHORTCUT_RESOURCE_ID = "shortcut_resource_id"
        const val EXTRA_RETURN_TO_SETTINGS = "extra_return_to_settings"
        const val EXTRA_RETURN_TO_SETTINGS_TAB = "extra_return_to_settings_tab"

        /** S0289: saved-state key for the resource id last opened in PlayerActivity. */
        const val KEY_LAST_PLAYED_RESOURCE_ID = "s0289_last_played_resource_id"

        fun createReturnToSettingsIntent(context: Context, initialTab: Int? = null): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_RETURN_TO_SETTINGS, true)
                if (initialTab != null) {
                    putExtra(EXTRA_RETURN_TO_SETTINGS_TAB, initialTab)
                }
            }
        }
    }
}
