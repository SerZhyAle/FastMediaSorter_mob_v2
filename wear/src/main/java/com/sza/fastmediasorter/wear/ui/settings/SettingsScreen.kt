package com.sza.fastmediasorter.wear.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.rememberScalingLazyListState
import com.sza.fastmediasorter.wear.R

/**
 * Settings screen for Wear OS app.
 * Allows users to configure media types, slideshow, and album art settings.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberScalingLazyListState()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header
        item {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.title2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        // Media Types Section
        item {
            Text(
                text = stringResource(R.string.media_types),
                style = MaterialTheme.typography.title3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        item {
            ToggleChip(
                checked = uiState.isAudioEnabled,
                onCheckedChange = { viewModel.toggleAudio() },
                label = {
                    Text(text = stringResource(R.string.enable_audio))
                },
                toggleControl = {
                    androidx.wear.compose.material.Icon(
                        imageVector = ToggleChipDefaults.switchIcon(checked = uiState.isAudioEnabled),
                        contentDescription = null
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors()
            )
        }

        item {
            ToggleChip(
                checked = uiState.isVideoEnabled,
                onCheckedChange = { viewModel.toggleVideo() },
                label = {
                    Text(text = stringResource(R.string.enable_video))
                },
                toggleControl = {
                    androidx.wear.compose.material.Icon(
                        imageVector = ToggleChipDefaults.switchIcon(checked = uiState.isVideoEnabled),
                        contentDescription = null
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors()
            )
        }

        item {
            ToggleChip(
                checked = uiState.isImagesEnabled,
                onCheckedChange = { viewModel.toggleImages() },
                label = {
                    Text(text = stringResource(R.string.enable_images))
                },
                toggleControl = {
                    androidx.wear.compose.material.Icon(
                        imageVector = ToggleChipDefaults.switchIcon(checked = uiState.isImagesEnabled),
                        contentDescription = null
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors()
            )
        }

        // Slideshow Section
        item {
            Text(
                text = stringResource(R.string.slideshow_settings),
                style = MaterialTheme.typography.title3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        item {
            ToggleChip(
                checked = uiState.isSlideshowEnabled,
                onCheckedChange = { viewModel.toggleSlideshow() },
                label = {
                    Text(text = stringResource(R.string.enable_slideshow))
                },
                toggleControl = {
                    androidx.wear.compose.material.Icon(
                        imageVector = ToggleChipDefaults.switchIcon(checked = uiState.isSlideshowEnabled),
                        contentDescription = null
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors()
            )
        }

        item {
            Text(
                text = stringResource(R.string.slideshow_interval, uiState.slideshowIntervalSeconds),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption1
            )
        }

        item {
            ToggleChip(
                checked = uiState.slideshowWaitForFinish,
                onCheckedChange = { viewModel.toggleWaitForFinish() },
                label = {
                    Text(text = stringResource(R.string.wait_for_finish))
                },
                toggleControl = {
                    androidx.wear.compose.material.Icon(
                        imageVector = ToggleChipDefaults.switchIcon(checked = uiState.slideshowWaitForFinish),
                        contentDescription = null
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors()
            )
        }

        // Audio Settings Section
        item {
            Text(
                text = stringResource(R.string.audio_settings),
                style = MaterialTheme.typography.title3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        item {
            ToggleChip(
                checked = uiState.downloadAlbumArt,
                onCheckedChange = { viewModel.toggleAlbumArt() },
                label = {
                    Text(text = stringResource(R.string.download_album_art))
                },
                toggleControl = {
                    androidx.wear.compose.material.Icon(
                        imageVector = ToggleChipDefaults.switchIcon(checked = uiState.downloadAlbumArt),
                        contentDescription = null
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors()
            )
        }

        // About Section
        item {
            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.title3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        item {
            Text(
                text = stringResource(R.string.version, uiState.appVersion),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption1
            )
        }

        item {
            Text(
                text = stringResource(R.string.build_number, uiState.buildNumber),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption1
            )
        }
    }
}
