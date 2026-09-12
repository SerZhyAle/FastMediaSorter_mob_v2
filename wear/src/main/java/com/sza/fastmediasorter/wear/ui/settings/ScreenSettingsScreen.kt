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
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode
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
    // S2522 / S3023: color scheme options laid out in 2 columns.
    val colorSchemeLabel = stringResource(R.string.wear_setting_color_scheme)
    val colorSchemeItems = WearColorScheme.entries.map { scheme ->
        WearSettingsItem { narrow ->
            ColorSchemeRow(
                scheme = scheme,
                groupLabel = colorSchemeLabel,
                selected = uiState.colorScheme == scheme,
                onSelect = { viewModel.setColorScheme(scheme) },
                narrow = narrow
            )
        }
    }
    val geometryLabel = stringResource(R.string.wear_setting_original_layout)
    val geometryItems = geometryModeItems(uiState, viewModel)
    val keepAwakeItems = keepAwakeItem(uiState, viewModel)

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(WearViewMode.GRID_3, maxWidth.value.toInt())
            val colorSchemeColumns = GridColumnFit.columnsFor(WearViewMode.GRID_2, maxWidth.value.toInt())
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
                items(packSettingsRows(keepAwakeItems, columns)) { row -> WearSettingsRow(row) }
                item { GroupCaption(text = displayModeLabel) }
                items(packSettingsRows(displayModeItems, columns)) { row -> WearSettingsRow(row) }
                item { GroupCaption(text = fileListLabel) }
                items(packSettingsRows(fileListItems, columns)) { row -> WearSettingsRow(row) }
                item { GroupCaption(text = backgroundLabel) }
                items(packSettingsRows(backgroundItems, columns)) { row -> WearSettingsRow(row) }
                item { GroupCaption(text = colorSchemeLabel) }
                items(packSettingsRows(colorSchemeItems, colorSchemeColumns)) { row -> WearSettingsRow(row) }
                if (geometryItems.isNotEmpty()) {
                    item { GroupCaption(text = geometryLabel) }
                    items(packSettingsRows(geometryItems, columns)) { row -> WearSettingsRow(row) }
                }
            }
        }
    }
}

/**
 * S2773 / ADR-3: the geometry row, built only where the build variant allows the view to be changed.
 *
 * In the published variant the list is empty, so the caller draws neither the row nor its caption and
 * the group leaves no gap behind. Its own function rather than a block in the screen because the screen
 * already sits at detekt's length ceiling, and because the emptiness IS the store-variant behaviour and
 * deserves to be stated somewhere it can be read.
 */
@Composable
private fun geometryModeItems(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
): List<WearSettingsItem> {
    if (!uiState.offersGeometryModeSwitch) {
        return emptyList()
    }
    val summary = stringResource(R.string.wear_setting_original_layout_summary)
    return listOf(
        WearSettingsItem(fullWidth = true) { _ ->
            WearSettingsToggleCell(
                label = summary,
                checked = uiState.geometryMode == WearGeometryMode.ORIGINAL,
                onToggle = { viewModel.toggleGeometryMode() }
            )
        }
    )
}

/**
 * The keep-awake toggle, lifted out of [ScreenSettingsScreen] so the screen stays under detekt's
 * length ceiling. A single-item run today, but the `narrow` flag is passed through so a future
 * sibling in the same row would not break mid-word (S2986).
 */
@Composable
private fun keepAwakeItem(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
): List<WearSettingsItem> {
    val keepAwakeLabel = stringResource(R.string.screen_settings_keep_awake)
    return listOf(
        WearSettingsItem { narrow ->
            WearSettingsToggleCell(
                label = keepAwakeLabel,
                checked = uiState.keepScreenAwakeOutsidePlayers,
                onToggle = { viewModel.toggleKeepScreenAwakeOutsidePlayers() },
                narrow = narrow
            )
        }
    )
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
    onSelect: () -> Unit,
    narrow: Boolean = false
) {
    val label = stringResource(colorSchemeLabelResFor(scheme))
    WearSettingsToggleCell(
        label = label,
        checked = selected,
        onToggle = { if (!selected) onSelect() },
        radio = true,
        accessibilityLabel = "$groupLabel: $label",
        narrow = narrow
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
