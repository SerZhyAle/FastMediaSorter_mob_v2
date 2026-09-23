package com.sza.fastmediasorter.wear.ui.apps.stopwatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState

private val TITLE_VERTICAL_PADDING = 12.dp

/**
 * The finished measurement, read on the watch itself.
 *
 * This is the whole of what the phone does with a file and a share sheet: the watch has neither, so a
 * result that cannot be read here cannot be read at all (strategic §2 goal 6, ADR-4).
 *
 * @param text the rendered measurement, or null when nothing has been measured yet.
 */
@Composable
fun WearStopwatchResultPage(
    text: String?,
    onDismiss: () -> Unit,
    listState: ScalingLazyListState = rememberWearListState()
) {
    // S3362: the page is raised over the stopwatch rather than hosted by its own scaffold, so the
    // indicator is drawn here beside the list it belongs to - the same place the menu sheet of this
    // program draws its own. Without it a result long enough to scroll scrolled with no scroll bar,
    // which is Wear OS review item WO-V8 and one of the causes of an earlier rejection. It paints the
    // scheme's background for the reason the menu sheet does (S3114): walked at font scale 1.3 on the
    // 192 dp emulator, the laps, the running reading and the Back chip were drawn over one another.
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_stopwatch_result),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = TITLE_VERTICAL_PADDING)
                )
            }
            item {
                Text(
                    text = text?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.wear_stopwatch_result_empty),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    onClick = onDismiss,
                    label = { Text(text = stringResource(R.string.wear_state_back)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
        PositionIndicator(listState)
    }
}
