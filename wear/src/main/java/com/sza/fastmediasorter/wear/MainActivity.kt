package com.sza.fastmediasorter.wear

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BadParcelableException
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.sza.fastmediasorter.wear.core.notification.WearOpenOnWatchNotifier
import com.sza.fastmediasorter.wear.core.util.WearLocaleManager
import com.sza.fastmediasorter.wear.core.util.WearUnitDateTimeFormatter
import com.sza.fastmediasorter.wear.data.onboarding.WearInstallInfoReader
import com.sza.fastmediasorter.wear.data.wear.WatchFileOpenEvents
import com.sza.fastmediasorter.wear.data.wear.WatchStreamOpenEvents
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentFormat
import com.sza.fastmediasorter.wear.domain.model.UnitSystem
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.WearBackground
import com.sza.fastmediasorter.wear.domain.model.WearColorScheme
import com.sza.fastmediasorter.wear.domain.model.WearFdSecMode
import com.sza.fastmediasorter.wear.domain.model.WearFileOpenRequest
import com.sza.fastmediasorter.wear.domain.model.WearFolderAddress
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearNetworkFileOpenRequest
import com.sza.fastmediasorter.wear.domain.model.readWearLaunchTarget
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.BuildWearOnboardingStepsUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ObserveWearGeometryModeUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareVoiceNotePlaybackUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearFilePlaybackUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearNetworkFilePlaybackUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearStreamPlaybackUseCase
import com.sza.fastmediasorter.wear.domain.usecase.RecordLastUsedAppUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ResolveWearBackgroundUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ResolveWearLaunchAddressUseCase
import com.sza.fastmediasorter.wear.ui.apps.AppsScreen
import com.sza.fastmediasorter.wear.ui.apps.bloodpressure.BloodPressureScreen
import com.sza.fastmediasorter.wear.ui.apps.bloodpressure.calibration.BloodPressureCalibrationScreen
import com.sza.fastmediasorter.wear.ui.apps.bloodpressure.history.BloodPressureHistoryScreen
import com.sza.fastmediasorter.wear.ui.apps.bodysensor.BodySensorScreen
import com.sza.fastmediasorter.wear.ui.apps.bodysensor.history.HeartRateHistoryScreen
import com.sza.fastmediasorter.wear.ui.apps.calculator.CalculatorScreen
import com.sza.fastmediasorter.wear.ui.apps.clipboard.ClipboardScreen
import com.sza.fastmediasorter.wear.ui.apps.game.GameRulesScreen
import com.sza.fastmediasorter.wear.ui.apps.game.GameScreen
import com.sza.fastmediasorter.wear.ui.apps.motionmonitor.MotionMonitorScreen
import com.sza.fastmediasorter.wear.ui.apps.motionmonitor.history.MotionHistoryScreen
import com.sza.fastmediasorter.wear.ui.apps.netmonitor.NetworkMonitorDetailScreen
import com.sza.fastmediasorter.wear.ui.apps.netmonitor.NetworkMonitorScreen
import com.sza.fastmediasorter.wear.ui.apps.sos.SosScreen
import com.sza.fastmediasorter.wear.ui.apps.stopwatch.WearStopwatchScreen
import com.sza.fastmediasorter.wear.ui.apps.systeminfo.SystemInfoScreen
import com.sza.fastmediasorter.wear.ui.apps.tourist.TouristScreen
import com.sza.fastmediasorter.wear.ui.apps.waterflashlight.WaterFlashlightScreen
import com.sza.fastmediasorter.wear.ui.brand.BrandFrameScreen
import com.sza.fastmediasorter.wear.ui.broadcast.WearBroadcastQrScreen
import com.sza.fastmediasorter.wear.ui.broadcast.WearBroadcastScreen
import com.sza.fastmediasorter.wear.ui.browse.BrowseScreen
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearGeometryMode
import com.sza.fastmediasorter.wear.ui.common.LocalWearListPositions
import com.sza.fastmediasorter.wear.ui.common.LocalWearRotaryFocusStack
import com.sza.fastmediasorter.wear.ui.common.LocalWearSectionExpansion
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import com.sza.fastmediasorter.wear.ui.common.LocalWearWallpaperState
import com.sza.fastmediasorter.wear.ui.common.WearBackAffordance
import com.sza.fastmediasorter.wear.ui.common.WearBackAffordanceRole
import com.sza.fastmediasorter.wear.ui.common.WearDimOverlay
import com.sza.fastmediasorter.wear.ui.common.WearListPositionStore
import com.sza.fastmediasorter.wear.ui.common.WearRotaryFocusStack
import com.sza.fastmediasorter.wear.ui.common.WearScreenOffAffordance
import com.sza.fastmediasorter.wear.ui.common.WearSectionExpansionStore
import com.sza.fastmediasorter.wear.ui.common.WearWallpaperState
import com.sza.fastmediasorter.wear.ui.common.playerRouteFor
import com.sza.fastmediasorter.wear.ui.common.testlaunch.WearTestLaunchOverride
import com.sza.fastmediasorter.wear.ui.common.testlaunch.WearTestLaunchOverrideReader
import com.sza.fastmediasorter.wear.ui.common.testlaunch.rememberWearTestScreenMetrics
import com.sza.fastmediasorter.wear.ui.common.wearBackAffordanceInset
import com.sza.fastmediasorter.wear.ui.favourites.FavouritesScreen
import com.sza.fastmediasorter.wear.ui.fdsec.FdSecCredentialScreen
import com.sza.fastmediasorter.wear.ui.folder.WearFolderWalkScreen
import com.sza.fastmediasorter.wear.ui.home.HomeScreen
import com.sza.fastmediasorter.wear.ui.home.LocalHomeScreen
import com.sza.fastmediasorter.wear.ui.home.PhoneHomeScreen
import com.sza.fastmediasorter.wear.ui.navigation.WearLaunchRoutes
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.network.AddNetworkSourceScreen
import com.sza.fastmediasorter.wear.ui.network.NetworkSourceMediaTypeScreen
import com.sza.fastmediasorter.wear.ui.network.NetworkSourcesScreen
import com.sza.fastmediasorter.wear.ui.network.SyncResultScreen
import com.sza.fastmediasorter.wear.ui.network.SyncTransferScreen
import com.sza.fastmediasorter.wear.ui.network.viewmodel.NetworkSourcesViewModel
import com.sza.fastmediasorter.wear.ui.onboarding.WearOnboardingEntry
import com.sza.fastmediasorter.wear.ui.onboarding.WearOnboardingScreen
import com.sza.fastmediasorter.wear.ui.permission.PermissionsScreen
import com.sza.fastmediasorter.wear.ui.phone.PhoneResourceScreen
import com.sza.fastmediasorter.wear.ui.phonecamera.PhoneCameraScreen
import com.sza.fastmediasorter.wear.ui.player.audio.AudioPlayerScreen
import com.sza.fastmediasorter.wear.ui.player.document.DocumentViewerScreen
import com.sza.fastmediasorter.wear.ui.player.image.ImageViewerScreen
import com.sza.fastmediasorter.wear.ui.player.unsupported.UnsupportedFileScreen
import com.sza.fastmediasorter.wear.ui.player.video.VideoPlayerScreen
import com.sza.fastmediasorter.wear.ui.settings.AboutSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.MediaTypesSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.OtherSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.PermissionsSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.ScreenSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.SettingsRoutes
import com.sza.fastmediasorter.wear.ui.settings.SettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.SlideshowSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.TileTargetsSettingsScreen
import com.sza.fastmediasorter.wear.ui.streams.StreamsScreen
import com.sza.fastmediasorter.wear.ui.testing.WearTestTags
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import com.sza.fastmediasorter.wear.ui.tile.TileTargetPickerScreen
import com.sza.fastmediasorter.wear.ui.voicenote.VoiceNoteListScreen
import com.sza.fastmediasorter.wear.ui.voicenote.VoiceRecorderScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S2000: the use cases the navigation host resolves with, handed down as one thing.
 *
 * Same reason as [WearLaunchEntry] below: all three are needed only by the host, and passing them
 * one by one repeats them at every level between the Activity and it.
 */
