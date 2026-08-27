package com.sza.fastmediasorter.wear

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.BadParcelableException
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
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
import com.sza.fastmediasorter.wear.data.wear.WatchFileOpenEvents
import com.sza.fastmediasorter.wear.data.wear.WatchStreamOpenEvents
import com.sza.fastmediasorter.wear.domain.model.WearBackground
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.readWearLaunchTarget
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearFilePlaybackUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearStreamPlaybackUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ResolveWearBackgroundUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ResolveWearLaunchRouteUseCase
import com.sza.fastmediasorter.wear.ui.apps.AppsScreen
import com.sza.fastmediasorter.wear.ui.apps.calculator.CalculatorScreen
import com.sza.fastmediasorter.wear.ui.apps.game.GameScreen
import com.sza.fastmediasorter.wear.ui.apps.netmonitor.NetworkMonitorScreen
import com.sza.fastmediasorter.wear.ui.apps.systeminfo.SystemInfoScreen
import com.sza.fastmediasorter.wear.ui.brand.BrandFrameScreen
import com.sza.fastmediasorter.wear.ui.browse.BrowseScreen
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
import com.sza.fastmediasorter.wear.ui.common.WearAppBackground
import com.sza.fastmediasorter.wear.ui.common.playerRouteFor
import com.sza.fastmediasorter.wear.ui.favourites.FavouritesScreen
import com.sza.fastmediasorter.wear.ui.home.HomeScreen
import com.sza.fastmediasorter.wear.ui.home.LocalHomeScreen
import com.sza.fastmediasorter.wear.ui.home.PhoneHomeScreen
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.network.AddNetworkSourceScreen
import com.sza.fastmediasorter.wear.ui.network.NetworkSourceMediaTypeScreen
import com.sza.fastmediasorter.wear.ui.network.NetworkSourcesScreen
import com.sza.fastmediasorter.wear.ui.network.SyncResultScreen
import com.sza.fastmediasorter.wear.ui.network.SyncTransferScreen
import com.sza.fastmediasorter.wear.ui.permission.PermissionsScreen
import com.sza.fastmediasorter.wear.ui.phone.PhoneResourceScreen
import com.sza.fastmediasorter.wear.ui.player.audio.AudioPlayerScreen
import com.sza.fastmediasorter.wear.ui.player.image.ImageViewerScreen
import com.sza.fastmediasorter.wear.ui.player.unsupported.UnsupportedFileScreen
import com.sza.fastmediasorter.wear.ui.player.video.VideoPlayerScreen
import com.sza.fastmediasorter.wear.ui.settings.AboutSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.MediaTypesSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.OtherSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.ScreenSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.SettingsRoutes
import com.sza.fastmediasorter.wear.ui.settings.SettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.SlideshowSettingsScreen
import com.sza.fastmediasorter.wear.ui.streams.StreamsScreen
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import com.sza.fastmediasorter.wear.ui.tile.TileTargetPickerScreen
import com.sza.fastmediasorter.wear.ui.voicenote.VoiceNoteListScreen
import com.sza.fastmediasorter.wear.ui.voicenote.VoiceRecorderScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
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
)

/**
 * S1955: the launch-intent wiring, handed down as one thing.
 *
 * The three are useless apart - a resolver with nothing pending resolves nothing, and clearing without
 * both cannot be ordered after the navigation - so they travel together rather than as three parameters
 * repeated at every level between the Activity and the navigation host.
 */
data class WearLaunchEntry(
    val resolveRoute: ResolveWearLaunchRouteUseCase,
    val pendingTarget: StateFlow<WearLaunchTarget?>,
    /** Takes the target it handled, so a newer one that arrived mid-resolution is not cleared unhandled. */
    val onHandled: (WearLaunchTarget) -> Unit,
)

