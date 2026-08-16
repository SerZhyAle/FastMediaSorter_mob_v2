package com.sza.fastmediasorter.wear.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets

/**
 * S1724: the media-type switches, moved out of the single settings list into a group of their own.
 *
 * The rows are the ones the flat screen carried, unchanged - same state, same callbacks, same toggle
 * control. Only their address changes: a watch screen shows a handful of rows at a time, so a list that
 * holds every setting is navigated by scrolling past the ones you did not want.
 *
 * [navController] is taken but unused for now: every group screen in this ticket carries the same
 * signature so the route table treats them alike, and the back gesture is the platform's own.
 */
@Composable
fun MediaTypesSettingsScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberScalingLazyListState()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        positionIndicator = { PositionIndicator(listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.media_types),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
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
        }
    }
}