data class WearHostUseCases(
    val prepareStreamPlayback: PrepareWearStreamPlaybackUseCase,
    val prepareFilePlayback: PrepareWearFilePlaybackUseCase,
    val resolveBackground: ResolveWearBackgroundUseCase,
    val prepareVoiceNotePlayback: PrepareVoiceNotePlaybackUseCase,
    val prepareNetworkFilePlayback: PrepareWearNetworkFilePlaybackUseCase,
    // S2773: the screen geometry in force. Travels here rather than as a parameter of its own for the
    // same reason as the rest: the navigation host is the only thing that needs it.
    val observeGeometryMode: ObserveWearGeometryModeUseCase,
    // S2995: restricted capabilities for build flavor routing
    val capabilities: WearRestrictedCapabilities,
    // S3116: records the mini-program the host navigated to, for the home row that offers it again.
    val recordLastUsedApp: RecordLastUsedAppUseCase,
)

/**
 * S1955: the launch-intent wiring, handed down as one thing.
 *
 * The three are useless apart - a resolver with nothing pending resolves nothing, and clearing without
 * both cannot be ordered after the navigation - so they travel together rather than as three parameters
 * repeated at every level between the Activity and the navigation host.
 */
data class WearLaunchEntry(
    val resolveAddress: ResolveWearLaunchAddressUseCase,
    val pendingTarget: StateFlow<WearLaunchTarget?>,
    /** Takes the target it handled, so a newer one that arrived mid-resolution is not cleared unhandled. */
    val onHandled: (WearLaunchTarget) -> Unit,
    /** S3201: the test parameters of this launch; null on every launch that carried none. */
    val testOverride: StateFlow<WearTestLaunchOverride?>,
)

/** S2201: the sentinel the player view models already treat as "no file was named". */
private const val UNRESOLVED_FILE_ID = -1L

/** S2161: the recorder writes one container only, so the route builder is told it rather than guessing. */
private const val VOICE_NOTE_MIME_TYPE = "audio/mp4"

/** The three player routes: each covers the whole window, so the shared background is not drawn behind them. */
private val PLAYER_ROUTES = setOf(
    WearRoutes.AUDIO_PLAYER_PATTERN,
    WearRoutes.VIDEO_PLAYER_PATTERN,
    WearRoutes.IMAGE_VIEWER_PATTERN
)

private val SETTINGS_ROUTES = setOf(
    WearRoutes.SETTINGS,
    SettingsRoutes.MEDIA_TYPES,
    SettingsRoutes.SLIDESHOW,
    SettingsRoutes.SCREEN,
    SettingsRoutes.OTHER,
    SettingsRoutes.TILE_TARGETS,
    SettingsRoutes.PERMISSIONS,
    SettingsRoutes.ABOUT
)

