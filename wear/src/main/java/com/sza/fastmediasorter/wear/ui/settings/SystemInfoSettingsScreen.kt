package com.sza.fastmediasorter.wear.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets

private val TITLE_BOTTOM_PADDING = 8.dp
private val SECTION_TOP_PADDING = 10.dp
private val ROW_HORIZONTAL_PADDING = 12.dp
private val ROW_VERTICAL_PADDING = 2.dp

/**
 * What the watch can say about itself, in the same shape the phone's report uses: a section title, then
 * name-value pairs. The pair is stacked rather than written on one line - a watch has no room for
 * "label: value" without truncating one half of it.
 */
@Composable
fun SystemInfoSettingsScreen(
    viewModel: SystemInfoViewModel = hiltViewModel(),
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
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.system_info_title),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = TITLE_BOTTOM_PADDING),
                    textAlign = TextAlign.Center
                )
            }
            if (uiState.loading) {
                item {
                    Text(
                        text = stringResource(R.string.system_info_loading),
                        style = MaterialTheme.typography.caption1,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
            uiState.sections.forEach { section ->
                item { SectionTitle(section.titleRes) }
                items(section.fields) { field -> SystemInfoRow(field) }
            }
        }
    }
}

@Composable
private fun SectionTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SECTION_TOP_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SystemInfoRow(field: WearSystemInfoField) {
    val label = stringResource(field.labelRes)
    val value = when (val fieldValue = field.value) {
        is WearSystemInfoValue.Text -> fieldValue.text
        is WearSystemInfoValue.Label -> stringResource(fieldValue.res)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = ROW_VERTICAL_PADDING)
            // One description for the pair: announced as two separate stops, the user has to hold the
            // label in mind while the reader moves on to a number that no longer names itself.
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}
