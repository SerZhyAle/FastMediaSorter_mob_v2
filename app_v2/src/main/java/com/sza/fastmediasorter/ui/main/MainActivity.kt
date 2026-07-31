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
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DefaultItemAnimator
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.input.GamepadInputManager
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.core.memory.MemoryCheckpoint
import com.sza.fastmediasorter.core.memory.MemoryProbe
import com.sza.fastmediasorter.core.network.NetworkContextAnalyzer
import com.sza.fastmediasorter.core.orientation.isWideLayout
import com.sza.fastmediasorter.core.screencapture.ScreenRecordingStateController
import com.sza.fastmediasorter.core.screencapture.ScreenVideoRecordingController
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.ui.UiState
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.GamepadAction
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.usecase.ClearResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.GetResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator
import com.sza.fastmediasorter.ui.addresource.AddResourceActivity
import com.sza.fastmediasorter.ui.calculator.helpers.CalculatorAprilFoolsPrankManager
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.InputHelpFirstRunHint
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.icon.ResourceIconComposer
import com.sza.fastmediasorter.ui.main.helpers.KeyboardNavigationHandler
import com.sza.fastmediasorter.ui.main.helpers.MainCameraCaptureManager
import com.sza.fastmediasorter.ui.main.helpers.MainChromeOsBannerManager
import com.sza.fastmediasorter.ui.main.helpers.MainCommandBarTooltipManager
import com.sza.fastmediasorter.ui.main.helpers.MainExitButtonManager
import com.sza.fastmediasorter.ui.main.helpers.MainLayoutChromeManager
import com.sza.fastmediasorter.ui.main.helpers.MainLinkDownloadManager
import com.sza.fastmediasorter.ui.main.helpers.MainLinkDownloadMenuManager
import com.sza.fastmediasorter.ui.main.helpers.MainMiniGameMenuManager
import com.sza.fastmediasorter.ui.main.helpers.MainPanelItemActionsManager
import com.sza.fastmediasorter.ui.main.helpers.MainProgramsMenuCoordinator
import com.sza.fastmediasorter.ui.main.helpers.MainProgramsPanelManager
import com.sza.fastmediasorter.ui.main.helpers.MainQuickCaptureMenuManager
import com.sza.fastmediasorter.ui.main.helpers.MainResourceTabsManager
import com.sza.fastmediasorter.ui.main.helpers.MainResumePlaybackHelper
import com.sza.fastmediasorter.ui.main.helpers.MainScreenRecordingManager
import com.sza.fastmediasorter.ui.main.helpers.MainScreenRecordingMenuManager
import com.sza.fastmediasorter.ui.main.helpers.MainSftpShareManager
import com.sza.fastmediasorter.ui.main.helpers.MainStoragePermissionsHelper
import com.sza.fastmediasorter.ui.main.helpers.MainStreamsMenuManager
import com.sza.fastmediasorter.ui.main.helpers.MainStreamsPanelManager
import com.sza.fastmediasorter.ui.main.helpers.MainVoiceCaptureManager
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.ui.main.helpers.ResourceVrCinemaLaunchManager
import com.sza.fastmediasorter.ui.main.helpers.StartupNoticeManager
import com.sza.fastmediasorter.ui.main.helpers.StreamsPanelMenuActions
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.resourceeditor.ResourceEditorActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.share.LinkAutoDownloadResultPresenter
import com.sza.fastmediasorter.ui.share.ShareDownloadResultBus
import com.sza.fastmediasorter.ui.streams.StreamsActivity
import com.sza.fastmediasorter.ui.welcome.WelcomeActivity
import com.sza.fastmediasorter.ui.welcome.WelcomeViewModel
import com.sza.fastmediasorter.util.getPackageInfoCompat
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
import com.sza.fastmediasorter.widget.ResourceShortcutPinManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@UnstableApi
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
    private lateinit var streamsMenuManager: MainStreamsMenuManager
    private lateinit var voiceCaptureManager: MainVoiceCaptureManager
    private lateinit var cameraCaptureManager: MainCameraCaptureManager
    private lateinit var quickCaptureMenuManager: MainQuickCaptureMenuManager
    private lateinit var linkDownloadMenuManager: MainLinkDownloadMenuManager
    private lateinit var linkDownloadManager: MainLinkDownloadManager
    private lateinit var exitButtonManager: MainExitButtonManager
    private lateinit var programsMenuCoordinator: MainProgramsMenuCoordinator
    private lateinit var screenRecordingMenuManager: MainScreenRecordingMenuManager
    private lateinit var screenRecordingManager: MainScreenRecordingManager
    private lateinit var programsPanelManager: MainProgramsPanelManager
    private lateinit var streamsPanelManager: MainStreamsPanelManager

    // S0831/S0770: per-item context-menu actions for the programs/streams panels + new-window primitives.
    private lateinit var panelItemActions: MainPanelItemActionsManager

    // S0984: builds the "share SFTP access" dialog; lazy since only SFTP resources reach it.
    private val sftpShareManager by lazy { MainSftpShareManager(this) }
    private var startupFullyDrawnReported = false
    private var startupAprilFoolsPrankChecked = false
    private var isCalculatorEnabled = false
    private var isEmbeddedGameEnabled = false
    private var isCameraOcrEnabled = false
    private var isQuickVoiceEnabled = false
    private var isQuickVideoEnabled = false
    private var isQuickPhotoEnabled = false
    private var isLinkDownloadEnabled = false
    private var isStreamsEnabled = false

    // S0774: settings toggle AND a bound ScreenVideoRecordingController (capability present).
    private var isScreenRecordingEnabled = false

    // S0755/S0756: main-window panel toggles, mirrored from settings; drive panel visibility + the
    // three-dots button suppression (programs panel) and the Streams-item dedup (streams panel).
    private var isProgramsPanelEnabled = false
    private var isStreamsPanelEnabled = false

    // S0770: latest settings snapshot from the collector, so the panel item menus can read the
    // multi-window flag synchronously and base a "Remove" (disable) write on the current state.
    private var latestSettings: AppSettings? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Result reflected next onResume via hasFullLocalPermissions() */ }

    // S0523: quick-capture launchers MUST be registered before the Activity is STARTED. setupViews()
    // (where the capture managers are constructed) runs post-STARTED via a posted lambda, so the
    // launchers live here as field initializers and the host delegates results to the lateinit managers.
    private val quickCaptureRecordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (::voiceCaptureManager.isInitialized) voiceCaptureManager.onRecordAudioResult(granted) }

    private val quickCaptureCameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> if (::cameraCaptureManager.isInitialized) cameraCaptureManager.handleResult(result) }

    // S0774: screen-recording permission launchers - registered pre-STARTED, delegated to the manager.
    private val screenRecordingRecordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (::screenRecordingManager.isInitialized) screenRecordingManager.onRecordAudioResult(granted) }

    private val screenRecordingPostNotificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (::screenRecordingManager.isInitialized) {
            screenRecordingManager.onPostNotificationsResult(granted)
        }
    }

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

    // S1152: last-active-stream store, read by the resume helper to resume a stream on cold start.
    @Inject
    lateinit var streamResumeStateRepository: com.sza.fastmediasorter.domain.repository.StreamResumeStateRepository

    @Inject
    lateinit var resourceRepository: ResourceRepository

    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

    @Inject
    lateinit var capabilityAvailability: CapabilityAvailability

    // S0963 (Pillar 2): XR-gated launcher for the resource "Open in VR Cinema" entry (No-Op on non-VR).
    @Inject
    lateinit var resourceVrCinemaLaunchManager: ResourceVrCinemaLaunchManager

    // S0774: empty except on standard (fms.screenCapture=on) + noLegal; gates the screen-recording scenario.
    @Inject
    lateinit var screenVideoRecordingControllers: Set<@JvmSuppressWildcards ScreenVideoRecordingController>

    @Inject
    lateinit var screenRecordingStateController: ScreenRecordingStateController

    @Inject
    lateinit var localDestinationClassifier: LocalDestinationClassifier

    @Inject
    lateinit var localDestinationWriter: LocalDestinationWriter

    @Inject
    lateinit var statsSink: StatsSink

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

    @Inject
    lateinit var resourceShortcutPinManager: ResourceShortcutPinManager

    // S0756: favicon atlas for the main-window streams panel.
    @Inject
    lateinit var faviconAtlasStore: FaviconAtlasStore

    @Inject
    lateinit var networkContextAnalyzer: NetworkContextAnalyzer

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // S0564: restore the pending quick-capture target if the process was killed while the camera
        // host was foreground. setupViews() (run inside super.onCreate) already constructed the manager,
        // so this runs before the pending Activity result is dispatched (onStart) - mirrors BrowseActivity.
        savedInstanceState?.let {
            if (::cameraCaptureManager.isInitialized) cameraCaptureManager.restoreState(it)
        }

        // S0207 Phase 01: post the MAIN_DRAWN measurement once the first frame is on screen. BaseActivity.onCreate has already called setContentView(binding.root) by the time we return from super.onCreate(), so binding.root is attached and post() runs after layout.
        binding.root.post {
            memoryProbe.record(MemoryCheckpoint.MAIN_DRAWN)
        }

        // Log config changes to detect unexpected recreations

        // S0202: subscribe to terminal share-download outcomes pushed by LinkDownloadWorker. The worker's foreground notification is the primary feedback channel; this collector is a fallback for when the user has the app foregrounded at the moment of completion (auth-required dialogs and open-in-player intents need an Activity context).
        collectOnLifecycle(shareResultBus.pending) { pending ->
            val isAuthGated = pending.result is LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly
            // S0202: SocialPreviewOnly already surfaces a "Sign in" notification action, so the in-Activity
            // dialog is redundant - suppress it. S0981: success kinds are NOT suppressed here; the presenter
            // honours linkAutoDownloadOpenInPlayer (auto-open) and suppresses only the duplicate toast.
            if (pending.notificationShown && isAuthGated) {
                shareResultBus.clearReplayCache()
                return@collectOnLifecycle
            }
            runCatching {
                shareResultPresenter.present(
                    result = pending.result,
                    hostActivity = this@MainActivity,
                    notificationShown = pending.notificationShown,
                    isAuthRetry = false,
                )
            }.onFailure { Timber.w(it, "shareResultPresenter.present failed") }
            // S0981: consume-once. replay = 1 would otherwise re-deliver the same terminal result on
            // every return to MainActivity (or recreation), re-opening the player over the current screen.
            shareResultBus.clearReplayCache()
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

        // S1153: defer the disk-reading startup notices (S0731 DB-reset, S0490 crash prompt) off the
        // main thread and past first frame. Scheduled here, after all early-return redirects, so a
        // finishing MainActivity never schedules a dialog it would immediately dismiss.
        StartupNoticeManager(this).presentDeferredNotices(showCrashPrompt = savedInstanceState == null)

        // S0510: one-shot first-run hint for non-touch users - "press F1 for shortcuts".
        binding.root.post { InputHelpFirstRunHint.showIfNeeded(this) }

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
            clearResumeStateUseCase = clearResumeStateUseCase,
            streamResumeStateRepository = streamResumeStateRepository
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
            // S0759: the keyboard exit shortcut keeps full-exit semantics (the minimize/close split is
            // owned by the on-screen exit button per scope Q6).
            onExit = { performFullExit() },
            onShowHelp = {
                InputHelpDialogFragment.show(supportFragmentManager, UiSurface.MAIN)
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
        if (!startupAprilFoolsPrankChecked) {
            startupAprilFoolsPrankChecked = true
            binding.root.post {
                if (!isFinishing && !isDestroyed) {
                    CalculatorAprilFoolsPrankManager(this).maybeShowDailyAprilFoolsPrank()
                }
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

        // S0759: recompute the exit/minimize mode from live background activity each time the main
        // window resumes (a service may have started/stopped while away). The settings collector keeps
        // the edge-gesture trigger fresh; this covers the foreground-service / scheduled-op triggers.
        if (::exitButtonManager.isInitialized) exitButtonManager.refresh()
    }

    override fun onPause() {
        super.onPause()
        isReturningFromAnotherActivity = true
        if (::voiceCaptureManager.isInitialized) voiceCaptureManager.release()
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
        // Before super: the dialog's window must go while this Activity is still its valid host,
        // otherwise a configuration recreate leaks it (S1197). lateinit, so guard - an early
        // finish() can destroy the Activity before setupViews() ever assigns the helper.
        if (::permissionsHelper.isInitialized) {
            permissionsHelper.dismissPendingDialog()
        }
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
        return binding.btnStartPlayer
    }

    /** S0289 Phase 08: route mouse wheel through the shared activity helper. */
    override fun getMouseScrollTargetView(): View? = binding.rvResources

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastPlayedResourceId?.let { outState.putLong(KEY_LAST_PLAYED_RESOURCE_ID, it) }
        // S0564: persist the pending quick-capture target so a kill while the camera host is
        // foreground does not abandon the captured file (restored in onCreate, see below).
        if (::cameraCaptureManager.isInitialized) cameraCaptureManager.saveState(outState)
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

    private fun setupMainWindowDropdownMenu() {
        refreshMainWindowDropdownMenuVisibility()
        binding.btnMainDropdownMenu.setOnClickListenerDebounced {
            showMainWindowDropdownMenu()
        }
    }

    private fun refreshMainWindowDropdownMenuVisibility() {
        // S0755: when the programs panel replaces the menu on the main window, hide the three-dots button.
        val shouldShowMenuButton = !isProgramsPanelEnabled && getMainWindowDropdownMenuItemCount() > 0
        val visibilityChanged = binding.layoutMainDropdownMenu.isVisible != shouldShowMenuButton ||
            binding.btnMainDropdownMenu.isVisible != shouldShowMenuButton
        if (visibilityChanged) {
            binding.layoutMainDropdownMenu.isVisible = shouldShowMenuButton
            binding.btnMainDropdownMenu.isVisible = shouldShowMenuButton
            layoutChrome.restitchControlBarFocusChain()
        }
    }

    /**
     * S0755/S0756: recompute both main-window panels from the current settings flags. The streams panel
     * also gates on flavor support + the Streams master toggle; its visibility feeds the programs panel
     * as the Streams-item dedup signal, and the programs panel's own visibility hides the three-dots button.
     */
    private fun refreshPanels() {
        if (!::programsPanelManager.isInitialized || !::streamsPanelManager.isInitialized) return
        val streamsPanelVisible =
            capabilityAvailability.isStreamsAvailable() && isStreamsEnabled && isStreamsPanelEnabled
        streamsPanelManager.setVisible(streamsPanelVisible)
        programsPanelManager.update(visible = isProgramsPanelEnabled, excludeStreams = streamsPanelVisible)
        refreshMainWindowDropdownMenuVisibility()
    }

    private fun getMainWindowDropdownMenuItemCount(): Int =
        programsMenuCoordinator.itemCount(currentProgramsMenuGate())

    // S0774: resolve the runtime flags + media capabilities into the coordinator's gate snapshot. The
    // flags are mutated by the settings collector, so this is recomputed on every menu build.
    private fun currentProgramsMenuGate() = MainProgramsMenuCoordinator.ProgramsMenuGate(
        streams = capabilityAvailability.isStreamsAvailable() && isStreamsEnabled,
        // S0962 (VR Cinema, Pillar 1): visible only on an XR device with the VR-3D master toggle on; the
        // launch manager mirrors that runtime state (same gate as the file/resource context-menu items).
        vrCinema = resourceVrCinemaLaunchManager.isAvailable,
        quickVoice = isQuickVoiceEnabled && mediaCapabilities.supportsMicRecording,
        quickCamera = (isQuickPhotoEnabled && mediaCapabilities.supportsImages) ||
            (isQuickVideoEnabled && mediaCapabilities.supportsVideo),
        calculator = isCalculatorEnabled,
        cameraOcr = isCameraOcrEnabled,
        linkDownload = isLinkDownloadEnabled,
        miniGame = isEmbeddedGameEnabled,
        screenRecording = isScreenRecordingEnabled,
    )

    private fun showMainWindowDropdownMenu() {
        val popup = PopupMenu(this, binding.btnMainDropdownMenu)
        val itemCount = populateMainWindowDropdownMenu(popup)
        if (itemCount <= 0) {
            refreshMainWindowDropdownMenuVisibility()
            return
        }

        popup.setForceShowIcon(true)
        popup.setOnMenuItemClickListener { item -> handleMainWindowMenuItem(item.itemId) }
        popup.show()
    }

    /** S0755: shared click routing for both the dropdown popup and the programs panel buttons. */
    private fun handleMainWindowMenuItem(itemId: Int): Boolean =
        programsMenuCoordinator.handleMenuItem(itemId)

    // S0756: excludeStreams drops the "Streams" item (the programs panel hides it when the streams
    // panel is visible, to avoid duplicating that entry point). The dropdown menu always passes false.
    private fun populateMainWindowDropdownMenu(popup: PopupMenu, excludeStreams: Boolean = false): Int =
        programsMenuCoordinator.populate(popup, excludeStreams, currentProgramsMenuGate())

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
        layoutChrome.applyEdgeToEdgeInsets()

        miniGameMenuManager = MainMiniGameMenuManager(this)
        streamsMenuManager = MainStreamsMenuManager(this)
        voiceCaptureManager = MainVoiceCaptureManager(
            this, lifecycleScope, localDestinationClassifier, localDestinationWriter, statsSink,
            requestRecordAudioPermission = {
                quickCaptureRecordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            },
        )
        cameraCaptureManager = MainCameraCaptureManager(
            this, lifecycleScope, viewModel::saveCapturedMedia, quickCaptureCameraLauncher,
        )
        quickCaptureMenuManager = MainQuickCaptureMenuManager(
            onVoice = { voiceCaptureManager.start() },
            onCamera = {
                cameraCaptureManager.captureCamera(
                    photoAvailable = isQuickPhotoEnabled && mediaCapabilities.supportsImages,
                    videoAvailable = isQuickVideoEnabled && mediaCapabilities.supportsVideo,
                )
            },
        )
        linkDownloadManager = MainLinkDownloadManager(this)
        linkDownloadMenuManager = MainLinkDownloadMenuManager(
            onLinkDownload = { linkDownloadManager.show() },
        )
        screenRecordingManager = MainScreenRecordingManager(
            activity = this,
            controller = screenVideoRecordingControllers.firstOrNull(),
            stateController = screenRecordingStateController,
            requestRecordAudioPermission = {
                screenRecordingRecordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            },
            requestPostNotificationsPermission = {
                screenRecordingPostNotificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            },
        )
        screenRecordingManager.bind(this)
        screenRecordingMenuManager = MainScreenRecordingMenuManager(
            onScreenRecording = { screenRecordingManager.start() },
        )
        // S0831/S0770: per-item panel actions (new-window launch + Remove/Disable confirms). Constructed
        // before the coordinator/menu-actions below, which delegate to it.
        panelItemActions = MainPanelItemActionsManager(
            activity = this,
            settingsRepository = settingsRepository,
            unpinStreamSource = viewModel::unpinStreamSource,
            currentSettings = { latestSettings },
            resourceVrCinema = resourceVrCinemaLaunchManager,
        )
        programsMenuCoordinator = MainProgramsMenuCoordinator(
            activity = this,
            miniGameMenuManager = miniGameMenuManager,
            streamsMenuManager = streamsMenuManager,
            quickCaptureMenuManager = quickCaptureMenuManager,
            linkDownloadMenuManager = linkDownloadMenuManager,
            screenRecordingMenuManager = screenRecordingMenuManager,
            hostActions = MainProgramsMenuCoordinator.ProgramsHostActions(
                isNewWindowAvailable = { panelItemActions.isNewWindowAvailable() },
                launchInNewWindow = { intent -> panelItemActions.launchInNewWindow(intent) },
                confirmRemoveProgram = { titleRes, apply ->
                    panelItemActions.confirmRemoveProgram(titleRes, apply)
                },
                // S0962 (VR Cinema, Pillar 1): tap prompts for a resource, then opens the immersive browser.
                onVrCinemaSelected = { resourceVrCinemaLaunchManager.promptResourceAndLaunch() },
            ),
        )
        // S0755: programs panel renders the same menu the dropdown builds (single source of order/gates).
        programsPanelManager = MainProgramsPanelManager(
            panel = binding.mainProgramsPanel,
            populateMenu = { popup, excludeStreams -> populateMainWindowDropdownMenu(popup, excludeStreams) },
            onItemSelected = { itemId -> handleMainWindowMenuItem(itemId) },
            // S0770: per-item menu providers - new-window launch + "Remove" (disable), null when absent.
            newWindowActionFor = { itemId -> programsMenuCoordinator.newWindowActionFor(itemId) },
            removeActionFor = { itemId -> programsMenuCoordinator.removeActionFor(itemId) },
            // S0780: "Configure" opens the program/scenario behaviour settings group.
            onConfigure = { startActivity(SettingsActivity.openProgramsSectionIntent(this)) },
            // S0807: "Hide panel" drops the panel; the collector restores the top command-bar three-dots.
            onHidePanel = { panelItemActions.hideProgramsPanelFromPanel() },
            settingsRepository = settingsRepository,
            scope = lifecycleScope,
            // S0809: collapsed chip lives in the shared collapsed-panels row (activity layout).
            collapsedChip = binding.chipProgramsCollapsed,
        )
        // S0807: wire the leading header menu + collapsed-strip tap; load the persisted collapsed state.
        programsPanelManager.install()
        // S0756/S0777: streams panel - entry button + pinned channels. An AUDIO channel tap plays inline
        // in the home window (the panel owns the now-playing mini-control); a VIDEO/RTSP tap falls back to
        // the Streams screen.
        streamsPanelManager = MainStreamsPanelManager(
            panel = binding.mainStreamsPanel,
            lifecycleOwner = this,
            scope = lifecycleScope,
            observePinnedStreamSources = viewModel::pinnedStreamSources,
            faviconAtlasStore = faviconAtlasStore,
            onOpenStreams = { startActivity(Intent(this, StreamsActivity::class.java)) },
            // S0777: AUDIO taps play inline (this coordinator drives the bottom now-playing control); a
            // VIDEO/RTSP tap defers to the Streams screen via onPlayVideo.
            inlineAudio = com.sza.fastmediasorter.ui.main.helpers.MainStreamsInlineAudioManager(
                lifecycleOwner = this,
                binding = binding,
                hasNetwork = networkContextAnalyzer::hasAnyNetwork,
                isPersistentAudioSettingOn = { latestSettings?.enablePersistentAudioPlayback == true },
                onPlayVideo = { channel -> startActivity(StreamsActivity.createPlayIntent(this, channel.url)) },
            ),
            // S0770: per-item menu actions - new-window launches + Remove (unpin) + availability gate.
            menuActions = StreamsPanelMenuActions(
                onOpenStreamsNewWindow = {
                    panelItemActions.launchInNewWindow(Intent(this, StreamsActivity::class.java))
                },
                onOpenChannelNewWindow = { channel ->
                    panelItemActions.launchInNewWindow(StreamsActivity.createPlayIntent(this, channel.url))
                },
                onRemoveChannel = { channel -> panelItemActions.confirmRemoveChannel(channel) },
                onDisableStreams = { panelItemActions.disableStreamsFromPanel() },
                // S0782: hide only the panel (Streams stays on; the entry returns to the programs panel/menu).
                onHideStreamsPanel = { panelItemActions.hideStreamsPanelFromPanel() },
                // S0780: "Configure" opens the Streams settings group.
                onConfigureStreams = { startActivity(SettingsActivity.openStreamsSectionIntent(this)) },
                isNewWindowAvailable = panelItemActions::isNewWindowAvailable,
                // S0783: add/remove the channel from the shared Favorites (feature-gated, label per state).
                onToggleFavorite = { channel -> viewModel.toggleStreamFavorite(channel) },
                isFavoritesEnabled = { latestSettings?.enableFavorites == true },
                isChannelFavorite = { channel -> viewModel.favoriteStreamUrls.value.contains(channel.url) },
            ),
            // S0808: persist the streams-panel collapsed-strip state (mirror of the programs panel).
            settingsRepository = settingsRepository,
            // S0809: collapsed chip lives in the shared collapsed-panels row (activity layout).
            collapsedChip = binding.chipStreamsCollapsed,
        )
        streamsPanelManager.setup()
        // S0810: long-press a command-bar icon reveals its name via a tooltip (reuses contentDescription).
        MainCommandBarTooltipManager.apply(binding)
        setupMainWindowDropdownMenu()
        layoutChrome.restitchControlBarFocusChain()

        // S0293 Phase 08: effective multi-window availability = persistent preference OR runtime
        // capability. S0727: seed from the non-blocking runtime capability only; the persisted
        // allowSeparateWindow preference is folded in off-Main by the settings collector below
        // (collectOnLifecycle(getSettings())), so setupViews does no disk IO on the Main thread.
        val mainAllowSeparateWindow =
            com.sza.fastmediasorter.core.compat.MultiWindowCapabilityDetector.isMultiWindowActiveNow(this)

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
            onExportClick = { resource ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.resource_share_export_title)
                    .setMessage(R.string.resource_share_credentials_warning)
                    .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.exportResourceForShare(resource) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            },
            onShareSftpAccessClick = { resource ->
                sftpShareManager.show(resource) { includePassword, method ->
                    when (method) {
                        MainSftpShareManager.ShareMethod.FILE ->
                            viewModel.shareSftpResourceConfig(resource, includePassword)
                        MainSftpShareManager.ShareMethod.QR ->
                            viewModel.shareSftpResourceConfigAsQr(resource, includePassword)
                    }
                }
            },
            onAddToHomeScreenClick = { resource ->
                pinResourceLaunchShortcut(resource)
            },
            // S0293 Phase 08: visible only when multi-window is effectively available (preference OR runtime)
            isOpenInNewWindowVisible = mainAllowSeparateWindow,
            onOpenInNewWindowClick = { resource -> panelItemActions.openResourceInNewWindow(resource.id) },
            // S0963 (Pillar 2): resource "Open in VR Cinema" entry; visibility mirrors XR availability.
            isOpenInVrCinemaVisible = panelItemActions.isVrCinemaAvailable(),
            onOpenInVrCinemaClick = { resource -> panelItemActions.openResourceInVrCinema(resource) }
        )

        binding.rvResources.adapter = resourceAdapter

        // Configure LayoutManager based on available width / orientation (Tablet + wide-portrait support)
        if (isWideLayout()) {
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

        // S0759: the top-left exit button minimizes (moveTaskToBack) when any background function is
        // live and only fully closes when nothing runs in the background; long-press always fully exits.
        // The manager owns the mode decision; the Activity owns the minimize primitive and the teardown.
        exitButtonManager = MainExitButtonManager(
            exitButton = binding.btnExit,
            onMinimize = { moveTaskToBack(true) },
            onFullExit = { performFullExit() },
        )
        exitButtonManager.setupClickHandlers()

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
                val packageInfo = packageManager.getPackageInfoCompat(packageName)
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
            layoutChrome.restitchControlBarFocusChain()

            // Enable Play button if any resources exist (auto-selects last used or first)
            binding.btnStartPlayer.isEnabled = state.resources.isNotEmpty()

            // Use state.resources.size instead of adapter.itemCount
            // because submitList() updates itemCount asynchronously
            layoutChrome.updateFilterWarning(state)

            // Sync TabLayout selection with ViewModel state
            // Skip FAVORITES tab - it's action-only (opens Browse), not a filter
            if (state.activeResourceTab != ResourceTab.FAVORITES) {
                val tabPosition = getTabIndexForResourceTab(state.activeResourceTab)
                if (binding.tabResourceTypes.selectedTabPosition != tabPosition) {
                    binding.tabResourceTypes.selectTab(binding.tabResourceTypes.getTabAt(tabPosition))
                }
            }
        }

        collectOnLifecycle(viewModel.resourceListUiState) { uiState ->
            binding.progressBar.isVisible = when (uiState) {
                UiState.Loading -> true
                is UiState.Content -> uiState.isRefreshing
                UiState.Empty, is UiState.Error -> false
            }
            binding.rvResources.isVisible = uiState is UiState.Content
            binding.emptyStateView.isVisible = uiState === UiState.Empty
            binding.errorStateView.isVisible = uiState is UiState.Error

            if (uiState is UiState.Error) {
                binding.tvErrorMessage.text = uiState.message
            }
        }

        // Handle navigation progress (connection test during resource open).
        // While the resume-loading flow owns the indicator, defer to it so a stray
        // isNavigating=false emission can't hide it mid-flow (one-frame race). S0708.
        collectOnLifecycle(viewModel.state) { state ->
            if (::resumeHelper.isInitialized && resumeHelper.isResumeLoadingActive) return@collectOnLifecycle
            binding.navigationProgressLayout.isVisible = state.isNavigating
            if (state.isNavigating && state.navigationMessage != null) {
                binding.tvNavigationMessage.text = state.navigationMessage
            }
        }

        val eventHandler = com.sza.fastmediasorter.ui.main.helpers.MainEventHandler(
            activity = this,
            binding = binding,
            viewModel = viewModel,
            settingsRepository = settingsRepository,
            passwordManager = passwordManager,
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
            latestSettings = settings // S0770: keep the freshest snapshot for the panel item menus.
            val calculatorEnabledChanged = isCalculatorEnabled != settings.enableCalculator
            val embeddedGameEnabledChanged = isEmbeddedGameEnabled != settings.embeddedGameEnabled
            val cameraOcrEnabledChanged = isCameraOcrEnabled != settings.cameraOcrTranslationEnabled
            // S0523: the quick-capture menu entries reuse the existing capture toggles - no separate
            // settings. Voice -> mic recording; video/photo -> the inverted capture-enable flags.
            val quickVoiceEnabledChanged = isQuickVoiceEnabled != settings.micRecordingEnabled
            val quickVideoEnabledChanged = isQuickVideoEnabled != !settings.disableVideoCapture
            val quickPhotoEnabledChanged = isQuickPhotoEnabled != !settings.disableCameraCapture
            // S0542: the manual "Download by link" entry reuses the existing link auto-download
            // setting - no separate toggle.
            val linkDownloadEnabledChanged = isLinkDownloadEnabled != settings.linkAutoDownloadEnabled
            // S0755/S0756: streams-enabled and the two panel toggles all change panel visibility/content.
            val streamsEnabledChanged = isStreamsEnabled != settings.enableStreams
            val programsPanelChanged = isProgramsPanelEnabled != settings.showProgramsPanelInMainWindow
            val streamsPanelChanged = isStreamsPanelEnabled != settings.showStreamsPanelInMainWindow
            // S0774: gate on the toggle AND the capability (empty controller set on lite/photos/legacy).
            val screenRecordingNowEnabled =
                settings.screenRecordingEnabled && screenVideoRecordingControllers.isNotEmpty()
            val screenRecordingEnabledChanged = isScreenRecordingEnabled != screenRecordingNowEnabled
            isCalculatorEnabled = settings.enableCalculator
            isEmbeddedGameEnabled = settings.embeddedGameEnabled
            isCameraOcrEnabled = settings.cameraOcrTranslationEnabled
            isQuickVoiceEnabled = settings.micRecordingEnabled
            isQuickVideoEnabled = !settings.disableVideoCapture
            isQuickPhotoEnabled = !settings.disableCameraCapture
            isLinkDownloadEnabled = settings.linkAutoDownloadEnabled
            isStreamsEnabled = settings.enableStreams
            isProgramsPanelEnabled = settings.showProgramsPanelInMainWindow
            isStreamsPanelEnabled = settings.showStreamsPanelInMainWindow
            isScreenRecordingEnabled = screenRecordingNowEnabled
            binding.btnFavorites.visibility = if (settings.enableFavorites) {
                View.VISIBLE
            } else {
                View.GONE
            }
            resourceAdapter.setUseCompactElements(settings.useCompactElements)
            resourceAdapter.setOverflowModeEnabled(settings.resourceOpsInOverflowMenu) // S0160
            // S0727: apply the persisted allowSeparateWindow preference off-Main here (OR runtime
            // capability), replacing the removed runBlocking read in setupViews.
            resourceAdapter.setOpenInNewWindowVisible(
                settings.allowSeparateWindow ||
                    com.sza.fastmediasorter.core.compat.MultiWindowCapabilityDetector
                        .isMultiWindowActiveNow(this@MainActivity)
            )
            // S0963: re-mirror XR availability (VR-3D master toggle) onto the resource VR Cinema entry.
            resourceAdapter.setOpenInVrCinemaVisible(resourceVrCinemaLaunchManager.isAvailable)
            layoutChrome.applyCompactToolbar(settings.useCompactElements)
            layoutChrome.refreshGridSpacing()
            // S0759: the left-edge gesture overlay is a setting, not a service - feed its live value to
            // the exit button so the minimize/close mode (and icon) tracks it without an app restart.
            exitButtonManager.setGestureOverlayEnabled(settings.gestureOverlayEnabled)
            // S0755/S0756: any menu-affecting gate OR a panel/streams toggle change rebuilds the panels
            // (the programs panel mirrors the menu) and refreshes the three-dots button visibility.
            val panelInputsChanged = listOf(
                calculatorEnabledChanged, embeddedGameEnabledChanged, cameraOcrEnabledChanged,
                quickVoiceEnabledChanged, quickVideoEnabledChanged, quickPhotoEnabledChanged,
                linkDownloadEnabledChanged, streamsEnabledChanged, programsPanelChanged, streamsPanelChanged,
                screenRecordingEnabledChanged,
            ).any { it }
            if (panelInputsChanged) {
                refreshPanels()
            }
            layoutChrome.restitchControlBarFocusChain()
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    /** Recalculates grid layout, toolbar labels and tabs after screen rotation. */
    override fun onLayoutConfigurationChanged(newConfig: Configuration) {
        layoutChrome.updateToolbarButtonLabels(newConfig)
        layoutChrome.updateLayoutManagerForScreenSize()

        // Recreate tabs to apply new inline/stacked label configuration
        binding.tabResourceTypes.removeAllTabs()
        tabsManager.createTabs()

        // S0755/S0756: re-apply the orientation/width-driven label rule + programs-panel overflow.
        if (::programsPanelManager.isInitialized) programsPanelManager.refresh()
        if (::streamsPanelManager.isInitialized) streamsPanelManager.onConfigurationChanged()
    }

    private fun showDeleteConfirmation(resource: com.sza.fastmediasorter.domain.model.MediaResource) {
        if (isFinishing || isDestroyed) return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
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
        val action = gamepadInputManager.handleKeyEvent(event, com.sza.fastmediasorter.domain.input.InputSurface.BROWSER)
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
            // S0809: the filter's collapsed representation is now the shared-row chip, not an in-place strip.
            collapsedStrip = binding.chipFilterCollapsed,
            configuration = resources.configuration,
            gate = remoteSourceGate,
            settingsRepository = settingsRepository,
            scope = lifecycleScope,
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

    /** S0759: single full-teardown path reused by every full-exit caller (exit button close branch,
     *  long-press, keyboard exit shortcut). Stops the audio service first to avoid the OS double-startup
     *  bug (see stopAudioPlaybackService), then finishAffinity() + killProcess(). The minimize branch
     *  deliberately does NOT go through here so background services stay alive. */
    private fun performFullExit() {
        stopAudioPlaybackService()
        finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** Stop AudioPlaybackService before exiting the app. Prevents OS from restarting the process due to foreground service being alive. CRITICAL FIX: Without this, finishAffinity() + Process.killProcess() causes the OS to restart the same process within 1 second (double startup bug). */
    private fun stopAudioPlaybackService() {
        try {
            val serviceIntent = Intent(this, AudioPlaybackService::class.java)
            stopService(serviceIntent)
        } catch (e: Exception) {
            Timber.w(e, "MainActivity: Failed to stop AudioPlaybackService before exit")
        }
    }

    private fun pinResourceLaunchShortcut(resource: com.sza.fastmediasorter.domain.model.MediaResource) {
        // Reuse the exact drawable the resource shows in the grid so the pinned icon matches it.
        val icon = ResourceIconComposer.compose(this, resource)
        val message = when (resourceShortcutPinManager.requestPin(resource.id, resource.name, icon)) {
            ResourceShortcutPinManager.PinResult.Requested -> R.string.resource_shortcut_created
            ResourceShortcutPinManager.PinResult.Unsupported -> R.string.resource_shortcut_unsupported
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val ACTION_START_SLIDESHOW = "com.sza.fastmediasorter.ACTION_START_SLIDESHOW"
        const val ACTION_RANDOM_MUSIC = "com.sza.fastmediasorter.ACTION_RANDOM_MUSIC"
        const val ACTION_CAMERA_PHOTOS = "com.sza.fastmediasorter.ACTION_CAMERA_PHOTOS"
        const val ACTION_CAMERA_OCR_TRANSLATE = "com.sza.fastmediasorter.action.CAMERA_OCR_TRANSLATE"
        const val ACTION_OPEN_FAVORITES = "com.sza.fastmediasorter.ACTION_OPEN_FAVORITES"
        const val ACTION_BROWSE_RESOURCE = "com.sza.fastmediasorter.ACTION_BROWSE_RESOURCE"
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
