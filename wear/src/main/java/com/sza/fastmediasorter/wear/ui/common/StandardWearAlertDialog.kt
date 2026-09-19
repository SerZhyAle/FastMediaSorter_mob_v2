package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import com.sza.fastmediasorter.wear.R

/**
 * The standard full-screen alert of the watch module, drawn with the typography and button styling
 * fixed by `docs/ui/WEAR_UI_COMPONENT_PATTERNS.md` section 2.3.
 *
 * Screens take this instead of calling [Alert] directly, so a deletion always asks in error colours
 * and an acknowledgement always draws one wide chip, whichever screen raises it.
 *
 * Two shapes, chosen by [cancelLabel]: with a cancel label the library's two-button overload draws
 * the pair side by side; without one the chip overload draws a single full-width action, because the
 * two-button overload has no way to omit its negative slot and would leave the remaining button
 * pushed off centre by the gap where the other one was.
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
    LaunchedEffect(title) {
    }

    val titleSlot: @Composable ColumnScope.() -> Unit = {
        Text(
            text = title,
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    val messageSlot: (@Composable ColumnScope.() -> Unit)? = message?.let { text ->
        {
            Text(
                text = text,
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (cancelLabel == null) {
        Alert(
            title = titleSlot,
            modifier = modifier,
            message = messageSlot
        ) {
            if (content != null) {
                item {
                    Column(modifier = Modifier.fillMaxWidth(), content = content)
                }
            }
            item {
                AlertActionChip(
                    label = confirmLabel,
                    onClick = onConfirm,
                    colors = confirmChipColors(isDestructive),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        Alert(
            title = titleSlot,
            negativeButton = {
                AlertActionChip(
                    label = cancelLabel,
                    onClick = onDismissRequest,
                    colors = ChipDefaults.secondaryChipColors()
                )
            },
            positiveButton = {
                AlertActionChip(
                    label = confirmLabel,
                    onClick = onConfirm,
                    colors = confirmChipColors(isDestructive)
                )
            },
            modifier = modifier
        ) {
            // The two-button overload of Alert carries no message slot, so the message is the first
            // row of the body instead of a parameter.
            messageSlot?.invoke(this)
            content?.invoke(this)
        }
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
    colors: ChipColors,
    modifier: Modifier = Modifier
) {
    Chip(
        onClick = onClick,
        modifier = modifier,
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
