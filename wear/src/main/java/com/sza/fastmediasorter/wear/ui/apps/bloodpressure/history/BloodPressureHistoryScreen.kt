package com.sza.fastmediasorter.wear.ui.apps.bloodpressure.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCategory
import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.BloodPressureSummary
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import timber.log.Timber

private val TITLE_BOTTOM_PADDING = 6.dp
private val SECTION_VERTICAL_PADDING = 4.dp
private val CARD_CORNER_RADIUS = 8.dp
private const val SUMMARY_BADGE_ALPHA = 0.25f
private const val HISTORY_BADGE_ALPHA = 0.2f

/**
 * S3012: The blood pressure history and analytics screen.
 *
 * Displays summary statistics (average, min, max), visual trend chart for recent readings,
 * and structured history cards with category badges.
 */
@Composable
fun BloodPressureHistoryScreen(
    viewModel: BloodPressureHistoryViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()


    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            centered = true
        ) {
            item { ScreenTitle() }

            if (state.isEmpty) {
                item { EmptyMessage() }
            } else {
                state.summary?.let { summary ->
                    item { AnalyticsSummaryCard(summary) }
                    item { SectionHeader(stringResource(R.string.blood_pressure_trend_title)) }
                    item { BloodPressureTrendChart(entries = state.entries) }
                    item { BaselineLegend() }
                    item { SectionHeader(stringResource(R.string.blood_pressure_history_title)) }
                }

                items(state.entries) { entry ->
                    BloodPressureHistoryCard(entry)
                }

                item { ClearChip(onClick = { viewModel.clearHistory() }) }
            }
        }
    }
}

@Composable
private fun ScreenTitle() {
    Text(
        text = stringResource(R.string.blood_pressure_analytics_title),
        style = MaterialTheme.typography.title2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = TITLE_BOTTOM_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.caption2.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun BaselineLegend() {
    Text(
        text = stringResource(R.string.blood_pressure_normal_baseline),
        style = MaterialTheme.typography.caption3,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun EmptyMessage() {
    Text(
        text = stringResource(R.string.blood_pressure_history_empty),
        style = MaterialTheme.typography.body1,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun AnalyticsSummaryCard(summary: BloodPressureSummary) {
    val avgLabel = stringResource(R.string.blood_pressure_stat_avg)
    val minLabel = stringResource(R.string.blood_pressure_stat_min)
    val maxLabel = stringResource(R.string.blood_pressure_stat_max)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = SECTION_VERTICAL_PADDING)
            .clip(RoundedCornerShape(CARD_CORNER_RADIUS))
            .background(MaterialTheme.colors.surface)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.blood_pressure_total_readings, summary.count),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant
        )

        Row(
            modifier = Modifier.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$avgLabel: ${summary.avgSystolic}/${summary.avgDiastolic}",
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colors.onSurface
            )
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(summary.avgCategory.color.copy(alpha = SUMMARY_BADGE_ALPHA))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(summary.avgCategory.labelRes),
                    style = MaterialTheme.typography.caption3.copy(fontWeight = FontWeight.Bold),
                    color = summary.avgCategory.color
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "$minLabel: ${summary.minSystolic}/${summary.minDiastolic}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
            Text(
                text = "$maxLabel: ${summary.maxSystolic}/${summary.maxDiastolic}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BloodPressureHistoryCard(entry: BloodPressureHistoryEntry) {
    val dateTime = formatTimestamp(entry.timestampMillis)
    val category = BloodPressureCategory.classify(entry.systolic, entry.diastolic)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(CARD_CORNER_RADIUS))
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateTime,
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.blood_pressure_reading_format, entry.systolic, entry.diastolic),
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colors.onSurface
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(category.color.copy(alpha = HISTORY_BADGE_ALPHA))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(category.labelRes),
                style = MaterialTheme.typography.caption3.copy(fontWeight = FontWeight.Bold),
                color = category.color
            )
        }
    }
}

@Composable
private fun ClearChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.blood_pressure_history_clear)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = ChipDefaults.secondaryChipColors()
    )
}

/**
 * Formats timestamp with standard Wear date time formatter.
 */
@Composable
private fun formatTimestamp(timestampMillis: Long): String =
    LocalWearDateTimeFormatter.current.formatDateTime(timestampMillis, LocalWearUnitSystem.current)