// S3155: ActivityLogicViolation fires on all eight @Inject use-case fields below. The detector
// enforces CLAUDE.md Rule 3 against a View-based Activity, where the cure is a Manager the Activity
// delegates to. This module is Compose (Rule 32) and this class is its composition root: there is no
// view hierarchy to own logic, and each field is handed down to the screen that needs it for a
// reason recorded on the field itself by the ticket that put it there - S1781, S1944, S1884, S2161,
// S1955, S2000. Rule 8 makes those comments requirements, so the suppression records the existing
// decision instead of silently overturning eight of them. Narrow by construction: MainActivity is
// the only Activity in the module, so this does not disarm the rule for any future one.
@SuppressLint("ActivityLogicViolation")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // S1781: the keep-awake setting is read once, here, and handed down - the flag lives on this
    // window, so the one composable that sets it has to sit above every screen that could.
    @Inject lateinit var preferencesRepository: WearPreferencesRepository

    // S1944: the phone can ask this watch to open a channel. The request arrives at the Data Layer
    // listener, which holds no navigation, so the host does the opening - and only while it is on
    // screen, because the platform forbids raising it from the background (strategic ADR-1).
    @Inject lateinit var prepareStreamPlayback: PrepareWearStreamPlaybackUseCase

    // S1884: the same arrangement for a file the phone delivered rather than a channel it named.
    @Inject lateinit var prepareFilePlayback: PrepareWearFilePlaybackUseCase

    @Inject lateinit var prepareNetworkFilePlayback: PrepareWearNetworkFilePlaybackUseCase

    // S2161: a voice note is addressed either by its published MediaStore row or by its private file,
    // and only this use case knows which - the recorder and note screens hand it the note and navigate.
    @Inject lateinit var prepareVoiceNotePlayback: PrepareVoiceNotePlaybackUseCase

    // S1955: a tile names its target in the launch intent, and resolving it reads the stores.
    @Inject lateinit var resolveLaunchAddress: ResolveWearLaunchAddressUseCase

    // S2000: injected here and handed down for the same reason the two playback use cases are -
    // the navigation host is the one place that sits above every screen the background shows behind.
    @Inject lateinit var resolveBackground: ResolveWearBackgroundUseCase

    @Inject lateinit var observeGeometryMode: ObserveWearGeometryModeUseCase

    @Inject lateinit var capabilities: WearRestrictedCapabilities

    // S3186: the first-run walk reads which permissions this edition declares and whether this is the
    // first install at all - an update over a used install goes straight to the app.
    @Inject lateinit var installInfo: WearInstallInfoReader

    @Inject lateinit var buildOnboardingSteps: BuildWearOnboardingStepsUseCase

    // S3116: the host is where a mini-program is seen to open, whichever entrance was used, so it is
    // where the home row's "last program" is recorded (strategic ADR-1).
    @Inject lateinit var recordLastUsedApp: RecordLastUsedAppUseCase

    // S1961: the pending-open notification is this app's own, so it is this app that puts it away
    // once the user is here and no longer needs it.
    @Inject lateinit var openOnWatchNotifier: WearOpenOnWatchNotifier

    // S2543: list positions outlive the screens that produced them, so the store is held by the process
    // and handed to composition here - a screen popped off the back stack takes its own state with it.
    @Inject lateinit var listPositions: WearListPositionStore

    // S2806: which groups of a grouped report were open survives the screen for the same reason its
    // scroll anchor does - the screen is destroyed by navigation, the process is not.
    @Inject lateinit var sectionExpansion: WearSectionExpansionStore

    // S2795: handed to composition here for the same reason the two stores above are - every screen
    // that shows a time needs it, and the pattern cache is worth nothing if each screen builds its own.
    @Inject lateinit var dateTimeFormatter: WearUnitDateTimeFormatter

    // S3201: one-launch test parameters. The release binding always answers null.
    @Inject lateinit var testLaunchOverrideReader: WearTestLaunchOverrideReader

    /**
     * S1955: what this launch asked to open, until the navigation host has opened it.
     *
     * Held here rather than read inside the host because the host is not composed yet when the intent
     * arrives - the brand frame and the permission screen both stand in front of it on a cold start, which
     * is exactly the start a tile tap produces.
     */
    private val pendingLaunchTarget = MutableStateFlow<WearLaunchTarget?>(null)

    /**
     * S3201: what the current launch asked to draw instead of the stored settings. Held in memory only,
     * so a process death or a launch without parameters is the owner's own view again.
     */
    private val testLaunchOverride = MutableStateFlow<WearTestLaunchOverride?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Log app info and configuration
        logAppInfo()

        Timber.d("MainActivity created")

        // Only on a genuine start: a recreation re-delivers the same intent, and the module's
        // configChanges does not cover a locale, font-scale or density change, so re-reading it here
        // would replay the jump the user already took - and again on every restore from Recents.
        if (savedInstanceState == null) {
            pendingLaunchTarget.value = launchTargetFrom(intent)
        }
        // Unlike the target, re-reading on a recreation is right: it is the same launch, and a rotation
        // must not drop the geometry the test is looking at.
        testLaunchOverride.value = testLaunchOverrideReader.read(intent)

        setContent {
            // S2763: one rotary stack for the whole watch UI. It has to span the screen and everything
            // drawn over it - dialogs, the action cloud - or each side would believe it owns the crown.
            val rotaryFocus = remember { WearRotaryFocusStack() }
            // S2795: collected above every screen, because the measurement system decides the clock
            // format on surfaces that share nothing else - the brand frame, a history list, the
            // flashlight's clock. A push from the phone lands here and recomposes all of them.
            val units by preferencesRepository.unitSystem
                .collectAsStateWithLifecycle(initialValue = UnitSystem.DEFAULT)
            // S3201: above everything, so the brand frame and the permission screen scale with the rest.
            val testOverride by testLaunchOverride.collectAsStateWithLifecycle()
            val testScreen = rememberWearTestScreenMetrics(testOverride?.screenDp)
            CompositionLocalProvider(
                LocalDensity provides testScreen.density,
                LocalConfiguration provides testScreen.configuration,
                LocalWearUnitSystem provides units,
                LocalWearListPositions provides listPositions,
                LocalWearSectionExpansion provides sectionExpansion,
                LocalWearRotaryFocusStack provides rotaryFocus,
                LocalWearDateTimeFormatter provides dateTimeFormatter
            ) {
                AskNotificationPermissionEffect(
                    alreadyAsked = preferencesRepository.notificationPermissionAsked,
                    onAsked = {
                        lifecycleScope.launch { preferencesRepository.setNotificationPermissionAsked(true) }
                    }
                )
                WearApp(
                    onboarding = WearOnboardingEntry(
                        needed = preferencesRepository.onboardingCompleted.map { completed ->
                            !completed && installInfo.isFreshInstall
                        },
                        steps = { buildOnboardingSteps(installInfo.declaredPermissions, Build.VERSION.SDK_INT) },
                        onFinished = {
                            lifecycleScope.launch { preferencesRepository.setOnboardingCompleted(true) }
                        },
                        // S3178: the store artifact declares no media permission, so the request could never
                        // be granted and the watch would stop on this prompt forever instead of reaching Home.
                        hasMediaAccess = { !capabilities.offersMediaAccess || hasMediaPermissions() },
                        offersMediaAccess = capabilities.offersMediaAccess
                    ),
                    keepScreenAwakeOutsidePlayers = preferencesRepository.keepScreenAwakeOutsidePlayers,
                    isAutoRotationEnabled = preferencesRepository.isAutoRotationEnabled,
                    appLanguage = preferencesRepository.appLanguage,
                    colorScheme = preferencesRepository.colorScheme,
                    hostUseCases = WearHostUseCases(
                        prepareStreamPlayback = prepareStreamPlayback,
                        prepareFilePlayback = prepareFilePlayback,
                        resolveBackground = resolveBackground,
                        prepareVoiceNotePlayback = prepareVoiceNotePlayback,
                        prepareNetworkFilePlayback = prepareNetworkFilePlayback,
                        observeGeometryMode = observeGeometryMode,
                        capabilities = capabilities,
                        recordLastUsedApp = recordLastUsedApp,
                    ),
                    launchEntry = WearLaunchEntry(
                        resolveAddress = resolveLaunchAddress,
                        pendingTarget = pendingLaunchTarget,
                        onHandled = { handled -> pendingLaunchTarget.compareAndSet(handled, null) },
                        testOverride = testLaunchOverride
                    )
                )
            }
        }
    }

    /**
     * S1961: the pending-open notification is spent the moment the app is in front.
     *
     * onStart rather than onCreate, because the app reaching the foreground by any route - the tap
     * itself, the launcher icon, a return from Recents - is what makes the command stale. Holding an
     * expired command in the watch's shade is worse than never having shown it (strategic §3.2).
     */
    override fun onStart() {
        super.onStart()

        openOnWatchNotifier.cancel()
    }

    /**
     * S1955: a tile tapped while this Activity is already in front is delivered here, not to [onCreate].
     *
     * `setIntent` keeps `getIntent()` agreeing with what was just handled, so anything later reading the
     * Activity's intent sees the target the user actually tapped rather than the one that started it.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Only overwrite with a real target: tapping the launcher icon while a tile's target is still
        // waiting behind the permission screen delivers a bare MAIN intent, and that must not erase it.
        launchTargetFrom(intent)?.let { pendingLaunchTarget.value = it }
        // Always overwritten: a launch without parameters is the owner's view again, which is the
        // "only this launch" promise the test parameters make.
        testLaunchOverride.value = testLaunchOverrideReader.read(intent)
    }

    /**
     * Reads the launch target, or nothing when the extras cannot be read.
     *
     * Reading one extra unparcels the whole bundle, and this Activity is exported, so a malformed bundle
     * from any app on the watch would otherwise take the process down before the first frame. Treating it
     * as "no target was named" is the same outcome as an ordinary launch, which is the safe answer.
     */
    private fun launchTargetFrom(intent: Intent): WearLaunchTarget? = try {
        readWearLaunchTarget(intent)
    } catch (e: BadParcelableException) {
        Timber.w(e, "Unreadable launch intent extras - treating as a plain launch")
        null
    }

    private fun logAppInfo() {
        try {
            // S0467: raw-int getPackageInfo overload deprecated in API 33; branch to the type-safe one.
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
            val versionName = packageInfo.versionName ?: "unknown"
            // longVersionCode is the non-deprecated reader and exists from API 28, the module minimum.
            val versionCode = packageInfo.longVersionCode

            Timber.d("========== FastMediaSorter Wear OS ==========")
            Timber.d("Version: $versionName")
            Timber.d("Version Code: $versionCode")
            Timber.d("Package: $packageName")
            Timber.d("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            Timber.d("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            Timber.d("Build Type: ${BuildConfig.BUILD_TYPE}")
            Timber.d("Debug: ${BuildConfig.DEBUG}")
            Timber.d("==========================================")
        } catch (e: Exception) {
            Timber.e(e, "Error logging app info")
        }
    }

    private fun hasMediaPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}

