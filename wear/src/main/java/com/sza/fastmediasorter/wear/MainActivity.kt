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
import com.sza.fastmediasorter.wear.ui.network.AddSmbScreen
import com.sza.fastmediasorter.wear.ui.network.NetworkSourcesScreen
import com.sza.fastmediasorter.wear.ui.permission.PermissionsScreen
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
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "unknown"
            val versionCode = packageInfo.versionCode
            
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
        startDestination = "home",
        modifier = Modifier.background(Color.Black)
    ) {
        composable("home") {
            HomeScreen(navController = navController)
        }
        
        // Browse screen with media type argument
        composable(
            route = "browse/{mediaType}",
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType }
            )
        ) {
            BrowseScreen(navController = navController)
        }
        
        // Browse network source screen
        composable(
            route = "browse/{mediaType}?sourceId={sourceId}&sourceName={sourceName}",
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("sourceId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("sourceName") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) {
            BrowseScreen(navController = navController)
        }
        
        // Network sources list screen
        composable("network_sources") {
            NetworkSourcesScreen(navController = navController)
        }
        
        // Add SMB connection screen
        composable("add_smb") {
            AddSmbScreen(navController = navController)
        }
        
        // Audio player screen
        composable(
            route = "audio_player/{fileId}",
            arguments = listOf(
                navArgument("fileId") { type = NavType.LongType }
            )
        ) {
            AudioPlayerScreen()
        }
        
        // Video player screen
        composable(
            route = "video_player/{fileId}",
            arguments = listOf(
                navArgument("fileId") { type = NavType.LongType }
            )
        ) {
            VideoPlayerScreen()
        }
        
        // Image viewer screen
        composable(
            route = "image_viewer/{fileId}",
            arguments = listOf(
                navArgument("fileId") { type = NavType.LongType }
            )
        ) {
            ImageViewerScreen()
        }
        
        // Settings screen
        composable("settings") {
            SettingsScreen(navController = navController)
        }
    }
}
