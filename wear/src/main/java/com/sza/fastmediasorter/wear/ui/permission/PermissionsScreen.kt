package com.sza.fastmediasorter.wear.ui.permission

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import timber.log.Timber

/**
 * Screen that requests runtime permissions for media access.
 * Required for Android 13+ (API 33+) to access audio, video, and images.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(
    onPermissionsGranted: () -> Unit,
    listState: ScalingLazyListState = rememberWearListState(initialCenterItemIndex = 0)
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
        Timber.d("S2273: PermissionsScreen rendered, allPermissionsGranted=${permissionsState.allPermissionsGranted}")
        if (permissionsState.allPermissionsGranted) {
            Timber.d("All permissions already granted, navigating to home")
            onPermissionsGranted()
        }
    }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            centered = true
        ) {
            item {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colors.onBackground
                )
            }

            item {
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(
                    text = stringResource(R.string.permission_description),
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    onClick = {
                        Timber.d("Requesting permissions: $mediaPermissions")
                        permissionsState.launchMultiplePermissionRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = stringResource(R.string.permission_grant_button),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    colors = ChipDefaults.primaryChipColors()
                )
            }

            // Show rationale if needed
            if (permissionsState.shouldShowRationale) {
                item {
                    Text(
                        text = stringResource(R.string.permission_rationale),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