@Composable
fun WearApp(
    onboarding: WearOnboardingEntry,
    keepScreenAwakeOutsidePlayers: Flow<Boolean>,
    isAutoRotationEnabled: Flow<Boolean>,
    appLanguage: Flow<String?>,
    colorScheme: Flow<WearColorScheme>,
    // S1944: passed down rather than resolved in the composable, so the one instance the Activity
    // injects is the one both entrances to a player use.
    hostUseCases: WearHostUseCases,
    // S1955: read-only here and cleared through the callback, so the one writer stays the Activity.
    launchEntry: WearLaunchEntry
) {
    // S2522: the default here is shown only until DataStore answers, and nothing themed is drawn in
    // that window - WearApp opens on the brand frame, which outlives the read. So no synchronous
    // preference mirror is needed, unlike the phone, whose night mode must be set before an Activity
    // inflates. If the brand frame is ever removed, a light-scheme owner would see one dark frame at
    // cold start, and the fix at that point is a synchronous mirror, not a different initial value.
    val scheme by colorScheme.collectAsStateWithLifecycle(initialValue = WearColorScheme.DEFAULT)
    WearAppTheme(scheme = scheme) {
        AutoLocaleEffect(appLanguage = appLanguage)
        AutoRotationEffect(isAutoRotationEnabled = isAutoRotationEnabled)
        val keepAwake by keepScreenAwakeOutsidePlayers.collectAsStateWithLifecycle(initialValue = false)
        KeepScreenOnEffect(enabled = keepAwake)
        var hasPermissions by remember { mutableStateOf(onboarding.hasMediaAccess()) }
        // S1981: scoped to this composable, not `rememberSaveable` or persistent storage - it
        // resets only when `WearApp` itself is recreated (a cold start), never on backgrounding/
        // foregrounding or in-app navigation back to Home (strategic §6 item 4).
        var showBrandFrame by remember { mutableStateOf(true) }
        // S3186: null until the store answers - neither the walk nor the app is drawn on a guess.
        val onboardingNeeded by onboarding.needed.collectAsStateWithLifecycle<Boolean?>(initialValue = null)
        // Local latch: the stored flag is written asynchronously, and the walk must not reappear for
        // the frames between the last tap and the store's next emission.
        var onboardingFinished by remember { mutableStateOf(false) }
        // S3362: what the walk would actually contain. An edition that asks for no permission shows
        // the welcome page alone, and that page's copy promises media this edition does not reach -
        // so there is nothing left to walk and the walk is recorded done without being drawn.
        val steps = remember { onboarding.steps() }
        val walkHasContent = steps.isNotEmpty() || onboarding.offersMediaAccess
        if (onboardingNeeded == true && !walkHasContent) {
            LaunchedEffect(Unit) { onboarding.onFinished() }
        }

        if (showBrandFrame) {
            BrandFrameScreen(onTimeout = { showBrandFrame = false })
        } else if (onboardingNeeded == null) {
            Unit
        } else if (onboardingNeeded == true && walkHasContent && !onboardingFinished) {
            WearOnboardingScreen(
                steps = steps,
                onFinished = {
                    onboardingFinished = true
                    onboarding.onFinished()
                    // The walk may just have granted media access; the gate below was decided before it.
                    hasPermissions = onboarding.hasMediaAccess()
                }
            )
        } else if (!hasPermissions) {
            // Show permissions screen first
            PermissionsScreen(
                onPermissionsGranted = {
                    Timber.d("Permissions granted, navigating to main app")
                    hasPermissions = true
                }
            )
        } else {
            // Main app navigation
            MainNavigation(
                hostUseCases = hostUseCases,
                launchEntry = launchEntry,
            )
        }
    }
}

@Composable
private fun AutoLocaleEffect(appLanguage: Flow<String?>) {
    val context = LocalContext.current
    val currentLanguage by appLanguage.collectAsStateWithLifecycle(initialValue = null)
    DisposableEffect(currentLanguage) {
        currentLanguage?.let { lang ->
            WearLocaleManager.applyLocale(context, lang)
        }
        onDispose { }
    }
}

@Composable
private fun AutoRotationEffect(isAutoRotationEnabled: Flow<Boolean>) {
    val context = LocalContext.current
    val autoRotate by isAutoRotationEnabled.collectAsStateWithLifecycle(initialValue = false)
    DisposableEffect(autoRotate) {
        val activity = context as? Activity
        val hasAccelerometer = context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
        if (hasAccelerometer && activity != null) {
            activity.requestedOrientation = if (autoRotate) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LOCKED
            }
        }
        onDispose {}
    }
}

/**
 * S1961: asks for POST_NOTIFICATIONS once, at the one moment the question makes sense.
 *
 * The trigger is a phone command handled while the app was open - the user pressed a button on their
 * phone a second ago, so they can tell what is being asked and why. That is also the only moment an
 * Activity exists to show the prompt at all: the command that arrives with the app closed is handled
 * by a service, which cannot ask for anything (strategic §3.3).
 *
 * The flag is set whether or not the permission was granted, because a refusal returns exactly the
 * behaviour the watch had before this ticket. That is a valid answer, not a failure to retry.
 *
 * Deliberately wired to the stream path alone: it is the only one that can currently produce a
 * notification, since a delivered file has no [WearLaunchTarget] to name it yet. S1884 adds its own
 * trigger here when it mints one - asking for a permission the app has no way to use would be a
 * prompt with nothing behind it.
 */
@Composable
private fun AskNotificationPermissionEffect(
    alreadyAsked: Flow<Boolean>,
    onAsked: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // true until the store answers: a prompt raised on a guess is worse than one raised a beat late.
    val asked by alreadyAsked.collectAsStateWithLifecycle(initialValue = true)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Timber.i("POST_NOTIFICATIONS answered on the watch: granted=%b", granted)
        onAsked()
    }
    LaunchedEffect(asked) {
        if (asked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            WatchStreamOpenEvents.openedFlow.collect {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                // Already granted still records the ask: there is nothing left to ask about, and
                // leaving the flag clear would re-arm this for the next command.
                if (granted) onAsked() else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainNavigation(
    hostUseCases: WearHostUseCases,
    launchEntry: WearLaunchEntry,
) {
    val navController = rememberSwipeDismissableNavController()

    // S3178: two effects that navigate on something the PHONE sent, not on something the user tapped
    // here. Both are collected only where the capability behind them exists - the store artifact has
    // no Data Layer listener to receive either, and an effect that stayed collected would be a second
    // address into a graph whose destinations are no longer registered.
    if (hostUseCases.capabilities.offersRemoteSources) {
        OpenStreamOnWatchEffect(
            navController = navController,
            prepareStreamPlayback = hostUseCases.prepareStreamPlayback
        )
    }

    if (hostUseCases.capabilities.offersContentTransfer) {
        OpenFileOnWatchEffect(
            navController = navController,
            prepareFilePlayback = hostUseCases.prepareFilePlayback
        )
    }

    OpenLaunchTargetEffect(navController = navController, launchEntry = launchEntry)

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    RecordLastUsedAppEffect(currentRoute = currentRoute, recordLastUsedApp = hostUseCases.recordLastUsedApp)

    // S2097 - When on WearRoutes.HOME (root destination), intercept Back press to call moveTaskToBack(true)
    // instead of finishing the Activity and overshooting into developer options or settings.
    BackHandler(enabled = currentRoute == WearRoutes.HOME) {
        (navController.context as? android.app.Activity)?.moveTaskToBack(true)
    }

    // S2000: the resolved background, and whether it is allowed to animate. The branded animation
    // costs roughly one and a half cores, a budget accepted for one player screen rather than for a
    // permanent backdrop, so it runs only while this window is actually in front of the user - and
    // never under a player, which covers the screen and, on audio, draws this same animation itself.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    val background by hostUseCases.resolveBackground().collectAsStateWithLifecycle(
        initialValue = WearBackground.BrandedAnimation
    )

    val showWallpaper = showsWallpaper(currentRoute)
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && currentRoute !in PLAYER_ROUTES

    // S2773: the geometry in force, published beside the wallpaper state because the shape helpers
    // every screen already calls read it from here. The initial value is the reviewed view, so the one
    // frame drawn before DataStore answers is never the shape Play rejected.
    val storedGeometryMode by hostUseCases.observeGeometryMode().collectAsStateWithLifecycle(
        initialValue = WearGeometryMode.STORE
    )
    // S3201: a test launch draws the other view without writing the owner's choice.
    val testOverride by launchEntry.testOverride.collectAsStateWithLifecycle()
    val geometryMode = testOverride?.geometryMode ?: storedGeometryMode

    // S3098: the screen-off command reaches about thirty screens, so it belongs to none of them. The
    // three screens that dimmed before this ticket keep their own state and are not in the route set
    // below, so the two owners never raise a sheet over each other.
    var dimmed by rememberSaveable { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalWearWallpaperState provides WearWallpaperState(
            background = background,
            showsWallpaper = showWallpaper,
            isResumed = isResumed
        ),
        LocalWearGeometryMode provides geometryMode
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = WearRoutes.HOME,
                // S2548: declared once for the whole graph - a testTag anywhere below reaches the
                // UiAutomator tree as a resource-id only through this opt-in.
                modifier = Modifier
                    .testTag(WearTestTags.WEAR_NAV_ROOT)
                    .semantics { testTagsAsResourceId = true },
            ) {
                composable(WearRoutes.HOME) {
                    HomeScreen(navController = navController)
                }

                contentRoutes(navController = navController, hostUseCases = hostUseCases)

                miniAppRoutes(
                    navController = navController,
                    prepareVoiceNotePlayback = hostUseCases.prepareVoiceNotePlayback,
                    capabilities = hostUseCases.capabilities,
                )

                settingsRoutes(
                    navController = navController,
                    capabilities = hostUseCases.capabilities
                )
            }

            // S2472: the universal back affordance, drawn above the host.
            WearNavBackAffordanceHost(currentRoute = currentRoute, navController = navController)

            // S3098: its mirror at the opposite rim, on exactly the same routes.
            WearNavScreenOffHost(
                currentRoute = currentRoute,
                dimmed = dimmed,
                onDim = { dimmed = true }
            )

            // Drawn last so the sheet covers both rim controls; a control left glowing over a dark
            // screen is the one thing this mode exists to remove.
            if (dimmed) {
                WearDimOverlay(onExit = { dimmed = false })
            }
        }
    }
}