/** The three player routes: each covers the whole window, so the shared background is not drawn behind them. */
private val PLAYER_ROUTES = setOf(
    WearRoutes.AUDIO_PLAYER_PATTERN,
    WearRoutes.VIDEO_PLAYER_PATTERN,
    WearRoutes.IMAGE_VIEWER_PATTERN
)

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

    // S1955: a tile names its target in the launch intent, and resolving it reads the stores.
    @Inject lateinit var resolveLaunchRoute: ResolveWearLaunchRouteUseCase

    // S2000: injected here and handed down for the same reason the two playback use cases are -
    // the navigation host is the one place that sits above every screen the background shows behind.
    @Inject lateinit var resolveBackground: ResolveWearBackgroundUseCase

    // S1961: the pending-open notification is this app's own, so it is this app that puts it away
    // once the user is here and no longer needs it.
    @Inject lateinit var openOnWatchNotifier: WearOpenOnWatchNotifier

    /**
     * S1955: what this launch asked to open, until the navigation host has opened it.
     *
     * Held here rather than read inside the host because the host is not composed yet when the intent
     * arrives - the brand frame and the permission screen both stand in front of it on a cold start, which
     * is exactly the start a tile tap produces.
     */
    private val pendingLaunchTarget = MutableStateFlow<WearLaunchTarget?>(null)

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

        // Check if permissions are already granted
        val hasPermissions = hasMediaPermissions()

        setContent {
            AskNotificationPermissionEffect(
                alreadyAsked = preferencesRepository.notificationPermissionAsked,
                onAsked = { lifecycleScope.launch { preferencesRepository.setNotificationPermissionAsked(true) } }
            )
            WearApp(
                initialHasPermissions = hasPermissions,
                keepScreenAwakeOutsidePlayers = preferencesRepository.keepScreenAwakeOutsidePlayers,
                isAutoRotationEnabled = preferencesRepository.isAutoRotationEnabled,
                appLanguage = preferencesRepository.appLanguage,
                hostUseCases = WearHostUseCases(
                    prepareStreamPlayback = prepareStreamPlayback,
                    prepareFilePlayback = prepareFilePlayback,
                    resolveBackground = resolveBackground
                ),
                launchEntry = WearLaunchEntry(
                    resolveRoute = resolveLaunchRoute,
                    pendingTarget = pendingLaunchTarget,
                    onHandled = { handled -> pendingLaunchTarget.compareAndSet(handled, null) }
                )
            )
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

        Timber.d("S1961: MainActivity onStart cancelling pending open notification")
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
                @Suppress("DEPRECATION")
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
    initialHasPermissions: Boolean = false,
    keepScreenAwakeOutsidePlayers: Flow<Boolean>,
    isAutoRotationEnabled: Flow<Boolean>,
    appLanguage: Flow<String?>,
    // S1944: passed down rather than resolved in the composable, so the one instance the Activity
    // injects is the one both entrances to a player use.
    hostUseCases: WearHostUseCases,
    // S1955: read-only here and cleared through the callback, so the one writer stays the Activity.
    launchEntry: WearLaunchEntry
) {
    WearAppTheme {
        AutoLocaleEffect(appLanguage = appLanguage)
        AutoRotationEffect(isAutoRotationEnabled = isAutoRotationEnabled)
        val keepAwake by keepScreenAwakeOutsidePlayers.collectAsStateWithLifecycle(initialValue = false)
        KeepScreenOnEffect(enabled = keepAwake)
        var hasPermissions by remember { mutableStateOf(initialHasPermissions) }
        // S1981: scoped to this composable, not `rememberSaveable` or persistent storage - it
        // resets only when `WearApp` itself is recreated (a cold start), never on backgrounding/
        // foregrounding or in-app navigation back to Home (strategic §6 item 4).
        var showBrandFrame by remember { mutableStateOf(true) }

        if (showBrandFrame) {
            BrandFrameScreen(onTimeout = { showBrandFrame = false })
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

@Composable
fun MainNavigation(
    hostUseCases: WearHostUseCases,
    launchEntry: WearLaunchEntry,
) {
    val navController = rememberSwipeDismissableNavController()

    OpenStreamOnWatchEffect(navController = navController, prepareStreamPlayback = hostUseCases.prepareStreamPlayback)

    OpenFileOnWatchEffect(navController = navController, prepareFilePlayback = hostUseCases.prepareFilePlayback)

    OpenLaunchTargetEffect(navController = navController, launchEntry = launchEntry)

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

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

    Box(modifier = Modifier.fillMaxSize()) {
        WearAppBackground(
            background = background,
            running = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
                currentRoute !in PLAYER_ROUTES
        )
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = WearRoutes.HOME,
        ) {
            composable(WearRoutes.HOME) {
                HomeScreen(navController = navController)
            }

            browseRoutes(navController = navController)

            miniAppRoutes(navController = navController)

            tileRoutes(navController = navController)

            // Add network source screen
            composable(WearRoutes.ADD_NETWORK_SOURCE) {
                AddNetworkSourceScreen(navController = navController)
            }

            // Backward-compatible alias for old route name
            composable(WearRoutes.ADD_SMB_ALIAS) {
                AddNetworkSourceScreen(navController = navController)
            }

            // Sync transfer animation (shown while receiving data from phone)
            composable(WearRoutes.SYNC_TRANSFER) {
                SyncTransferScreen(navController = navController)
            }

            // Sync result screen (shown after successful sync)
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

            // Audio player screen
            composable(
                route = WearRoutes.AUDIO_PLAYER_PATTERN,
                arguments = listOf(
                    navArgument(WearRoutes.ARG_FILE_ID) { type = NavType.LongType }
                )
            ) {
                AudioPlayerScreen()
            }

            // Video player screen
            composable(
                route = WearRoutes.VIDEO_PLAYER_PATTERN,
                arguments = listOf(
                    navArgument(WearRoutes.ARG_FILE_ID) { type = NavType.LongType }
                )
            ) {
                VideoPlayerScreen()
            }

            // Image viewer screen
            composable(
                route = WearRoutes.IMAGE_VIEWER_PATTERN,
                arguments = listOf(
                    navArgument(WearRoutes.ARG_FILE_ID) { type = NavType.LongType }
                )
            ) {
                ImageViewerScreen()
            }

            settingsRoutes(navController = navController)
        }
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
private fun NavGraphBuilder.settingsRoutes(navController: NavHostController) {
    composable(WearRoutes.SETTINGS) {
        SettingsScreen(navController = navController)
    }

    composable(SettingsRoutes.MEDIA_TYPES) {
        MediaTypesSettingsScreen()
    }

    composable(SettingsRoutes.SLIDESHOW) {
        SlideshowSettingsScreen()
    }

    composable(SettingsRoutes.SCREEN) {
        ScreenSettingsScreen()
    }

    composable(SettingsRoutes.OTHER) {
        OtherSettingsScreen()
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
                navController.navigate(route)
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
                navController.navigate(playerRouteFor(target.fileId, target.mimeType))
                // Confirm only after navigating, so the phone's "opened" is a report, not a promise.
                WatchFileOpenEvents.openedFlow.emit(request.path)
            }
        }
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
                when (val route = launchEntry.resolveRoute(target)) {
                    // The whole path is otherwise silent: a deleted resource, a channel dropped from the
                    // catalog and an ordinary launch all look identical from outside the process.
                    null -> Timber.w("Launch target no longer resolves, staying put: %s", target)
                    else -> navigateGuarded(navController, route)
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
        navController.navigate(route)
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "Launch route is not in the navigation graph: %s", route)
    }
}

/**
 * S1944: the Apps section and its programs, lifted out of [MainNavigation] for the same reason as
 * [settingsRoutes] - the host was over detekt's length ceiling and this ticket adds a line to it.
 * The group is coherent on its own: everything reachable from the Apps list, and nothing else.
 */
private fun NavGraphBuilder.miniAppRoutes(navController: NavHostController) {
    composable(WearRoutes.APPS) {
        AppsScreen(navController = navController)
    }

    // Each program of the Apps list is replaced by its own phase; until then every entry still
    // leads somewhere, because a row navigating to an unregistered route is a dead tap.
    composable(WearRoutes.CALCULATOR) {
        // S1719: holding the menu key leaves the calculator, and leaving is the host's word.
        CalculatorScreen(onLeave = { navController.popBackStack() })
    }

    composable(WearRoutes.NETWORK_MONITOR) {
        NetworkMonitorScreen()
    }

    composable(WearRoutes.GAME) {
        GameScreen(navController = navController)
    }

    // S1862: the recorder is a program of this list, and its note list is reached only from it -
    // so both live in this group rather than growing a fourth one for one feature.
    composable(WearRoutes.VOICE_RECORDER) {
        VoiceRecorderScreen(navController = navController)
    }

    composable(WearRoutes.VOICE_NOTES) {
        VoiceNoteListScreen()
    }

    // S2008: moved here from [settingsRoutes]. The screen configures nothing - it reports what this
    // watch is - so it is a program of this list rather than a settings destination.
    composable(WearRoutes.SYSTEM_INFO) {
        SystemInfoScreen()
    }

    composable(WearRoutes.UNSUPPORTED_FILE) {
        UnsupportedFileScreen()
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
