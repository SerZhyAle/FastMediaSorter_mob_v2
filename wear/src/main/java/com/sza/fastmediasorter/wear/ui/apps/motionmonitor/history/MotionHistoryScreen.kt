package com.sza.fastmediasorter.wear.ui.apps.motionmonitor.history

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
import com.sza.fastmediasorter.wear.domain.model.ActivityIntensity
import com.sza.fastmediasorter.wear.domain.model.MotionHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.MotionSummary
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import timber.log.Timber

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
 * S3014: The physical activity measurement history and analytics screen.
 *
 * Displays summary statistics (total steps, average, max), visual trend chart for recent readings,
 * and structured history cards with activity intensity badges.
 */
@Composable
fun MotionHistoryScreen(
    viewModel: MotionHistoryViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Timber.d("S3014: motion history screen opened")

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
                    item { SectionHeader(stringResource(R.string.motion_trend_title)) }
                    item { MotionTrendChart(entries = state.entries) }
                    item { BaselineLegend() }
                    item { SectionHeader(stringResource(R.string.motion_history_title)) }
                }

                items(state.entries) { entry ->
                    MotionHistoryCard(entry)
                }

                item { ClearChip(onClick = { viewModel.clearHistory() }) }
            }
        }
    }
}

@Composable
private fun ScreenTitle() {
    Text(
        text = stringResource(R.string.motion_analytics_title),
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
        text = stringResource(R.string.motion_step_baselines),
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
        text = stringResource(R.string.motion_history_empty),
        style = MaterialTheme.typography.body1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EMPTY_MESSAGE_VERTICAL_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun AnalyticsSummaryCard(summary: MotionSummary) {
    val totalLabel = stringResource(R.string.motion_stat_total)
    val avgLabel = stringResource(R.string.motion_stat_avg)
    val maxLabel = stringResource(R.string.motion_stat_max)

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
            text = stringResource(R.string.motion_total_readings, summary.count),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant
        )

        Row(
            modifier = Modifier.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$totalLabel: ${summary.totalSteps}",
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colors.onSurface
            )
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(BADGE_CORNER_RADIUS))
                    .background(summary.avgIntensity.color.copy(alpha = SUMMARY_BADGE_ALPHA))
                    .padding(horizontal = BADGE_INNER_HORIZONTAL_PADDING, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(summary.avgIntensity.labelRes),
                    style = MaterialTheme.typography.caption3.copy(fontWeight = FontWeight.Bold),
                    color = summary.avgIntensity.color
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
                text = "$avgLabel: ${summary.avgSteps}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
            Text(
                text = "$maxLabel: ${summary.maxSteps}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MotionHistoryCard(entry: MotionHistoryEntry) {
    val dateTime = formatTimestamp(entry.timestampMillis)
    val intensity = ActivityIntensity.classify(entry.steps)

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
                text = stringResource(R.string.motion_history_steps_format, entry.steps),
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colors.onSurface
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(BADGE_CORNER_RADIUS))
                .background(intensity.color.copy(alpha = HISTORY_BADGE_ALPHA))
                .padding(horizontal = BADGE_INNER_HORIZONTAL_PADDING, vertical = BADGE_INNER_VERTICAL_PADDING),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(intensity.labelRes),
                style = MaterialTheme.typography.caption3.copy(fontWeight = FontWeight.Bold),
                color = intensity.color
            )
        }
    }
}

@Composable
private fun ClearChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.motion_history_clear)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CLEAR_CHIP_HORIZONTAL_PADDING, vertical = CLEAR_CHIP_VERTICAL_PADDING),
        colors = ChipDefaults.secondaryChipColors()
    )
}

@Composable
private fun formatTimestamp(timestampMillis: Long): String =
    LocalWearDateTimeFormatter.current.formatDateTime(timestampMillis, LocalWearUnitSystem.current)