@Composable
private fun BoxScope.WearNavBackAffordanceHost(
    currentRoute: String?,
    navController: NavHostController
) {
    if (showsNavBackAffordance(currentRoute)) {
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        WearBackAffordance(
            role = WearBackAffordanceRole.Back,
            onClick = {
                if (backDispatcher != null) {
                    backDispatcher.onBackPressed()
                } else {
                    navController.popBackStack()
                }
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = wearBackAffordanceInset())
        )
    }
}

/**
 * S3098: the screen-off command at the right rim, opposite the shared back arrow.
 *
 * It hides while the sheet is up because the sheet covers it anyway, and a command that cannot be seen
 * must not stay pressable underneath.
 */
@Composable
private fun BoxScope.WearNavScreenOffHost(
    currentRoute: String?,
    dimmed: Boolean,
    onDim: () -> Unit
) {
    if (!showsNavBackAffordance(currentRoute) || dimmed) {
        return
    }
    WearScreenOffAffordance(
        onClick = {
            onDim()
        },
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = wearBackAffordanceInset())
    )
}

/**
 * S2472: which routes draw the shared back arrow. The players get their back command inside their
 * own control cluster (it must hide with those controls), home gets the close/minimize role, and
 * the game board and calculator are surfaces whose every pixel is the interaction itself - an edge
 * control there steals the gesture the screen exists for.
 */
private fun showsNavBackAffordance(route: String?): Boolean =
    route != null &&
        route != WearRoutes.HOME &&
        route !in PLAYER_ROUTES &&
        route != WearRoutes.GAME &&
        route != WearRoutes.CALCULATOR &&
        // S2825: the same reason as the calculator - the stopwatch's regions reach the screen edge,
        // and an arrow drawn over one of them takes a tap meant for a participant.
        route != WearRoutes.STOPWATCH &&
        // S2516: the back arrow is a touch target, and this screen exists to have none - drawing it
        // would hand a wet wrist the exit the program is built to withhold.
        route != WearRoutes.WATER_FLASHLIGHT &&
        // S3216: the same reason, and for the same half of the program - while the signal runs this
        // screen has no touch target at all, so an arrow drawn over it would be the one exception.
        route != WearRoutes.SOS

private fun showsWallpaper(route: String?): Boolean =
    route != null && route !in SETTINGS_ROUTES && route !in PLAYER_ROUTES

/**
 * S3178: the four route groups that reach user content, registered only where the artifact can.
 *
 * A route that is not registered is not reachable - not by a tap, not by a tile, not by an incoming
 * intent - so this is the navigation half of the store boundary and its manifest half is
 * `wear/src/standard/AndroidManifest.xml`. Neither is sufficient alone: the manifest stops a
 * component being started, this stops an address being resolved, and a capability returns to the
 * store variant only when both are edited together on its own ticket.
 *
 * Grouped into one function rather than four gated call sites in [MainNavigation] because that host
 * already sat at detekt's length ceiling - the same reason [settingsRoutes] and [miniAppRoutes] were
 * lifted out of it by S1944.
 */
private fun NavGraphBuilder.contentRoutes(
    navController: NavHostController,
    hostUseCases: WearHostUseCases
) {
    if (hostUseCases.capabilities.offersMediaAccess) {
        browseRoutes(navController = navController)

        localFolderRoutes(
            navController,
            hostUseCases.prepareFilePlayback,
            hostUseCases.prepareNetworkFilePlayback
        )

        playerRoutes(navController = navController)
    }

    if (hostUseCases.capabilities.offersRemoteSources) {
        tileRoutes(navController = navController)

        syncRoutes(navController = navController)
    }
}

private fun NavGraphBuilder.syncRoutes(navController: NavHostController) {
    composable(WearRoutes.ADD_NETWORK_SOURCE) {
        AddNetworkSourceScreen(navController = navController)
    }
    composable(WearRoutes.ADD_SMB_ALIAS) {
        AddNetworkSourceScreen(navController = navController)
    }
    composable(WearRoutes.SYNC_TRANSFER) { backStackEntry ->
        val parentEntry = remember(backStackEntry) {
            try {
                navController.getBackStackEntry(WearRoutes.NETWORK_SOURCES)
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "NETWORK_SOURCES route not found on backstack for SYNC_TRANSFER")
                null
            }
        }
        val viewModel: NetworkSourcesViewModel = if (parentEntry != null) {
            hiltViewModel(parentEntry)
        } else {
            hiltViewModel()
        }
        val syncState by viewModel.syncState.collectAsState()
        SyncTransferScreen(
            navController = navController,
            syncState = syncState,
            onDispose = { viewModel.resetSyncState() }
        )
    }
    composable(
        route = WearRoutes.SYNC_RESULT_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_ADDED) { type = NavType.IntType },
            navArgument(WearRoutes.ARG_UPDATED) { type = NavType.IntType }
        )
    ) { backStackEntry ->
        val added = backStackEntry.arguments?.getInt(WearRoutes.ARG_ADDED) ?: 0
        val updated = backStackEntry.arguments?.getInt(WearRoutes.ARG_UPDATED) ?: 0
        SyncResultScreen(navController = navController, added = added, updated = updated)
    }
}

/**
 * S2161: the three playback destinations, lifted out of [MainNavigation] for the same reason S1944
 * lifted the settings block - the host sat at detekt's length ceiling and this ticket adds a line to
 * it. All three take the same single file-id argument; S2472 additionally hands each player the
 * back action, so the controller travels in here with them.
 *
 * S2532: the document reader joins them, on the same argument and the same back action. It is
 * deliberately absent from [PLAYER_ROUTES] all the same - that set names the screens that cover the
 * whole window, and the reader is a scrolling list like every other one, so it keeps the shared
 * wallpaper and the shared back affordance.
 */
private fun NavGraphBuilder.playerRoutes(navController: NavHostController) {
    composable(
        route = WearRoutes.AUDIO_PLAYER_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_FILE_ID) { type = NavType.LongType }
        )
    ) {
        AudioPlayerScreen(onBack = {
            navController.popBackStack()
        })
    }

    composable(
        route = WearRoutes.VIDEO_PLAYER_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_FILE_ID) { type = NavType.LongType }
        )
    ) {
        VideoPlayerScreen(onBack = {
            navController.popBackStack()
        })
    }

    composable(
        route = WearRoutes.IMAGE_VIEWER_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_FILE_ID) { type = NavType.LongType }
        )
    ) {
        ImageViewerScreen(onBack = {
            navController.popBackStack()
        })
    }

    // S3383: the credential screen for a FileDO container. Not registered among PLAYER_ROUTES - it
    // renders no content and must keep the list that led here underneath it, because two of its
    // three modes come straight back to that list.
    composable(
        route = WearRoutes.FDSEC_CREDENTIAL_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_FILE_ID) { type = NavType.LongType },
            navArgument(WearRoutes.ARG_FDSEC_MODE) { type = NavType.StringType }
        )
    ) {
        FdSecCredentialScreen(
            onFinished = { navController.popBackStack() },
            onOpen = { route ->
                // The credential screen leaves the stack with the viewer: a back press from the
                // recovered file belongs to the list it was opened from, never to a second prompt.
                navController.navigate(route) {
                    popUpTo(WearRoutes.FDSEC_CREDENTIAL_PATTERN) { inclusive = true }
                }
            }
        )
    }

    composable(
        route = WearRoutes.DOCUMENT_VIEWER_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_FILE_ID) { type = NavType.LongType }
        )
    ) {
        DocumentViewerScreen(onBack = {
            navController.popBackStack()
        })
    }
}

