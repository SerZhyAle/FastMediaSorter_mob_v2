package com.sza.fastmediasorter.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.sza.fastmediasorter.wear.ui.browse.BrowseScreen
import com.sza.fastmediasorter.wear.ui.home.HomeScreen
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.network.AddNetworkSourceScreen
import com.sza.fastmediasorter.wear.ui.network.NetworkSourcesScreen
import com.sza.fastmediasorter.wear.ui.network.SyncResultScreen
import com.sza.fastmediasorter.wear.ui.network.SyncTransferScreen
import com.sza.fastmediasorter.wear.ui.permission.PermissionsScreen
import com.sza.fastmediasorter.wear.ui.phone.PhoneResourceScreen
import com.sza.fastmediasorter.wear.ui.player.audio.AudioPlayerScreen
import com.sza.fastmediasorter.wear.ui.player.image.ImageViewerScreen
import com.sza.fastmediasorter.wear.ui.player.video.VideoPlayerScreen
import com.sza.fastmediasorter.wear.ui.settings.SettingsScreen
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Log app info and configuration
        logAppInfo()
        
        Timber.d("MainActivity created")
        
        // Check if permissions are already granted
        val hasPermissions = hasMediaPermissions()
        
        setContent {
            WearApp(initialHasPermissions = hasPermissions)
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
fun WearApp(initialHasPermissions: Boolean = false) {
    WearAppTheme {
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
            MainNavigation()
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberSwipeDismissableNavController()
    
    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearRoutes.HOME,
        modifier = Modifier.background(Color.Black)
    ) {
        composable(WearRoutes.HOME) {
            HomeScreen(navController = navController)
        }
        
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

        // Paired-phone resource browser (S1697)
        composable(WearRoutes.PHONE_RESOURCE) {
            PhoneResourceScreen(navController = navController)
        }
        
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
        
        // Settings screen
        composable(WearRoutes.SETTINGS) {
            SettingsScreen(navController = navController)
        }
    }
}
