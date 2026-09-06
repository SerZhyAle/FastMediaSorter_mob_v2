package com.sza.fastmediasorter.wear.ui.settings

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
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearColorScheme
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearSettingsItem
import com.sza.fastmediasorter.wear.ui.common.WearSettingsRow
import com.sza.fastmediasorter.wear.ui.common.WearSettingsToggleCell
import com.sza.fastmediasorter.wear.ui.common.packSettingsRows
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.util.GridColumnFit

private val TITLE_BOTTOM_PADDING = 8.dp

@Composable
fun ScreenSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = SettingsRoutes.SCREEN)
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
        WearSettingsItem(fullWidth = true) { _ ->
            BackgroundModeRow(
                mode = mode,
                groupLabel = backgroundLabel,
                selected = uiState.backgroundMode == mode,
                onSelect = { viewModel.setBackgroundMode(mode) }
            )
        }
    }
    // S2522: the same shape as the background group above - mutually exclusive options, each named in
    // words rather than shown only as a swatch, so the choice reaches a screen reader too.
    val colorSchemeLabel = stringResource(R.string.wear_setting_color_scheme)
    val colorSchemeItems = WearColorScheme.entries.map { scheme ->
        WearSettingsItem(fullWidth = true) { _ ->
            ColorSchemeRow(
                scheme = scheme,
                groupLabel = colorSchemeLabel,
                selected = uiState.colorScheme == scheme,
                onSelect = { viewModel.setColorScheme(scheme) }
            )
        }
    }
    val keepAwakeLabel = stringResource(R.string.screen_settings_keep_awake)
    val keepAwakeItems = listOf(
        WearSettingsItem { _ ->
            WearSettingsToggleCell(
                label = keepAwakeLabel,
                checked = uiState.keepScreenAwakeOutsidePlayers,
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
            WearListColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
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
                item { GroupCaption(text = colorSchemeLabel) }
                items(packSettingsRows(colorSchemeItems, columns)) { row -> WearSettingsRow(row) }
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
    WearSettingsItem { _ ->
        ViewModeRow(
            mode = mode,
            groupLabel = groupLabel,
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
    selected: Boolean,
    onSelect: () -> Unit
) {
    val label = stringResource(labelResFor(mode))
    WearSettingsToggleCell(
        label = label,
        checked = selected,
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
    selected: Boolean,
    onSelect: () -> Unit
) {
    val label = stringResource(backgroundLabelResFor(mode))
    WearSettingsToggleCell(
        label = label,
        checked = selected,
        onToggle = { if (!selected) onSelect() },
        radio = true,
        accessibilityLabel = "$groupLabel: $label"
    )
}

/**
 * S2522: one scheme as a radio row, in the same shape as the background rows above.
 *
 * The row is named rather than swatched: strategic 3.2 requires the option to be readable as a word,
 * without which the choice is unavailable to a screen reader and to an owner who does not tell the
 * hues apart.
 */
@Composable
private fun ColorSchemeRow(
    scheme: WearColorScheme,
    groupLabel: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val label = stringResource(colorSchemeLabelResFor(scheme))
    WearSettingsToggleCell(
        label = label,
        checked = selected,
        onToggle = { if (!selected) onSelect() },
        radio = true,
        accessibilityLabel = "$groupLabel: $label"
    )
}

private fun colorSchemeLabelResFor(scheme: WearColorScheme): Int = when (scheme) {
    WearColorScheme.DARK -> R.string.wear_color_scheme_dark
    WearColorScheme.LIGHT -> R.string.wear_color_scheme_light
    WearColorScheme.DARK_GREEN -> R.string.wear_color_scheme_dark_green
    WearColorScheme.DARK_BLUE -> R.string.wear_color_scheme_dark_blue
    WearColorScheme.DARK_RED -> R.string.wear_color_scheme_dark_red
    WearColorScheme.LIGHT_GREEN -> R.string.wear_color_scheme_light_green
    WearColorScheme.LIGHT_BLUE -> R.string.wear_color_scheme_light_blue
    WearColorScheme.LIGHT_RED -> R.string.wear_color_scheme_light_red
}

private fun backgroundLabelResFor(mode: WearBackgroundMode): Int = when (mode) {
    WearBackgroundMode.BRANDED_ANIMATION -> R.string.wear_background_mode_animation
    WearBackgroundMode.BRANDED_STILL -> R.string.wear_background_mode_still
    WearBackgroundMode.IMAGE -> R.string.wear_background_mode_image
    WearBackgroundMode.NONE -> R.string.wear_background_mode_none
}

private fun labelResFor(mode: WearViewMode): Int = when (mode) {
    WearViewMode.LIST -> R.string.wear_view_mode_list
    WearViewMode.GRID_2 -> R.string.wear_view_mode_grid2
    WearViewMode.GRID_3 -> R.string.wear_view_mode_grid3
}
