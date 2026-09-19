package com.sza.fastmediasorter.wear.ui.apps.stopwatch

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchState
import com.sza.fastmediasorter.wear.ui.common.WearAction
import com.sza.fastmediasorter.wear.ui.common.WearActionButton
import com.sza.fastmediasorter.wear.ui.common.WearActionSquare

private val CAPTION_VERTICAL_PADDING = 6.dp
private val BUTTON_GAP = 6.dp

/**
 * Everything that acts on the whole measurement, plus the participant count and the result page.
 *
 * These actions live here rather than beside the regions on purpose: a mis-tap on the main surface must
 * not be able to stop four measurements at once (strategic §5).
 */
data class WearStopwatchMenuActions(
    val onStartAll: () -> Unit,
    val onStopAll: () -> Unit,
    val onResetAll: () -> Unit,
    val onParticipantCountSelected: (Int) -> Unit,
    val onResult: () -> Unit,
    val onDismiss: () -> Unit
)

/**
 * The menu is raised over the running regions, so it paints the scheme's own background: without it the
 * measurements underneath read straight through the menu and the wearer cannot tell which surface a tap
 * will reach (S3114).
 *
 * It is hosted by [WearActionSquare] rather than a list, because that host already carries the three
 * things the owner asked for - a button as wide as its label, centred on the glass, and a tap past the
 * buttons that means "back".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WearStopwatchMenuSheet(
    participantCount: Int,
    actions: WearStopwatchMenuActions,
    scrollState: ScrollState = rememberScrollState()
) {
    val wholeMeasurement = listOf(
        WearAction(label = stringResource(R.string.wear_stopwatch_start_all), onClick = actions.onStartAll),
        WearAction(label = stringResource(R.string.wear_stopwatch_stop_all), onClick = actions.onStopAll),
        WearAction(label = stringResource(R.string.wear_stopwatch_reset_all), onClick = actions.onResetAll)
    )
    val trailing = listOf(
        WearAction(label = stringResource(R.string.wear_stopwatch_result), onClick = actions.onResult),
        WearAction(label = stringResource(R.string.wear_state_back), onClick = actions.onDismiss)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        WearActionSquare(
            modifier = Modifier.background(MaterialTheme.colors.background),
            onDismiss = actions.onDismiss,
            scrollState = scrollState,
            header = {
                Text(
                    text = stringResource(R.string.wear_app_stopwatch),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center
                )
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(BUTTON_GAP),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MenuActionStack(wholeMeasurement)
                Text(
                    text = stringResource(R.string.wear_stopwatch_participants),
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = CAPTION_VERTICAL_PADDING)
                )
                // A count is one glyph wide, so the row holds every allowed value on any watch this
                // module supports and the wrap is only the fallback for a narrower one.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(BUTTON_GAP)
                ) {
                    WearStopwatchState.ALLOWED_COUNTS.forEach { count ->
                        WearActionButton(
                            action = WearAction(
                                label = count.toString(),
                                primary = count == participantCount,
                                onClick = { actions.onParticipantCountSelected(count) }
                            )
                        )
                    }
                }
                MenuActionStack(trailing)
            }
        }
        PositionIndicator(scrollState)
    }
}

/**
 * One column of actions sized by the longest label in that column, never by the glass.
 */
@Composable
private fun MenuActionStack(actions: List<WearAction>) {
    Column(
        modifier = Modifier.width(IntrinsicSize.Min),
        verticalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        actions.forEach { action ->
            WearActionButton(
                action = action,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