/**
 * S1944: the settings half of the graph, lifted out of [MainNavigation].
 *
 * Not a rearrangement for taste: the host function was at detekt's length ceiling, so the one line
 * this ticket adds to it had to be paid for. These destinations are the largest block that shares a
 * single subject and needs nothing from the host but the controller.
 *
 * S2008 took system information out of the block: it configured nothing, so it moved to
 * [miniAppRoutes] with the rest of the watch's programs.
 */
private fun NavGraphBuilder.settingsRoutes(
    navController: NavHostController,
    capabilities: WearRestrictedCapabilities
) {
    composable(WearRoutes.SETTINGS) {
        SettingsScreen(navController = navController)
    }

    // S3178: three of these seven configure a capability the store artifact does not carry. A
    // settings page reads as a promise that the thing it configures exists, so they follow their
    // subject out of the store variant rather than staying as pages that change nothing.
    if (capabilities.offersMediaAccess) {
        composable(SettingsRoutes.MEDIA_TYPES) {
            MediaTypesSettingsScreen()
        }

        composable(SettingsRoutes.SLIDESHOW) {
            SlideshowSettingsScreen()
        }
    }

    composable(SettingsRoutes.SCREEN) {
        ScreenSettingsScreen()
    }

    composable(SettingsRoutes.OTHER) {
        OtherSettingsScreen()
    }

    if (capabilities.offersRemoteSources) {
        composable(SettingsRoutes.TILE_TARGETS) {
            TileTargetsSettingsScreen(navController = navController)
        }
    }

    // S3226: registered in every edition. The screen decides for itself what it can ask for, and the
    // menu entry above it is hidden where that answer is nothing, so a build without permissions has
    // no way in rather than a route that crashes when one is reached from elsewhere.
    composable(SettingsRoutes.PERMISSIONS) {
        PermissionsSettingsScreen()
    }

    composable(SettingsRoutes.ABOUT) {
        AboutSettingsScreen()
    }
}

/**
 * S1944: opens the channel the phone asked for, and only while this host is on screen.
 *
 * STARTED, not CREATED, is the boundary that makes the answer honest: a collector running while the
 * app is invisible would confirm an opening the user cannot see, and the phone would tell them it is
 * playing on the watch when nothing is. When nobody collects, the listener's short wait expires and
 * the phone is told the watch app was closed - which is the truth the platform leaves available
 * (strategic ADR-1).
 */
@Composable
private fun OpenStreamOnWatchEffect(
    navController: NavHostController,
    prepareStreamPlayback: PrepareWearStreamPlaybackUseCase,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(navController) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            WatchStreamOpenEvents.requestFlow.collect { channel ->
                val target = prepareStreamPlayback(channel)
                val route = if (target.isVideo) {
                    WearRoutes.videoPlayer(target.fileId)
                } else {
                    WearRoutes.audioPlayer(target.fileId)
                }
                navigateReplacingContent(navController, route)
                // Confirm only after navigating, so the phone's "playing" is a report, not a promise.
                WatchStreamOpenEvents.openedFlow.emit(channel.url)
            }
        }
    }
}

/**
 * S1884: opens the file the phone delivered, and only while this host is on screen.
 *
 * Deliberately the same shape as [OpenStreamOnWatchEffect], down to the STARTED boundary and the
 * confirm-after-navigating order: the two are one mechanism reaching two kinds of payload, and a
 * second collection idiom here would be a second set of lifecycle bugs to find on a real watch.
 */
@Composable
private fun OpenFileOnWatchEffect(
    navController: NavHostController,
    prepareFilePlayback: PrepareWearFilePlaybackUseCase,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(navController) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            WatchFileOpenEvents.requestFlow.collect { request ->

                val target = prepareFilePlayback(request)
                navigateReplacingContent(
                    navController,
                    playerRouteFor(target.fileId, target.mimeType, fileName = request.path)
                )
                // Confirm only after navigating, so the phone's "opened" is a report, not a promise.
                WatchFileOpenEvents.openedFlow.emit(request.path)
            }
        }
    }
}

/**
 * S3116: records the mini-program the host is showing, so the home row can offer it again.
 *
 * Keyed on the route rather than collecting an event stream: a program is reached from the Apps list,
 * from the home row, from a tile shortcut and from an external launch intent, and every one of those
 * ends as this route - one observation covers four entrances (strategic ADR-1). A route naming no
 * program records nothing, which is what leaves the value at the program actually opened last.
 */
@Composable
private fun RecordLastUsedAppEffect(
    currentRoute: String?,
    recordLastUsedApp: RecordLastUsedAppUseCase,
) {
    LaunchedEffect(currentRoute) {
        val program = currentRoute?.let(WearLaunchRoutes::appIdForRoute) ?: return@LaunchedEffect
        recordLastUsedApp(program)
    }
}

/**
 * S1955: opens what the launch intent named, once there is a host able to open it.
 *
 * Deliberately the same shape as [OpenStreamOnWatchEffect], including the STARTED boundary. The waiting is
 * the point: a tile tap starts a cold process, and the brand frame and the permission screen both stand in
 * front of this host, so the target has to outlive them rather than be read where it arrives.
 *
 * A null resolution navigates nowhere. The assigned target is gone, and leaving the user on the start
 * destination is a better answer than jumping at a screen with nothing behind it (strategic §5.2).
 */
@Composable
private fun OpenLaunchTargetEffect(
    navController: NavHostController,
    launchEntry: WearLaunchEntry,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(navController) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launchEntry.pendingTarget.filterNotNull().collect { target ->
                when (val address = launchEntry.resolveAddress(target)) {
                    // The whole path is otherwise silent: a deleted resource, a channel dropped from the
                    // catalog and an ordinary launch all look identical from outside the process.
                    null -> Timber.w("Launch target no longer resolves, staying put: %s", target)
                    else -> navigateGuarded(navController, WearLaunchRoutes.routeFor(address))
                }
                // Cleared even when nothing resolved, so the jump cannot be replayed.
                launchEntry.onHandled(target)
            }
        }
    }
}

/**
 * Navigates to [route], or logs it when the graph does not carry it.
 *
 * The target-picker address is minted before its screen exists - it lands a phase later - and this entry
 * point is reachable from outside the app, so an address the graph cannot serve must not take the process
 * down. Staying on the current screen is the same safe outcome as a target that no longer resolves.
 */
private fun navigateGuarded(navController: NavHostController, route: String) {
    try {
        navigateReplacingContent(navController, route)
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "Launch route is not in the navigation graph: %s", route)
    }
}

/**
 * S3213: opens [route], replacing the content destination the watch is already standing on.
 *
 * Every caller here is an entrance that can fire while a player is open - the phone's stream, the
 * phone's file, a launch intent, a second Start on the phone-camera screen - and each of them used to
 * push unconditionally. Two players then stood next to each other on the back stack and BACK from the
 * upper one resumed the older one instead of leaving the flow (observed on the S2551 device run).
 *
 * Only the destination the user can see is replaced, so a browse screen or a control screen under the
 * player survives and BACK still lands where the flow was entered. `launchSingleTop` covers the
 * narrower case of the very same address arriving twice.
 */
private fun navigateReplacingContent(navController: NavHostController, route: String) {
    val replaced = navController.currentBackStackEntry?.destination
        ?.takeIf { WearRoutes.isContentRoute(it.route) }
    navController.navigate(route) {
        replaced?.let { popUpTo(it.id) { inclusive = true } }
        launchSingleTop = true
    }
}

