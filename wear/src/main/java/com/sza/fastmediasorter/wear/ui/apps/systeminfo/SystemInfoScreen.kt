package com.sza.fastmediasorter.wear.ui.apps.systeminfo

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.ui.common.LocalWearSectionExpansion
import com.sza.fastmediasorter.wear.ui.common.WearInformationRow
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearReportDivider
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearSectionExpansionStore
import com.sza.fastmediasorter.wear.ui.common.WearSettingsItem
import com.sza.fastmediasorter.wear.ui.common.WearSettingsRow
import com.sza.fastmediasorter.wear.ui.common.packSettingsRows
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import timber.log.Timber

/**
 * The key this screen's list position and its open sections are remembered under (S2543, S2806).
 *
 * The enumerated fields inside a section take a key of their own: both halves are addressed by a string
 * resource id, and one shared namespace would let a field label that happens to equal a section title
 * open the wrong half.
 */
private const val SYSTEM_INFO_SCREEN_KEY = "apps/systeminfo"
private const val SYSTEM_INFO_FIELDS_KEY = "apps/systeminfo/fields"

private val TITLE_BOTTOM_PADDING = 8.dp
private val SECTION_TOP_PADDING = 14.dp
private val SECTION_TITLE_BOTTOM_PADDING = 10.dp
private val ROW_VERTICAL_PADDING = 4.dp

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
    listState: ScalingLazyListState = rememberWearListState(positionKey = SYSTEM_INFO_SCREEN_KEY)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Kept outside the composition rather than in it: a ScalingLazyColumn recycles the composition of a
    // row scrolled off the screen and navigation destroys the screen outright, so state held in either
    // place would collapse a group the user had opened. A local store stands in where no memory is
    // provided - previews and unit tests - so the screen still works, it just forgets on exit.
    val expansion = LocalWearSectionExpansion.current ?: remember { WearSectionExpansionStore() }
    // Built here, in the screen's own recompose scope, rather than inside the list content lambda: the
    // expansion reads must invalidate something that rebuilds the whole item list, and a lazy list's
    // content lambda is not that scope.
    val rows = packSettingsRows(reportItems(uiState.sections, expansion), 1)
    Timber.d("S2806: system information built %d rows from %d sections", rows.size, uiState.sections.size)

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = stringResource(R.string.system_info_loading),
                            style = MaterialTheme.typography.caption1,
                            modifier = Modifier.padding(top = ROW_VERTICAL_PADDING),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                item {
                    RefreshChip(enabled = !uiState.refreshing, onClick = viewModel::refresh)
                }
            }
            items(rows) { row ->
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
 *
 * A collapsed section contributes its title and nothing else (S2806). The fields are left OUT of the
 * list rather than hidden by a modifier: a ScalingLazyColumn counts items, so a hidden row would still
 * be one crown notch to scroll past, which is the very cost this screen was reported for.
 */
private fun reportItems(
    sections: List<WearSystemInfoSection>,
    expansion: WearSectionExpansionStore
): List<WearSettingsItem> = buildList {
    sections.forEachIndexed { index, section ->
        if (index > 0) {
            add(WearSettingsItem(fullWidth = true) { WearReportDivider() })
        }
        val open = expansion.isExpanded(SYSTEM_INFO_SCREEN_KEY, section.titleRes)
        add(
            WearSettingsItem(fullWidth = true) {
                SectionTitle(
                    titleRes = section.titleRes,
                    hiddenCount = if (open) null else section.fields.size,
                    open = open,
                    onToggle = {
                        Timber.d("S2806: section %d toggled, was open=%b", section.titleRes, open)
                        expansion.toggle(SYSTEM_INFO_SCREEN_KEY, section.titleRes)
                    }
                )
            }
        )
        if (!open) {
            return@forEachIndexed
        }
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
                            field = field,
                            entries = enumerated.entries,
                            open = expansion.isExpanded(SYSTEM_INFO_FIELDS_KEY, field.labelRes),
                            onToggle = { expansion.toggle(SYSTEM_INFO_FIELDS_KEY, field.labelRes) }
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
    field: WearSystemInfoField,
    entries: List<String>,
    open: Boolean,
    onToggle: () -> Unit
) {
    val hint = stringResource(
        if (open) R.string.system_info_collapse_hint else R.string.system_info_expand_hint
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        WearInformationRow(
            labelRes = field.labelRes,
            value = entries.size.toString(),
            onClick = onToggle,
            accessibilitySuffix = hint,
            accentColor = accentColor(field)
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

/**
 * The section heading, which is also the control that opens and closes the section (S2806).
 *
 * A closed section carries the number of lines it is holding back: without it the report reads as a
 * list of empty headings and gives no reason to open any particular one. The count is written as a
 * bare number in brackets, so the thirteen declared locales need no new string for it.
 *
 * The clickable is applied before the padding so the padding is part of the touch target - a heading
 * is one line of caption text, which on its own is well under a comfortable target on a watch.
 */
@Composable
private fun SectionTitle(titleRes: Int, hiddenCount: Int?, open: Boolean, onToggle: () -> Unit) {
    val title = stringResource(titleRes)
    val hint = stringResource(
        if (open) R.string.system_info_collapse_hint else R.string.system_info_expand_hint
    )
    Text(
        text = if (hiddenCount == null) title else "$title ($hiddenCount)",
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = SECTION_TOP_PADDING, bottom = SECTION_TITLE_BOTTOM_PADDING)
            .semantics { contentDescription = "$title. $hint" },
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
private fun accentColor(field: WearSystemInfoField): Color? =
    if (field.accentHint) MaterialTheme.colors.error else null

@Composable
private fun SystemInfoRow(field: WearSystemInfoField) =
    WearInformationRow(
        labelRes = field.labelRes,
        value = valueOf(field),
        accentColor = accentColor(field)
    )
