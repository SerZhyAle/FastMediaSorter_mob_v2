package com.sza.fastmediasorter.wear.ui.apps.bodysensor.history

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
import com.sza.fastmediasorter.wear.domain.model.HeartRateHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.HeartRateSummary
import com.sza.fastmediasorter.wear.domain.model.HeartRateZone
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes

private val TITLE_BOTTOM_PADDING = 6.dp
private val SECTION_VERTICAL_PADDING = 4.dp
private val CARD_CORNER_RADIUS = 8.dp
private val BADGE_CORNER_RADIUS = 8.dp
private val CARD_HORIZONTAL_PADDING = 8.dp
private val CARD_VERTICAL_PADDING = 2.dp
private val CARD_INNER_HORIZONTAL_PADDING = 8.dp
private val CARD_INNER_VERTICAL_PADDING = 6.dp
private val BADGE_INNER_HORIZONTAL_PADDING = 6.dp
private val BADGE_INNER_VERTICAL_PADDING = 3.dp
private val CLEAR_CHIP_HORIZONTAL_PADDING = 8.dp
private val CLEAR_CHIP_VERTICAL_PADDING = 4.dp
private val EMPTY_MESSAGE_VERTICAL_PADDING = 16.dp

private const val SUMMARY_BADGE_ALPHA = 0.25f
private const val HISTORY_BADGE_ALPHA = 0.2f

/**
 * S2808/S3013: The heart rate measurement history and analytics screen.
 *
 * Displays summary statistics (average, min, max), visual trend chart for recent readings,
 * and structured history cards with physiological heart rate zone badges.
 */
@Composable
fun HeartRateHistoryScreen(
    viewModel: HeartRateHistoryViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = WearRoutes.HEART_RATE_HISTORY)
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
                    item { SectionHeader(stringResource(R.string.heart_rate_trend_title)) }
                    item { HeartRateTrendChart(entries = state.entries) }
                    item { BaselineLegend() }
                    item { SectionHeader(stringResource(R.string.body_sensor_history_title)) }
                }

                items(state.entries) { entry ->
                    HeartRateHistoryCard(entry)
                }

                item { ClearChip(onClick = { viewModel.clearHistory() }) }
            }
        }
    }
}

@Composable
private fun ScreenTitle() {
    Text(
        text = stringResource(R.string.heart_rate_analytics_title),
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
        text = stringResource(R.string.heart_rate_normal_baseline),
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
        text = stringResource(R.string.body_sensor_history_empty),
        style = MaterialTheme.typography.body1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EMPTY_MESSAGE_VERTICAL_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun AnalyticsSummaryCard(summary: HeartRateSummary) {
    val avgLabel = stringResource(R.string.heart_rate_stat_avg)
    val minLabel = stringResource(R.string.heart_rate_stat_min)
    val maxLabel = stringResource(R.string.heart_rate_stat_max)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CARD_HORIZONTAL_PADDING, vertical = SECTION_VERTICAL_PADDING)
            .clip(RoundedCornerShape(CARD_CORNER_RADIUS))
            .background(MaterialTheme.colors.surface)
            .padding(CARD_INNER_HORIZONTAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.heart_rate_total_readings, summary.count),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant
        )

        Row(
            modifier = Modifier.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$avgLabel: ${summary.avgBpm} bpm",
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colors.onSurface
            )
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(BADGE_CORNER_RADIUS))
                    .background(summary.avgZone.color.copy(alpha = SUMMARY_BADGE_ALPHA))
                    .padding(horizontal = BADGE_INNER_HORIZONTAL_PADDING, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(summary.avgZone.labelRes),
                    style = MaterialTheme.typography.caption3.copy(fontWeight = FontWeight.Bold),
                    color = summary.avgZone.color
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "$minLabel: ${summary.minBpm} bpm",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
            Text(
                text = "$maxLabel: ${summary.maxBpm} bpm",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeartRateHistoryCard(entry: HeartRateHistoryEntry) {
    val dateTime = formatTimestamp(entry.timestampMillis)
    val zone = HeartRateZone.classify(entry.bpm)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CARD_HORIZONTAL_PADDING, vertical = CARD_VERTICAL_PADDING)
            .clip(RoundedCornerShape(CARD_CORNER_RADIUS))
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = CARD_INNER_HORIZONTAL_PADDING, vertical = CARD_INNER_VERTICAL_PADDING),
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
                text = stringResource(R.string.body_sensor_bpm, entry.bpm),
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colors.onSurface
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(BADGE_CORNER_RADIUS))
                .background(zone.color.copy(alpha = HISTORY_BADGE_ALPHA))
                .padding(horizontal = BADGE_INNER_HORIZONTAL_PADDING, vertical = BADGE_INNER_VERTICAL_PADDING),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(zone.labelRes),
                style = MaterialTheme.typography.caption3.copy(fontWeight = FontWeight.Bold),
                color = zone.color
            )
        }
    }
}

@Composable
private fun ClearChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.body_sensor_history_clear)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CLEAR_CHIP_HORIZONTAL_PADDING, vertical = CLEAR_CHIP_VERTICAL_PADDING),
        colors = ChipDefaults.secondaryChipColors()
    )
}

/**
 * S2795: the measurement system decides the field order and the clock length here, not the locale's
 * short form - a reading is compared against the ones above it, so the order has to be the same one
 * the rest of the app shows.
 */
@Composable
private fun formatTimestamp(timestampMillis: Long): String =
    LocalWearDateTimeFormatter.current.formatDateTime(timestampMillis, LocalWearUnitSystem.current)
