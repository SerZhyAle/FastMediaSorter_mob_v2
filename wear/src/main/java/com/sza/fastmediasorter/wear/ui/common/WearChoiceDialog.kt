package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog

private val CHOICE_DIALOG_ICON_SIZE = 20.dp
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
fun <T> WearChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets()
        ) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier.padding(bottom = CHOICE_DIALOG_TITLE_GAP),
                    textAlign = TextAlign.Center
                )
            }

            items(options) { option ->
                val isSelected = option == selected
                Chip(
                    onClick = {
                        onSelected(option)
                        onDismiss()
                    },
                    label = { Text(labelOf(option)) },
                    icon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                // The row's own `selected` flag is what TalkBack reads; describing
                                // the glyph too would announce the same fact a second time.
                                contentDescription = null,
                                modifier = Modifier.size(CHOICE_DIALOG_ICON_SIZE)
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { this.selected = isSelected },
                    colors = if (isSelected) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }
        }
    }
}
