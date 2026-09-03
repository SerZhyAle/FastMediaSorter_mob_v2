package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog
import com.sza.fastmediasorter.wear.domain.model.WearViewMode

private val CHOICE_DIALOG_TITLE_GAP = 8.dp

/**
 * S2136: pick one value out of a list, full screen.
 *
 * Generic over the value because the same dialog serves both the sort orders and the content types
 * - the two screens differ only in what they hand it - which is what keeps each content screen from
 * growing two more private dialogs of its own.
 *
 * The chosen row is marked twice over: a check glyph, and a `selected` semantics flag. Colour alone
 * would not carry the choice (strategic 3.2), and the semantics flag is what states it aloud
 * without needing a word this component would then have to be handed in every language.
 */
@Composable
@Suppress("LongParameterList")
fun <T> WearChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    viewMode: WearViewMode = WearViewMode.LIST,
    fixedEnumeration: Boolean = true
) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val listState = rememberScalingLazyListState()
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val gridFit = WearChoiceGridFit(
                viewMode = viewMode,
                availableWidthDp = maxWidth.value.toInt(),
                fixedEnumeration = fixedEnumeration
            )
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = wearScreenInsets(),
                scalingParams = WearGridScalingParams
            ) {
                item {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.title3,
                        modifier = Modifier.padding(bottom = CHOICE_DIALOG_TITLE_GAP),
                        textAlign = TextAlign.Center
                    )
                }

                wearChoiceRows(
                    options = options,
                    selected = selected,
                    labelOf = labelOf,
                    onSelected = { option ->
                        onSelected(option)
                        onDismiss()
                    },
                    gridFit = gridFit
                )
            }
        }
    }
}
