package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.DialogDefaults
import com.sza.fastmediasorter.wear.R

// Together with the list's own 4 dp spacing these restore the gaps the library alert put under its
// title (8 dp) and its message (12 dp).
private val ALERT_TITLE_GAP = 4.dp
private val ALERT_MESSAGE_GAP = 8.dp

/**
 * The standard full-screen alert of the watch module, drawn with the typography and button styling
 * fixed by `docs/ui/WEAR_UI_COMPONENT_PATTERNS.md` section 2.3.
 *
 * Screens take this instead of calling the library `Alert` directly, so a deletion always asks in error
 * colours and an alert always stands inside the round glass, whichever screen raises it.
 *
 * S4361: the body is a [WearDialogListColumn], not the library `Alert`. The library pads its content by
 * 10 dp at the sides only and centres it, which stood the title's first line on the glass edge of the
 * frame Google Play rejected, and its two-button overload lines the actions up in a row sized for 52 dp
 * round buttons - with text chips in it the pair reached the bottom arc. The dialog list gives every row
 * the inscribed square's width and bounds the viewport where the chord admits that width (S2762, S4136),
 * so no scroll position draws anything at the glass.
 *
 * The actions therefore always stack, confirmation first and cancel under it, the order of the library's
 * own chip-alert sample; [cancelLabel] `null` drops the second chip.
 */
// A slot API's parameters are its contract: every one below is a distinct rendering decision the
// caller has to be able to make, and folding them into a holder would only move the list.
@Suppress("LongParameterList")
@Composable
fun StandardWearAlertDialog(
    show: Boolean,
    title: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmLabel: String = stringResource(R.string.ok),
    cancelLabel: String? = stringResource(R.string.cancel),
    isDestructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    if (!show) return

    val listState = rememberWearDialogListState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            // The alert is drawn over its screen rather than in a window of its own, and the bounded
            // viewport leaves a band at the top and bottom that nothing in the list covers; a pointer
            // node here keeps a tap in those bands from reaching the screen behind.
            .pointerInput(Unit) {}
    ) {
        WearDialogListColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = DialogDefaults.AlertVerticalArrangement
        ) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = ALERT_TITLE_GAP)
                )
            }
            if (message != null) {
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = ALERT_MESSAGE_GAP)
                    )
                }
            }
            if (content != null) {
                item {
                    Column(modifier = Modifier.fillMaxWidth(), content = content)
                }
            }
            item {
                AlertActionChip(
                    label = confirmLabel,
                    onClick = onConfirm,
                    colors = confirmChipColors(isDestructive)
                )
            }
            if (cancelLabel != null) {
                item {
                    AlertActionChip(
                        label = cancelLabel,
                        onClick = onDismissRequest,
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }
        }
        // S2754: no Scaffold reaches an overlay, so the alert draws its own indicator.
        PositionIndicator(listState)
    }
}

@Composable
private fun confirmChipColors(isDestructive: Boolean): ChipColors = if (isDestructive) {
    ChipDefaults.chipColors(
        backgroundColor = MaterialTheme.colors.error,
        contentColor = MaterialTheme.colors.onError
    )
} else {
    ChipDefaults.primaryChipColors()
}

@Composable
private fun AlertActionChip(
    label: String,
    onClick: () -> Unit,
    colors: ChipColors
) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                text = label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        colors = colors
    )
}