/**
 * S1944: the Apps section and its programs, lifted out of [MainNavigation] for the same reason as
 * [settingsRoutes] - the host was over detekt's length ceiling and this ticket adds a line to it.
 * The group is coherent on its own: everything reachable from the Apps list, and nothing else.
 */
private fun NavGraphBuilder.miniAppRoutes(
    navController: NavHostController,
    prepareVoiceNotePlayback: PrepareVoiceNotePlaybackUseCase,
    capabilities: WearRestrictedCapabilities,
) {
    composable(WearRoutes.APPS) {
        AppsScreen(navController = navController)
    }

    // Each program of the Apps list is replaced by its own phase; until then every entry still
    // leads somewhere, because a row navigating to an unregistered route is a dead tap.
    composable(WearRoutes.CALCULATOR) {
        // S1719: holding the menu key leaves the calculator, and leaving is the host's word.
        CalculatorScreen(onLeave = { navController.popBackStack() })
    }

    composable(WearRoutes.STOPWATCH) {
        // S2825: holding the menu control leaves the stopwatch, the calculator's gesture.
        WearStopwatchScreen(onLeave = { navController.popBackStack() })
    }

    // S3178: the radio sampling behind these two has no permission in the store artifact.
    if (capabilities.offersDeviceDiagnostics && capabilities.offersNearbyDeviceState) {
        composable(WearRoutes.NETWORK_MONITOR) {
            NetworkMonitorScreen(
                onNavigateToSection = { sectionKey ->
                    navController.navigate(WearRoutes.networkMonitorSection(sectionKey))
                }
            )
        }

        composable(WearRoutes.NETWORK_MONITOR_SECTION_PATTERN) { backStackEntry ->
            val sectionKey = backStackEntry.arguments?.getString(WearRoutes.ARG_NETMON_SECTION)
            NetworkMonitorDetailScreen(sectionKey = sectionKey.orEmpty())
        }
    }

    composable(WearRoutes.GAME) {
        GameScreen(navController = navController)
    }

    // S2516: leaving is the host's word here too, the way the calculator already has it - the screen
    // knows only that some hardware input arrived, never what to navigate to.
    // S3362: registered only where the screen takeover is offered. Both programs consume every
    // pointer event on the initial pass, so the route is the last place the store artifact can
    // refuse a screen WO-V3 says a swipe must be able to leave.
    if (capabilities.offersScreenTakeoverPrograms) {
        composable(WearRoutes.WATER_FLASHLIGHT) {
            WaterFlashlightScreen(onLeave = { navController.popBackStack() })
        }

        // S3216: leaving is the host's word here too, the way the water flashlight already has it -
        // the screen knows only that some hardware input arrived, never what to navigate to.
        composable(WearRoutes.SOS) {
            SosScreen(onLeave = { navController.popBackStack() })
        }
    }

    composable(WearRoutes.GAME_RULES) {
        GameRulesScreen()
    }

    // S1862: the recorder is a program of this list, and its note list is reached only from it -
    // so both live in this group rather than growing a fourth one for one feature.
    // S3178: neither is registered where the microphone service is not declared.
    if (capabilities.offersVoiceRecording) {
        composable(WearRoutes.VOICE_RECORDER) {
            VoiceRecorderScreen(
                navController = navController,
                onPlayNote = { note -> navigateToVoiceNote(navController, note, prepareVoiceNotePlayback) }
            )
        }

        composable(WearRoutes.VOICE_NOTES) {
            VoiceNoteListScreen(
                onPlayNote = { note -> navigateToVoiceNote(navController, note, prepareVoiceNotePlayback) }
            )
        }
    }

    // S2008: moved here from [settingsRoutes]. The screen configures nothing - it reports what this
    // watch is - so it is a program of this list rather than a settings destination.
    // S3178: that report is device and usage data, which the store artifact does not collect.
    if (capabilities.offersDeviceDiagnostics) {
        composable(WearRoutes.SYSTEM_INFO) {
            SystemInfoScreen()
        }

        // S3007: Tourist telemetry and navigation dashboard
        // S3216: the dashboard names the destination it wants and the host performs the jump, the way
        // every other screen here does - the screen holds no navigation controller of its own.
        // S3362: guarded rather than raw, because the distress signal now answers to a capability of
        // its own - the two travel together in both shipped flavors, but nothing here enforces that.
        composable(WearRoutes.TOURIST) {
            TouristScreen(onLaunchSos = { navigateGuarded(navController, WearRoutes.SOS) })
        }
    }

    // S3109: the watch's text clipboard, and the action that hands it to the paired phone.
    // S3362: that hand-off is the whole program, so it is registered where the transfer path is.
    if (capabilities.offersContentTransfer) {
        composable(WearRoutes.CLIPBOARD) {
            ClipboardScreen()
        }
    }

    healthAndHardwareAppRoutes(navController, capabilities)
}

/**
 * Health sensors, broadcast and auxiliary hardware program routes.
 */
private fun NavGraphBuilder.healthAndHardwareAppRoutes(
    navController: NavHostController,
    capabilities: WearRestrictedCapabilities,
) {
    if (capabilities.offersHealthFeatures) {
        // S2458: a live session rather than a report, so the destination owns nothing - leaving the
        // composition is what unregisters the sensors, through the repository's own awaitClose.
        composable(WearRoutes.MOTION_MONITOR) {
            MotionMonitorScreen(
                onHistoryClick = { navController.navigate(WearRoutes.MOTION_HISTORY) }
            )
        }

        composable(WearRoutes.MOTION_HISTORY) {
            MotionHistoryScreen()
        }

        // S2809: registered in noLegal flavor when offersHealthFeatures is true.
        composable(WearRoutes.BLOOD_PRESSURE) {
            BloodPressureScreen(
                onCalibrationClick = { navController.navigate(WearRoutes.BLOOD_PRESSURE_CALIBRATION) },
                onHistoryClick = { navController.navigate(WearRoutes.BLOOD_PRESSURE_HISTORY) }
            )
        }

        composable(WearRoutes.BLOOD_PRESSURE_CALIBRATION) {
            BloodPressureCalibrationScreen()
        }

        composable(WearRoutes.BLOOD_PRESSURE_HISTORY) {
            BloodPressureHistoryScreen()
        }
    }

    if (capabilities.offersBodySensorDiagnostics) {
        // S2457: registered in noLegal flavor when offersBodySensorDiagnostics is true.
        composable(WearRoutes.BODY_SENSOR) {
            BodySensorScreen(
                onHistoryClick = { navController.navigate(WearRoutes.HEART_RATE_HISTORY) }
            )
        }

        composable(WearRoutes.HEART_RATE_HISTORY) {
            HeartRateHistoryScreen()
        }
    }

    // S2509: reached from the Home section and from this list alike - one route, two entrances, as
    // the owner ruled. Going back from here does not stop the broadcast: the session belongs to the
    // foreground service, and its notification carries the same stop action.
    // S3178: both entrances close together in the store artifact, which declares no microphone.
    if (capabilities.offersVoiceRecording) {
        composable(WearRoutes.BROADCAST) {
            WearBroadcastScreen(onShowQr = { navController.navigate(WearRoutes.BROADCAST_QR) })
        }

        composable(WearRoutes.BROADCAST_QR) {
            WearBroadcastQrScreen()
        }
    }

    // S2551: leaving this screen DOES end the session, unlike the broadcast above - the camera runs
    // on the phone with no surface here to stop it from, so the screen's own lifetime is the
    // session's (strategic criterion 3).
    // S3178: the view arrives over the Data Layer, which the store artifact does not listen on.
    if (capabilities.offersContentTransfer) {
        composable(WearRoutes.PHONE_CAMERA) {
            PhoneCameraScreen(
                onWatch = { target ->
                    navigateReplacingContent(navController, WearRoutes.videoPlayer(target.fileId))
                }
            )
        }
    }

    // S3178: the refusal screen belongs to the media graph it refuses from - registering it where
    // no file can be opened would leave one reachable address into a capability that is gone.
    if (capabilities.offersMediaAccess) {
        composable(
            route = WearRoutes.UNSUPPORTED_FILE,
            arguments = listOf(navArgument(WearRoutes.ARG_DOCUMENT_FORMAT) { type = NavType.StringType })
        ) { entry ->
            val token = entry.arguments?.getString(WearRoutes.ARG_DOCUMENT_FORMAT)
            UnsupportedFileScreen(format = WearDocumentFormat.fromToken(token))
        }
    }
}

