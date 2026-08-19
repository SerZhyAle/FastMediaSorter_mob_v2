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
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets

private val ROW_SPACING = 4.dp
private val TITLE_BOTTOM_PADDING = 8.dp

@Composable
fun MediaTypesSettingsScreen(
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
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
        ) {
            item {
                Text(
                    text = stringResource(R.string.media_types),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier.fillMaxWidth().padding(bottom = TITLE_BOTTOM_PADDING),
                    textAlign = TextAlign.Center
                )
            }
            item {
                MediaTypeToggle(
                    checked = uiState.isAudioEnabled,
                    labelRes = R.string.enable_audio,
                    onToggle = viewModel::toggleAudio
                )
            }
            item {
                MediaTypeToggle(
                    checked = uiState.isVideoEnabled,
                    labelRes = R.string.enable_video,
                    onToggle = viewModel::toggleVideo
                )
            }
            item {
                MediaTypeToggle(
                    checked = uiState.isImagesEnabled,
                    labelRes = R.string.enable_images,
                    onToggle = viewModel::toggleImages
                )
            }
            item {
                MediaTypeToggle(
                    checked = uiState.streamsSectionEnabled,
                    labelRes = R.string.wear_streams_section_enabled,
                    onToggle = viewModel::toggleStreamsSection
                )
            }
        }
    }
}

@Composable
private fun MediaTypeToggle(
    checked: Boolean,
    labelRes: Int,
    onToggle: () -> Unit
) {
    ToggleChip(
        checked = checked,
        onCheckedChange = { onToggle() },
        label = { Text(stringResource(labelRes)) },
        toggleControl = {
            Icon(
                imageVector = ToggleChipDefaults.switchIcon(checked),
                contentDescription = null
            )
        },
        colors = ToggleChipDefaults.toggleChipColors()
    )
}
