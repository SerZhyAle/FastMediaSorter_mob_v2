package com.sza.fastmediasorter.wear.ui.apps.calculator

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorHistoryEntry
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionSteps

private val TITLE_VERTICAL_PADDING = 12.dp
private val EMPTY_TEXT_PADDING = 16.dp

/**
 * The stored calculations, newest first, reached from the menu (owner ruling 2026-08-19).
 *
 * Tapping an entry puts its result back on the display, so the history is a source of operands rather
 * than a log to read.
 */
@Composable
fun CalculatorHistoryPage(
    entries: List<WearCalculatorHistoryEntry>,
    onEntryPicked: (WearCalculatorHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberWearListState()

    // S1719: the crown steps the history's type size, mirroring the phone's pinch through the same
    // five sizes. The rotary helper consumes the event by design, so the crown no longer scrolls this
    // list - a swipe still does, and reading a result was the point of scaling it (strategic 2.3).
    val context = LocalContext.current
    val scale = remember(context) { WearCalculatorHistoryScale(context) }
    var historySizeSp by remember { mutableStateOf(scale.currentSizeSp) }

    Box(
        modifier = Modifier
            .rotaryActionSteps { step -> historySizeSp = scale.step(step) }
            .fillMaxSize()
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_calc_history_title),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = TITLE_VERTICAL_PADDING)
                )
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.wear_calc_history_empty),
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(EMPTY_TEXT_PADDING)
                    )
                }
            } else {
                items(entries) { entry ->
                    Chip(
                        onClick = { onEntryPicked(entry) },
                        label = {
                            Text(
                                text = "${entry.expression} = ${entry.result}",
                                fontSize = historySizeSp.sp,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
                item {
                    Chip(
                        onClick = onClearHistory,
                        label = { Text(text = stringResource(R.string.wear_calc_history_clear)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }

            item {
                Chip(
                    onClick = onDismiss,
                    label = { Text(text = stringResource(R.string.wear_calc_close)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}
