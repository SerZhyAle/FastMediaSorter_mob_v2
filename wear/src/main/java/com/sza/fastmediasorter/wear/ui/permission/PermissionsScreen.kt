package com.sza.fastmediasorter.wear.ui.permission

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.sza.fastmediasorter.wear.R
import timber.log.Timber

/**
 * Screen that requests runtime permissions for media access.
 * Required for Android 13+ (API 33+) to access audio, video, and images.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(
    onPermissionsGranted: () -> Unit
) {
    // Define required permissions based on API level
    val mediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    val permissionsState = rememberMultiplePermissionsState(
        permissions = mediaPermissions,
        onPermissionsResult = { results ->
            val allGranted = results.values.all { it }
            Timber.d("Permissions result: allGranted=$allGranted, results=$results")
            if (allGranted) {
                onPermissionsGranted()
            }
        }
    )
    
    // Auto-navigate if already granted
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            Timber.d("All permissions already granted, navigating to home")
            onPermissionsGranted()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.White
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = stringResource(R.string.permission_title),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.permission_description),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                Timber.d("Requesting permissions: $mediaPermissions")
                permissionsState.launchMultiplePermissionRequest()
            }
        ) {
            Text(text = stringResource(R.string.permission_grant_button))
        }
        
        // Show rationale if needed
        if (permissionsState.shouldShowRationale) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permission_rationale),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFFFF9800) // Orange warning color
            )
        }
    }
}
