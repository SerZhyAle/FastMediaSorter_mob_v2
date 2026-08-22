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
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets

private val TITLE_BOTTOM_PADDING = 8.dp
private val ROW_SPACING = 4.dp

@Composable
fun ScreenSettingsScreen(
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
                    text = stringResource(R.string.screen_settings_title),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = TITLE_BOTTOM_PADDING),
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = stringResource(R.string.screen_settings_view_mode),
                    style = MaterialTheme.typography.caption1,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            items(WearViewMode.entries.size) { index ->
                val mode = WearViewMode.entries[index]
                ViewModeRow(
                    mode = mode,
                    groupLabel = stringResource(R.string.screen_settings_view_mode),
                    selected = uiState.viewMode == mode,
                    onSelect = { viewModel.setViewMode(mode) }
                )
            }
            item {
                Text(
                    text = stringResource(R.string.screen_settings_file_list_view),
                    style = MaterialTheme.typography.caption1,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            items(WearViewMode.entries.size) { index ->
                val mode = WearViewMode.entries[index]
                ViewModeRow(
                    mode = mode,
                    groupLabel = stringResource(R.string.screen_settings_file_list_view),
                    selected = uiState.fileListViewMode == mode,
                    onSelect = { viewModel.setFileListViewMode(mode) }
                )
            }
            item {
                KeepAwakeRow(
                    checked = uiState.keepScreenAwakeOutsidePlayers,
                    onToggle = { viewModel.toggleKeepScreenAwakeOutsidePlayers() }
                )
            }
        }
    }
}

@Composable
private fun KeepAwakeRow(
    checked: Boolean,
    onToggle: () -> Unit
) {
    val label = stringResource(R.string.screen_settings_keep_awake)
    ToggleChip(
        checked = checked,
        onCheckedChange = { onToggle() },
        label = { Text(text = label) },
        toggleControl = {
            Icon(
                imageVector = ToggleChipDefaults.switchIcon(checked),
                contentDescription = label
            )
        },
        colors = ToggleChipDefaults.toggleChipColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ViewModeRow(
    mode: WearViewMode,
    groupLabel: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val label = stringResource(labelResFor(mode))
    ToggleChip(
        checked = selected,
        // A radio row reports the choice it makes, so re-tapping the active mode is a no-op rather
        // than a way to end up with no view mode at all.
        onCheckedChange = { if (!selected) onSelect() },
        label = { Text(text = label) },
        toggleControl = {
            Icon(
                imageVector = ToggleChipDefaults.radioIcon(selected),
                // Both groups offer the same three mode names, so the row is read out with the
                // setting it belongs to - otherwise the two settings are indistinguishable to a
                // screen reader, which is exactly what strategic §6 item 1 forbids.
                contentDescription = "$groupLabel: $label"
            )
        },
        colors = ToggleChipDefaults.toggleChipColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun labelResFor(mode: WearViewMode): Int = when (mode) {
    WearViewMode.LIST -> R.string.wear_view_mode_list
    WearViewMode.GRID_2 -> R.string.wear_view_mode_grid2
    WearViewMode.GRID_3 -> R.string.wear_view_mode_grid3
}
