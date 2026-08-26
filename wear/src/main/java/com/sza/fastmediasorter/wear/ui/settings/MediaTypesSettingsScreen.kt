package com.sza.fastmediasorter.wear.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearSettingsItem
import com.sza.fastmediasorter.wear.ui.common.WearSettingsRow
import com.sza.fastmediasorter.wear.ui.common.WearSettingsToggleCell
import com.sza.fastmediasorter.wear.ui.common.packSettingsRows
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.util.GridColumnFit

private val ROW_SPACING = 4.dp
private val TITLE_BOTTOM_PADDING = 8.dp

@Composable
fun MediaTypesSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberScalingLazyListState()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // S1949: every label on this screen measures 17-21 characters in its worst locale - under the
    // 32-character threshold - so all four toggles share rows instead of each taking the full width.
    val toggles = listOf(
        mediaTypeItem(
            checked = uiState.isAudioEnabled,
            label = stringResource(R.string.enable_audio),
            onToggle = viewModel::toggleAudio
        ),
        mediaTypeItem(
            checked = uiState.isVideoEnabled,
            label = stringResource(R.string.enable_video),
            onToggle = viewModel::toggleVideo
        ),
        mediaTypeItem(
            checked = uiState.isImagesEnabled,
            label = stringResource(R.string.enable_images),
            onToggle = viewModel::toggleImages
        ),
        mediaTypeItem(
            checked = uiState.streamsSectionEnabled,
            label = stringResource(R.string.wear_streams_section_enabled),
            onToggle = viewModel::toggleStreamsSection
        )
    )

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(WearViewMode.GRID_2, maxWidth.value.toInt())
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
                items(packSettingsRows(toggles, columns)) { row ->
                    WearSettingsRow(row)
                }
            }
        }
    }
}

private fun mediaTypeItem(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit
): WearSettingsItem = WearSettingsItem { narrow ->
    WearSettingsToggleCell(
        label = label,
        checked = checked,
        narrow = narrow,
        onToggle = onToggle
    )
}
