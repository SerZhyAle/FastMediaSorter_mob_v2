package com.sza.fastmediasorter.wear.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets

private const val THREE_SECONDS = 3
private const val FIVE_SECONDS = 5
private const val TEN_SECONDS = 10
private const val FIFTEEN_SECONDS = 15
private const val TWENTY_SECONDS = 20
private const val THIRTY_SECONDS = 30
private const val SIXTY_SECONDS = 60
private val SLIDESHOW_INTERVALS = intArrayOf(
    THREE_SECONDS,
    FIVE_SECONDS,
    TEN_SECONDS,
    FIFTEEN_SECONDS,
    TWENTY_SECONDS,
    THIRTY_SECONDS,
    SIXTY_SECONDS,
)

@Composable
fun SlideshowSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.slideshow_settings),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
            item {
                ToggleChip(
                    checked = uiState.isSlideshowEnabled,
                    onCheckedChange = { viewModel.toggleSlideshow() },
                    label = { Text(stringResource(R.string.enable_slideshow)) },
                    toggleControl = {
                        androidx.wear.compose.material.Icon(
                            imageVector = ToggleChipDefaults.switchIcon(uiState.isSlideshowEnabled),
                            contentDescription = null
                        )
                    },
                    colors = ToggleChipDefaults.toggleChipColors()
                )
            }
            item {
                SlideshowIntervalStepper(
                    currentSeconds = uiState.slideshowIntervalSeconds,
                    onIntervalChanged = viewModel::setSlideshowInterval
                )
            }
        }
    }
}

@Composable
private fun SlideshowIntervalStepper(
    currentSeconds: Int,
    onIntervalChanged: (Int) -> Unit
) {
    val currentIndex = SLIDESHOW_INTERVALS.indexOfFirst { it == currentSeconds }.coerceAtLeast(0)
    val labelText = stringResource(R.string.slideshow_interval_label, SLIDESHOW_INTERVALS[currentIndex])
    val decreaseDescription = stringResource(R.string.slideshow_interval_decrease)
    val increaseDescription = stringResource(R.string.slideshow_interval_increase)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(
            onClick = { if (currentIndex > 0) onIntervalChanged(SLIDESHOW_INTERVALS[currentIndex - 1]) },
            enabled = currentIndex > 0,
            modifier = Modifier.size(36.dp).semantics { contentDescription = decreaseDescription },
            colors = ButtonDefaults.secondaryButtonColors()
        ) { Text(text = "−", style = MaterialTheme.typography.button) }
        Text(
            text = labelText,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                .semantics { contentDescription = labelText },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.caption1
        )
        Button(
            onClick = {
                if (currentIndex < SLIDESHOW_INTERVALS.lastIndex) {
                    onIntervalChanged(SLIDESHOW_INTERVALS[currentIndex + 1])
                }
            },
            enabled = currentIndex < SLIDESHOW_INTERVALS.lastIndex,
            modifier = Modifier.size(36.dp).semantics { contentDescription = increaseDescription },
            colors = ButtonDefaults.secondaryButtonColors()
        ) { Text(text = "+", style = MaterialTheme.typography.button) }
    }
}
