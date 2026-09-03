package com.sza.fastmediasorter.wear.ui.apps.systeminfo

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearSettingsItem
import com.sza.fastmediasorter.wear.ui.common.WearSettingsRow
import com.sza.fastmediasorter.wear.ui.common.packSettingsRows
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.util.GridColumnFit

private val TITLE_BOTTOM_PADDING = 8.dp
private val SECTION_TOP_PADDING = 10.dp
private val ROW_HORIZONTAL_PADDING = 12.dp
private val ROW_NARROW_HORIZONTAL_PADDING = 2.dp
private val ROW_VERTICAL_PADDING = 2.dp

/**
 * Longest **value** that still reads in half a watch screen.
 *
 * The value decides and the label does not, because they fail differently: a label is fixed
 * vocabulary the author can see, and one that wraps to two lines is still read, while a value is
 * whatever the device answered - a model string or a build number - and one broken across lines
 * reads as two values. Measured on a 150 dp watch: at two columns a cell is about 70 dp, which holds
 * roughly fourteen `caption2` characters.
 *
 * The first attempt compared the longer of label and value against 12 and made almost every field
 * full-width, so the two-column layout never engaged at all on a real screen (device pass 2026-08-26,
 * where "Model", "Wear OS version" and "API level" each took a row of their own).
 */
private const val NARROW_VALUE_CHARS = 14

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
    listState: ScalingLazyListState = rememberScalingLazyListState()
) {
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(WearViewMode.GRID_2, maxWidth.value.toInt())
            val rows = packSettingsRows(reportItems(uiState.sections, expanded), columns)
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = wearScreenInsets(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                scalingParams = WearGridScalingParams
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
                items(rows) { row -> WearSettingsRow(row) }
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
        label = {
            Text(
                text = stringResource(R.string.system_info_refresh),
                style = MaterialTheme.typography.caption2,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = TITLE_BOTTOM_PADDING)
    )
}

/**
 * The report as one flat list, where a section title is what breaks the packing.
 *
 * `packSettingsRows` flushes its current run at every full-width item, so marking each title
 * full-width groups the fields under it without the packer learning what a section is (S2008).
 */
@Composable
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
                add(WearSettingsItem(fullWidth = isWide(field)) { narrow -> SystemInfoRow(field, narrow) })
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
    val label = stringResource(labelRes)
    val hint = stringResource(
        if (open) R.string.system_info_collapse_hint else R.string.system_info_expand_hint
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = ROW_VERTICAL_PADDING)
            // One description for the whole control, on the same reasoning as the plain pair below: a
            // reader that announced the count apart from its name would read out a number that no
            // longer says what it counts.
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: ${entries.size}. $hint"
            }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = entries.size.toString(),
            style = MaterialTheme.typography.body2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
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
            .padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = ROW_VERTICAL_PADDING),
        textAlign = TextAlign.Center
    )
}

/**
 * Whether a field needs a row to itself, decided from the value it actually resolved to.
 *
 * S1949 fixed this per control at authoring time, against the longest label across the locales, which
 * is right for a control whose label is the only text it holds. Half of a row here is read off the
 * device - a model number, an OS string, a byte count - so no author can measure it, and reading the
 * resolved value keeps the rule correct in every locale without a thirteen-file audit.
 */
@Composable
private fun isWide(field: WearSystemInfoField): Boolean = valueOf(field).length > NARROW_VALUE_CHARS

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

/**
 * @param narrow true when the pair is sharing its row. It is drawn a type step down and with almost no
 * horizontal padding, because at two columns the cell is about 70 dp and the full-width padding of
 * 12 dp a side would spend a third of it on margin.
 */
@Composable
private fun SystemInfoRow(field: WearSystemInfoField, narrow: Boolean = false) {
    val label = stringResource(field.labelRes)
    val value = valueOf(field)
    val sidePadding = if (narrow) ROW_NARROW_HORIZONTAL_PADDING else ROW_HORIZONTAL_PADDING
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePadding, vertical = ROW_VERTICAL_PADDING)
            // One description for the pair: announced as two separate stops, the user has to hold the
            // label in mind while the reader moves on to a number that no longer names itself.
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
    ) {
        Text(
            text = label,
            style = if (narrow) {
                MaterialTheme.typography.caption3
            } else {
                MaterialTheme.typography.caption2
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = value,
            style = if (narrow) {
                MaterialTheme.typography.caption2
            } else {
                MaterialTheme.typography.body2
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}
