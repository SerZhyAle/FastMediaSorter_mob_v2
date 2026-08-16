package com.sza.fastmediasorter.wear.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.settings.SettingsViewModel
import timber.log.Timber

@Composable
fun HomeScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    Timber.d("HomeScreen composing")

    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val allCategories = listOf(
        MediaCategory(stringResource(R.string.music), "music", Icons.Filled.MusicNote),
        MediaCategory(stringResource(R.string.videos), "videos", Icons.Filled.VideoLibrary),
        MediaCategory(stringResource(R.string.photos), "photos", Icons.Filled.Image)
    )

    // Filter categories based on settings
    val filteredCategories = allCategories.filter { category ->
        when (category.route) {
            "music" -> settings.isAudioEnabled
            "videos" -> settings.isVideoEnabled
            "photos" -> settings.isImagesEnabled
            else -> true
        }
    }

    val listState = rememberScalingLazyListState()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        positionIndicator = { PositionIndicator(listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets()
        ) {
            item {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
            }

            items(filteredCategories) { category ->
                Chip(
                    onClick = {
                        Timber.d("Category clicked: ${category.route}")
                        navController.navigate(WearRoutes.browse(category.route))
                    },
                    label = {
                        Text(text = category.name)
                    },
                    icon = {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors()
                )
            }

            // Network Storage button
            item {
                Chip(
                    onClick = {
                        Timber.d("Network Storage clicked")
                        navController.navigate(WearRoutes.NETWORK_SOURCES)
                    },
                    label = {
                        Text(text = stringResource(R.string.network_storage))
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors()
                )
            }

            // Paired-phone resource (S1697). Stays visible with no phone connected: the screen
            // itself explains the absence and offers Retry, which a disabled chip could not.
            item {
                Chip(
                    onClick = {
                        Timber.d("Phone resource clicked")
                        navController.navigate(WearRoutes.PHONE_RESOURCE)
                    },
                    label = {
                        Text(text = stringResource(R.string.phone_resource_title))
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.PhoneAndroid,
                            contentDescription = stringResource(R.string.phone_resource_title),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors()
                )
            }

            // Settings button
            item {
                Chip(
                    onClick = {
                        Timber.d("Settings clicked")
                        navController.navigate(WearRoutes.SETTINGS)
                    },
                    label = {
                        Text(text = stringResource(R.string.settings))
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

data class MediaCategory(
    val name: String,
    val route: String,
    val icon: ImageVector
)