/**
 * S1944: everything reachable from Browse - the media-type list, a resource's files, the network
 * sources, the paired phone's browser, favourites and streams. Lifted out of [MainNavigation] for the
 * reason [settingsRoutes] and [miniAppRoutes] were: the host sat over detekt's length ceiling.
 */
private fun NavGraphBuilder.browseRoutes(navController: NavHostController) {
    // Browse screen with media type argument
    composable(
        route = WearRoutes.BROWSE_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_MEDIA_TYPE) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        BrowseScreen(navController = navController, backStackEntry = backStackEntry)
    }

    // Browse network source screen
    composable(
        route = WearRoutes.BROWSE_SOURCE_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_MEDIA_TYPE) { type = NavType.StringType },
            navArgument(WearRoutes.ARG_SOURCE_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(WearRoutes.ARG_SOURCE_NAME) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        BrowseScreen(navController = navController, backStackEntry = backStackEntry)
    }

    // Network sources list screen
    composable(WearRoutes.NETWORK_SOURCES) {
        NetworkSourcesScreen(navController = navController)
    }

    // S1829: the media-type step between a network source and browse. Without it the source list
    // was the only entrance and it hard-coded "music", so network images and video could not be
    // reached at all.
    composable(
        route = WearRoutes.SOURCE_MEDIA_TYPE_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_SOURCE_ID) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(WearRoutes.ARG_SOURCE_NAME) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { entry ->
        NetworkSourceMediaTypeScreen(
            navController = navController,
            sourceId = entry.arguments?.getString(WearRoutes.ARG_SOURCE_ID).orEmpty(),
            sourceName = entry.arguments?.getString(WearRoutes.ARG_SOURCE_NAME).orEmpty()
        )
    }

    // Paired-phone resource browser (S1697)
    composable(WearRoutes.PHONE_RESOURCE) {
        PhoneResourceScreen(navController = navController)
    }

    // Home section destinations (S1781). Every row the section catalog can return leads
    // somewhere: an unregistered route is a dead tap the user cannot tell apart from a bug.
    composable(WearRoutes.LOCAL_HOME) {
        LocalHomeScreen(navController = navController)
    }

    composable(WearRoutes.PHONE_HOME) {
        PhoneHomeScreen(navController = navController)
    }

    // S1846: the same browser the unfiltered Phone entrance opens. The media type is not passed
    // as a parameter - the view model reads it off the route, so both entrances share one screen
    // and one view model, and a chip differs from the unfiltered entrance only by its argument.
    composable(
        route = WearRoutes.PHONE_BROWSE_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_MEDIA_TYPE) { type = NavType.StringType }
        )
    ) {
        PhoneResourceScreen(navController = navController)
    }

    composable(WearRoutes.FAVOURITES) {
        FavouritesScreen(navController = navController)
    }

    composable(WearRoutes.STREAMS) {
        StreamsScreen(navController = navController)
    }
}

/**
 * S2201: the walk over the watch's own storage.
 *
 * Its own group rather than a line in [browseRoutes] because its file tap needs the playback
 * preparation, which nothing else in that group carries - handing it to the whole browse block would
 * pass a use case to six destinations that never touch it.
 */
private fun NavGraphBuilder.localFolderRoutes(
    navController: NavHostController,
    prepareFilePlayback: PrepareWearFilePlaybackUseCase,
    prepareNetworkFilePlayback: PrepareWearNetworkFilePlaybackUseCase
) {
    composable(
        route = WearRoutes.LOCAL_FOLDER_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_FOLDER_TOKEN) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            // S2694: absent for the local walk, which wants the default header.
            navArgument(WearRoutes.ARG_FOLDER_TITLE) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { entry ->
        // The walk's entrance decides which half of the walk this is, and it does not change as the
        // wearer descends: a network walk holds only network rows, a local one only local rows. The
        // level's own address is the screen's private state, so the route argument is the one place
        // the host can read it from.
        val walkSourceId = (
            WearFolderAddress.parse(entry.arguments?.getString(WearRoutes.ARG_FOLDER_TOKEN))
                as? WearFolderAddress.NetworkLevel
            )?.sourceId
        WearFolderWalkScreen(
            onOpenFile = { row ->
                val uri = row.uri ?: return@WearFolderWalkScreen
                val fileId = if (walkSourceId == null) {
                    localFileIdFor(uri, row.mimeType, prepareFilePlayback)
                } else {
                    prepareNetworkFilePlayback(
                        WearNetworkFileOpenRequest(
                            sourceId = walkSourceId,
                            uri = uri,
                            name = row.name,
                            mimeType = row.mimeType,
                            sizeBytes = row.sizeBytes,
                            dateModifiedEpochSeconds = row.dateModifiedEpochSeconds
                        )
                    ).fileId
                }
                navController.navigate(playerRouteFor(fileId, row.mimeType, fileName = row.name))
            },
            onOpenContainer = { fileId ->
                navController.navigate(WearRoutes.fdSecCredential(fileId, WearFdSecMode.OPEN))
            },
            onExit = { navController.popBackStack() }
        )
    }
}

/**
 * How a player addresses a file the walk found.
 *
 * The walk spans two halves of watch storage and they are addressable in different ways. A MediaStore
 * row already is: the players look an id up in MediaStore, and the walk's uri carries that id. A file
 * in the app's own tree has no row to look up, so it goes through the same hand-off S1884 built for a
 * file the phone delivered - a second preparation here would be free to drift from that one.
 *
 * A uri that is neither yields [UNRESOLVED_FILE_ID], which is the value the players already read as
 * "nothing was selected" rather than a crash on a malformed address.
 */
private fun localFileIdFor(
    uri: Uri,
    mimeType: String?,
    prepareFilePlayback: PrepareWearFilePlaybackUseCase
): Long {
    val path = uri.path
    return if (uri.scheme == ContentResolver.SCHEME_FILE && path != null) {
        prepareFilePlayback(WearFileOpenRequest(path = path, mimeType = mimeType.orEmpty())).fileId
    } else {
        uri.lastPathSegment?.toLongOrNull() ?: UNRESOLVED_FILE_ID
    }
}

/**
 * S2161: opens a voice note in the watch's existing audio player.
 *
 * The same [playerRouteFor] the folder walk uses, so the watch keeps one way of opening one file
 * (strategic section 5.5) rather than growing a second route for the recorder. A note the use case
 * cannot address - no published row and no readable private file - navigates nowhere, because the
 * players read an unresolved id as "nothing was selected" and would show an empty player instead of
 * saying anything.
 */
private fun navigateToVoiceNote(
    navController: NavHostController,
    note: VoiceNote,
    prepareVoiceNotePlayback: PrepareVoiceNotePlaybackUseCase
) {
    val fileId = prepareVoiceNotePlayback(note) ?: return
    navController.navigate(playerRouteFor(fileId, VOICE_NOTE_MIME_TYPE))
}

/**
 * S1955: Tile-related destinations (target picker).
 */
private fun NavGraphBuilder.tileRoutes(navController: NavHostController) {
    composable(
        route = WearRoutes.TILE_TARGET_PICKER_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_TILE_KIND) { type = NavType.StringType }
        )
    ) {
        TileTargetPickerScreen(navController = navController)
    }
}
