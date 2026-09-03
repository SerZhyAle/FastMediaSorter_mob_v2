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
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearSettingsItem
import com.sza.fastmediasorter.wear.ui.common.WearSettingsRow
import com.sza.fastmediasorter.wear.ui.common.WearSettingsToggleCell
import com.sza.fastmediasorter.wear.ui.common.packSettingsRows
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.util.GridColumnFit

private val TITLE_BOTTOM_PADDING = 8.dp
private val ROW_SPACING = 4.dp

@Composable
fun ScreenSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberScalingLazyListState()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayModeLabel = stringResource(R.string.screen_settings_view_mode)
    val fileListLabel = stringResource(R.string.screen_settings_file_list_view)

    // S1949: the three mode chips measure 6-12 characters in their worst locale, so they are narrow
    // and share a row. Each group is packed on its own, so a run never spans two settings: on a
    // display narrow enough to drop to two columns, the keep-awake toggle would otherwise pair with
    // a leftover mode chip and read as part of that group.
    val displayModeItems = viewModeItems(displayModeLabel, uiState.viewMode, viewModel::setViewMode)
    val fileListItems =
        viewModeItems(fileListLabel, uiState.fileListViewMode, viewModel::setFileListViewMode)
    // S2093 / ADR-3: the mode is two values and so is editable from both sides; the picture it points
    // at stays a phone choice, because choosing one means opening a gallery.
    val backgroundLabel = stringResource(R.string.wear_setting_background_mode)
    val backgroundItems = WearBackgroundMode.entries.map { mode ->
        WearSettingsItem { narrow ->
            BackgroundModeRow(
                mode = mode,
                groupLabel = backgroundLabel,
                narrow = narrow,
                selected = uiState.backgroundMode == mode,
                onSelect = { viewModel.setBackgroundMode(mode) }
            )
        }
    }
    val keepAwakeLabel = stringResource(R.string.screen_settings_keep_awake)
    val keepAwakeItems = listOf(
        WearSettingsItem { narrow ->
            WearSettingsToggleCell(
                label = keepAwakeLabel,
                checked = uiState.keepScreenAwakeOutsidePlayers,
                narrow = narrow,
                onToggle = { viewModel.toggleKeepScreenAwakeOutsidePlayers() }
            )
        }
    )

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(WearViewMode.GRID_3, maxWidth.value.toInt())
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = wearScreenInsets(),
                verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
                scalingParams = WearGridScalingParams
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
                item { GroupCaption(text = displayModeLabel) }
                items(packSettingsRows(displayModeItems, columns)) { row -> WearSettingsRow(row) }
                item { GroupCaption(text = fileListLabel) }
                items(packSettingsRows(fileListItems, columns)) { row -> WearSettingsRow(row) }
                item { GroupCaption(text = backgroundLabel) }
                items(packSettingsRows(backgroundItems, columns)) { row -> WearSettingsRow(row) }
                items(packSettingsRows(keepAwakeItems, columns)) { row -> WearSettingsRow(row) }
            }
        }
    }
}

private fun viewModeItems(
    groupLabel: String,
    selectedMode: WearViewMode,
    onSelect: (WearViewMode) -> Unit
): List<WearSettingsItem> = WearViewMode.entries.map { mode ->
    WearSettingsItem { narrow ->
        ViewModeRow(
            mode = mode,
            groupLabel = groupLabel,
            narrow = narrow,
            selected = selectedMode == mode,
            onSelect = { onSelect(mode) }
        )
    }
}

@Composable
private fun GroupCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption1,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ViewModeRow(
    mode: WearViewMode,
    groupLabel: String,
    narrow: Boolean,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val label = stringResource(labelResFor(mode))
    WearSettingsToggleCell(
        label = label,
        checked = selected,
        narrow = narrow,
        // A radio row reports the choice it makes, so re-tapping the active mode is a no-op rather
        // than a way to end up with no view mode at all.
        onToggle = { if (!selected) onSelect() },
        radio = true,
        // Both groups offer the same three mode names, so the row is read out with the setting it
        // belongs to - otherwise the two settings are indistinguishable to a screen reader, which is
        // exactly what strategic §6 item 1 forbids.
        accessibilityLabel = "$groupLabel: $label"
    )
}

/**
 * S2093: the two background modes as a radio pair, in the same shape as the view-mode rows above.
 *
 * Picking the image mode with no picture delivered is allowed and needs no new fallback: the existing
 * renderer already falls back to the branded animation when no frame has arrived.
 */
@Composable
private fun BackgroundModeRow(
    mode: WearBackgroundMode,
    groupLabel: String,
    narrow: Boolean,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val label = stringResource(backgroundLabelResFor(mode))
    WearSettingsToggleCell(
        label = label,
        checked = selected,
        narrow = narrow,
        onToggle = { if (!selected) onSelect() },
        radio = true,
        accessibilityLabel = "$groupLabel: $label"
    )
}

private fun backgroundLabelResFor(mode: WearBackgroundMode): Int = when (mode) {
    WearBackgroundMode.BRANDED_ANIMATION -> R.string.wear_background_mode_animation
    WearBackgroundMode.IMAGE -> R.string.wear_background_mode_image
}

private fun labelResFor(mode: WearViewMode): Int = when (mode) {
    WearViewMode.LIST -> R.string.wear_view_mode_list
    WearViewMode.GRID_2 -> R.string.wear_view_mode_grid2
    WearViewMode.GRID_3 -> R.string.wear_view_mode_grid3
}
