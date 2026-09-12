package com.sza.fastmediasorter.wear.ui.apps.stopwatch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchResultLabels
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchResultRenderer
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
import com.sza.fastmediasorter.wear.ui.common.RectangularButton
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.common.wearMaxSquareSide
import com.sza.fastmediasorter.wear.ui.common.wearRingInset
import java.util.Locale

private val MENU_BUTTON_HEIGHT = 26.dp
private val MENU_BUTTON_TOP_GAP = 2.dp

/**
 * The watch stopwatch: one, two or four independent measurements on one surface.
 *
 * The regions take the largest square the round glass holds whole, and everything that acts on the
 * whole measurement sits behind the single menu control below them - a mis-tap on the main surface must
 * not be able to stop four measurements at once (strategic §5).
 *
 * The screen owns no measurement of its own: every button is one call into the view model, which owns
 * the engine and is the only place that reads the clock.
 */
@Composable
fun WearStopwatchScreen(
    onLeave: () -> Unit = {},
    viewModel: WearStopwatchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var resultOpen by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val menuListState = rememberWearListState()
    val resultListState = rememberWearListState()
    // The two words are resolved here, as format strings, and filled in when the result is rendered:
    // the renderer is plain Kotlin and its label lambdas run outside composition, where a resource
    // lookup cannot happen at all.
    val participantFormat = stringResource(R.string.wear_stopwatch_participant)
    val lapFormat = stringResource(R.string.wear_stopwatch_lap_n)
    val labels = remember(participantFormat, lapFormat) {
        WearStopwatchResultLabels(
            participant = { number -> String.format(Locale.getDefault(), participantFormat, number) },
            lap = { number -> String.format(Locale.getDefault(), lapFormat, number) }
        )
    }

    // While a measurement runs the display is what the wearer is watching, so it is held awake; a
    // stopped screen releases the claim and the watch dims on its own schedule.
    KeepScreenOnEffect(enabled = uiState.anyRunning)

    WearScreenScaffold(contentPadding = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = wearRingInset(), bottom = wearRingInset()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WearStopwatchRegions(
                state = uiState.state,
                nowMillis = uiState.nowMillis,
                onStartOrLap = viewModel::onStartOrLap,
                onStopOrReset = viewModel::onStopOrReset,
                modifier = Modifier
                    .width(wearMaxSquareSide())
                    .weight(1f)
            )
            // Holding the menu control leaves the program, the same gesture the calculator uses - the
            // screen does not own the back stack, so leaving is the host's word, handed in.
            RectangularButton(
                onClick = { menuOpen = true },
                onLongClick = onLeave,
                modifier = Modifier
                    .width(wearMaxSquareSide())
                    .padding(top = MENU_BUTTON_TOP_GAP)
                    .height(MENU_BUTTON_HEIGHT),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text(
                    text = stringResource(R.string.wear_stopwatch_menu),
                    style = MaterialTheme.typography.button,
                    maxLines = 1
                )
            }
        }

        if (menuOpen) {
            WearStopwatchMenuSheet(
                participantCount = uiState.participantCount,
                listState = menuListState,
                actions = WearStopwatchMenuActions(
                    onStartAll = {
                        viewModel.onStartAll()
                        menuOpen = false
                    },
                    onStopAll = {
                        viewModel.onStopAll()
                        menuOpen = false
                    },
                    onResetAll = {
                        viewModel.onResetAll()
                        menuOpen = false
                    },
                    onParticipantCountSelected = { count ->
                        viewModel.onParticipantCountSelected(count)
                        menuOpen = false
                    },
                    onResult = {
                        // Rendered here because only the screen can resolve the words; the view model
                        // keeps the text so it survives the program being dismissed. The page is shown
                        // the text just rendered rather than the stored one - the store is written
                        // asynchronously and would still hold the previous measurement at this instant.
                        val rendered = WearStopwatchResultRenderer.render(
                            uiState.state,
                            uiState.nowMillis,
                            labels
                        )
                        viewModel.onResultRendered(rendered)
                        resultText = rendered
                        menuOpen = false
                        resultOpen = true
                    },
                    onDismiss = { menuOpen = false }
                )
            )
        }

        if (resultOpen) {
            WearStopwatchResultPage(
                text = resultText ?: uiState.lastResult,
                listState = resultListState,
                onDismiss = { resultOpen = false }
            )
        }
    }
}
