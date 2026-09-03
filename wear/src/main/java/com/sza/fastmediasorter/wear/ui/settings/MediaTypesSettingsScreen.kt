package com.sza.fastmediasorter.wear.ui.settings

import androidx.annotation.StringRes
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
import com.sza.fastmediasorter.wear.domain.browse.BrowseCategoryCatalog
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
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
    // 32-character threshold - so the toggles share rows instead of each taking the full width.
    // Built once rather than per row: asking the state per type would rebuild the whole allowed set
    // on every cell of every recomposition.
    val allowed = uiState.allowedContentTypes()
    val typeToggles = BrowseCategoryCatalog.DISABLEABLE_TYPES.map { type ->
        mediaTypeItem(
            checked = type in allowed,
            label = stringResource(settingsLabelFor(type)),
            onToggle = { viewModel.toggleType(type) }
        )
    }
    val sectionToggles = listOf(
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
                verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
                scalingParams = WearGridScalingParams
            ) {
                item { SettingsHeading(R.string.media_types) }
                items(packSettingsRows(typeToggles, columns)) { row ->
                    WearSettingsRow(row)
                }
                item { SettingsHeading(R.string.wear_settings_sections) }
                items(packSettingsRows(sectionToggles, columns)) { row ->
                    WearSettingsRow(row)
                }
            }
        }
    }
}

@Composable
private fun SettingsHeading(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.title2,
        modifier = Modifier.fillMaxWidth().padding(bottom = TITLE_BOTTOM_PADDING),
        textAlign = TextAlign.Center
    )
}

/**
 * The wording of a type's own switch, which is not the wording of its category chip.
 *
 * S2130 kept the two apart deliberately: a chip is named after the thing ("Images") and a switch after
 * the act of allowing it ("Enable Images"). The `else` branch is unreachable while this table covers
 * [BrowseCategoryCatalog.DISABLEABLE_TYPES], which `MediaTypesSettingsLabelTest` is what keeps true.
 */
@StringRes
internal fun settingsLabelFor(type: WearContentType): Int = when (type) {
    WearContentType.MUSIC -> R.string.enable_audio
    WearContentType.VIDEO -> R.string.enable_video
    WearContentType.IMAGE -> R.string.enable_images
    else -> R.string.enable_documents
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
