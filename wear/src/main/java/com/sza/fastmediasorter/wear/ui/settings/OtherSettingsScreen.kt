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

@Composable
fun OtherSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberScalingLazyListState()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
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
                    text = stringResource(R.string.settings_group_other),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
            item {
                ToggleChip(
                    checked = uiState.downloadAlbumArt,
                    onCheckedChange = { viewModel.toggleAlbumArt() },
                    label = { Text(stringResource(R.string.download_album_art)) },
                    toggleControl = {
                        androidx.wear.compose.material.Icon(
                            imageVector = ToggleChipDefaults.switchIcon(uiState.downloadAlbumArt),
                            contentDescription = null
                        )
                    },
                    colors = ToggleChipDefaults.toggleChipColors()
                )
            }
            if (uiState.hasAutoRotationSensor) {
                item {
                    ToggleChip(
                        checked = uiState.isAutoRotationEnabled,
                        onCheckedChange = { viewModel.toggleAutoRotation() },
                        label = { Text(stringResource(R.string.wear_auto_rotation)) },
                        toggleControl = {
                            androidx.wear.compose.material.Icon(
                                imageVector = ToggleChipDefaults.switchIcon(uiState.isAutoRotationEnabled),
                                contentDescription = null
                            )
                        },
                        colors = ToggleChipDefaults.toggleChipColors()
                    )
                }
            }
        }
    }
}
