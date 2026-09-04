package com.sza.fastmediasorter.wear.ui.apps.systeminfo

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.ui.common.WearInformationRow
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearSettingsItem
import com.sza.fastmediasorter.wear.ui.common.WearSettingsRow
import com.sza.fastmediasorter.wear.ui.common.packSettingsRows
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import timber.log.Timber

private val TITLE_BOTTOM_PADDING = 8.dp
private val SECTION_TOP_PADDING = 10.dp
private val ROW_VERTICAL_PADDING = 2.dp

/**
 * What the watch can say about itself, in the same shape the phone's report uses: a section title, then
 * name-value pairs. The pair is stacked rather than written on one line - a watch has no room for
 * "label: value" without truncating one half of it.
 *
 * Reached from Applications rather than from Settings (S2008): it configures nothing, it reports what
 * this watch is, which is what the Applications section holds.
 */
@Composable
fun SystemInfoScreen(
    viewModel: SystemInfoViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(initialItemIndex = 1)
) {
    Timber.d("S2470: system information compact pairs shown")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Hoisted out of the row rather than remembered inside it: a ScalingLazyColumn recycles the
    // composition of a row scrolled off the screen, so state kept in the row would silently collapse a
    // list the user had opened as soon as they scrolled past it.
    val expanded = remember { mutableStateMapOf<Int, Boolean>() }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
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
            } else {
                item {
                    RefreshChip(enabled = !uiState.refreshing, onClick = viewModel::refresh)
                }
            }
            items(packSettingsRows(reportItems(uiState.sections, expanded), 1)) { row ->
                WearSettingsRow(row)
            }
        }
    }
}

/**
 * A button rather than a pull-to-refresh gesture: wear-compose is pinned at 1.2.1 here, which ships no
 * pull-to-refresh, and the vertical drag on this screen already belongs to the report's own scroll.
 */
@Composable
private fun RefreshChip(enabled: Boolean, onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(stringResource(R.string.system_info_refresh)) },
        icon = {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
    )
}

/**
 * The report as one flat list, where a section title is what breaks the packing.
 *
 * `packSettingsRows` flushes its current run at every full-width item, so marking each title
 * full-width groups the fields under it without the packer learning what a section is (S2008).
 */
private fun reportItems(
    sections: List<WearSystemInfoSection>,
    expanded: MutableMap<Int, Boolean>
): List<WearSettingsItem> = buildList {
    sections.forEach { section ->
        add(WearSettingsItem(fullWidth = true) { SectionTitle(section.titleRes) })
        val emptyReasonRes = section.emptyReasonRes
        if (section.fields.isEmpty() && emptyReasonRes != null) {
            add(WearSettingsItem(fullWidth = true) { SectionEmptyReason(emptyReasonRes) })
        }
        section.fields.forEach { field ->
            val enumerated = field.value as? WearSystemInfoValue.Enumerated
            if (enumerated == null) {
                add(WearSettingsItem(fullWidth = true) { SystemInfoRow(field) })
            } else {
                add(
                    WearSettingsItem(fullWidth = true) {
                        EnumeratedRow(
                            labelRes = field.labelRes,
                            entries = enumerated.entries,
                            open = expanded[field.labelRes] == true,
                            onToggle = { expanded[field.labelRes] = expanded[field.labelRes] != true }
                        )
                    }
                )
            }
        }
    }
}

/**
 * A set shown as its size, opening into the list on tap.
 *
 * The collapsed value is the bare count: the label beside it already names what is counted, so no
 * plural form and no format string is needed in any of the thirteen declared locales.
 */
@Composable
private fun EnumeratedRow(
    @StringRes labelRes: Int,
    entries: List<String>,
    open: Boolean,
    onToggle: () -> Unit
) {
    val hint = stringResource(
        if (open) R.string.system_info_collapse_hint else R.string.system_info_expand_hint
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        WearInformationRow(
            labelRes = labelRes,
            value = entries.size.toString(),
            onClick = onToggle,
            accessibilitySuffix = hint
        )
        if (open) {
            entries.forEach { entry ->
                Text(
                    text = entry,
                    style = MaterialTheme.typography.caption3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ROW_VERTICAL_PADDING),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * A section this watch cannot fill says why, instead of leaving the report a different length on every
 * device (S2165, on the form S2156 settled and S2130 established here before it).
 */
@Composable
private fun SectionEmptyReason(@StringRes reasonRes: Int) {
    Text(
        text = stringResource(reasonRes),
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ROW_VERTICAL_PADDING),
        textAlign = TextAlign.Center
    )
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
private fun valueOf(field: WearSystemInfoField): String = when (val fieldValue = field.value) {
    is WearSystemInfoValue.Text -> fieldValue.text
    is WearSystemInfoValue.Label -> stringResource(fieldValue.res)
    // Never reached: an Enumerated field is routed to EnumeratedRow before this point. The branch
    // exists so that a future value shape fails to compile here instead of falling through silently.
    is WearSystemInfoValue.Enumerated -> fieldValue.entries.size.toString()
}

@Composable
private fun SystemInfoRow(field: WearSystemInfoField) =
    WearInformationRow(labelRes = field.labelRes, value = valueOf(field))
