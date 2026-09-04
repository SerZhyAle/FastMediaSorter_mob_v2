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
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearSettingsItem
import com.sza.fastmediasorter.wear.ui.common.WearSettingsRow
import com.sza.fastmediasorter.wear.ui.common.WearSettingsStepperCell
import com.sza.fastmediasorter.wear.ui.common.WearSettingsToggleCell
import com.sza.fastmediasorter.wear.ui.common.packSettingsRows
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.util.GridColumnFit

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
    listState: ScalingLazyListState = rememberWearListState(positionKey = SettingsRoutes.SLIDESHOW)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // S1949: both controls declare full width - `enable_slideshow` measures 35 characters in
    // Portuguese, past the 32-character threshold, and the stepper carries its value on the same
    // line as its label, which a half cell would swallow. The screen is single-column by the rule
    // rather than by omission, and a narrow setting added here joins rows without further work.
    val items = listOf(
        WearSettingsItem(fullWidth = true) { _ ->
            WearSettingsToggleCell(
                label = stringResource(R.string.enable_slideshow),
                checked = uiState.isSlideshowEnabled,
                onToggle = viewModel::toggleSlideshow
            )
        },
        WearSettingsItem(fullWidth = true) { _ ->
            val currentIndex = SLIDESHOW_INTERVALS
                .indexOfFirst { it == uiState.slideshowIntervalSeconds }
                .coerceAtLeast(0)
            WearSettingsStepperCell(
                values = SLIDESHOW_INTERVALS,
                currentValue = uiState.slideshowIntervalSeconds,
                labelText = stringResource(
                    R.string.slideshow_interval_label,
                    SLIDESHOW_INTERVALS[currentIndex]
                ),
                decreaseDescription = stringResource(R.string.slideshow_interval_decrease),
                increaseDescription = stringResource(R.string.slideshow_interval_increase),
                onValueChanged = viewModel::setSlideshowInterval
            )
        }
    )

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(WearViewMode.GRID_2, maxWidth.value.toInt())
            WearListColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                item {
                    Text(
                        text = stringResource(R.string.slideshow_settings),
                        style = MaterialTheme.typography.title2,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
                items(packSettingsRows(items, columns)) { row -> WearSettingsRow(row) }
            }
        }
    }
}
