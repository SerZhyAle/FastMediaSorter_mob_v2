package com.sza.fastmediasorter.wear

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.sza.fastmediasorter.wear.core.util.WearLocaleManager
import com.sza.fastmediasorter.wear.data.wear.WatchStreamOpenEvents
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.PrepareWearStreamPlaybackUseCase
import com.sza.fastmediasorter.wear.ui.apps.AppsScreen
import com.sza.fastmediasorter.wear.ui.apps.calculator.CalculatorScreen
import com.sza.fastmediasorter.wear.ui.apps.game.GameScreen
import com.sza.fastmediasorter.wear.ui.apps.netmonitor.NetworkMonitorScreen
import com.sza.fastmediasorter.wear.ui.browse.BrowseScreen
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
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
import com.sza.fastmediasorter.wear.ui.player.video.VideoPlayerScreen
import com.sza.fastmediasorter.wear.ui.settings.AboutSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.MediaTypesSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.OtherSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.ScreenSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.SettingsRoutes
import com.sza.fastmediasorter.wear.ui.settings.SettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.SlideshowSettingsScreen
import com.sza.fastmediasorter.wear.ui.settings.SystemInfoSettingsScreen
import com.sza.fastmediasorter.wear.ui.streams.StreamsScreen
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

/** The three routes whose players hold the screen unconditionally, so the setting must not reach them. */
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Log app info and configuration
        logAppInfo()
        
        Timber.d("MainActivity created")
        
        // Check if permissions are already granted
        val hasPermissions = hasMediaPermissions()
        
        setContent {
            WearApp(
                initialHasPermissions = hasPermissions,
                keepScreenAwakeOutsidePlayers = preferencesRepository.keepScreenAwakeOutsidePlayers,
                isAutoRotationEnabled = preferencesRepository.isAutoRotationEnabled,
                appLanguage = preferencesRepository.appLanguage,
                prepareStreamPlayback = prepareStreamPlayback
            )
        }
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
            Timber.d("Package: ${packageName}")
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
    prepareStreamPlayback: PrepareWearStreamPlaybackUseCase
) {
    WearAppTheme {
        AutoLocaleEffect(appLanguage = appLanguage)
        AutoRotationEffect(isAutoRotationEnabled = isAutoRotationEnabled)
        var hasPermissions by remember { mutableStateOf(initialHasPermissions) }
        
        if (!hasPermissions) {
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
                keepScreenAwakeOutsidePlayers = keepScreenAwakeOutsidePlayers,
                prepareStreamPlayback = prepareStreamPlayback,
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

@Composable
fun MainNavigation(
    keepScreenAwakeOutsidePlayers: Flow<Boolean>,
    prepareStreamPlayback: PrepareWearStreamPlaybackUseCase,
) {
    val navController = rememberSwipeDismissableNavController()

    OpenStreamOnWatchEffect(navController = navController, prepareStreamPlayback = prepareStreamPlayback)

    // One shared call site rather than the effect on each non-player screen: two composables both
    // touching FLAG_KEEP_SCREEN_ON on the same window would clear each other's flag on navigation.
    val keepAwake by keepScreenAwakeOutsidePlayers.collectAsStateWithLifecycle(initialValue = false)
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    KeepScreenOnEffect(enabled = keepAwake && currentRoute !in PLAYER_ROUTES)

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearRoutes.HOME,
        modifier = Modifier.background(Color.Black)
    ) {
        composable(WearRoutes.HOME) {
            HomeScreen(navController = navController)
        }
        
        browseRoutes(navController = navController)

        miniAppRoutes(navController = navController)
        
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

/**
 * S1944: the settings half of the graph, lifted out of [MainNavigation].
 *
 * Not a rearrangement for taste: the host function was at detekt's length ceiling, so the one line
 * this ticket adds to it had to be paid for. These six destinations are the largest block that shares
 * a single subject and needs nothing from the host but the controller.
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

    composable(SettingsRoutes.SYSTEM_INFO) {
        SystemInfoSettingsScreen()
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
    ) {
        BrowseScreen(navController = navController)
    }
    
    // Browse network source screen
    composable(
        route = WearRoutes.BROWSE_SOURCE_PATTERN,
        arguments = listOf(
            navArgument(WearRoutes.ARG_MEDIA_TYPE) { type = NavType.StringType },
            navArgument(WearRoutes.ARG_SOURCE_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(WearRoutes.ARG_SOURCE_NAME) {
                type = NavType.StringType; nullable = true; defaultValue = null
            }
        )
    ) {
        BrowseScreen(navController = navController)
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
