package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog

private val SEARCH_DIALOG_ICON_SIZE = 20.dp
private val SEARCH_DIALOG_TITLE_GAP = 8.dp

/**
 * S2136: the full-screen dialog behind the search icon.
 *
 * It has exactly two jobs - start the watch's input path, and clear what is already narrowing the
 * list. The query itself never originates here: it arrives from the input activity, which is why
 * the input chip dismisses before launching rather than after.
 *
 * The streams dialog's four preset chips are deliberately not carried over. They name stream genres
 * and would be four dead rows in a file list.
 */
@Composable
fun WearSearchDialog(
    title: String,
    inputLabel: String,
    clearLabel: String,
    currentQuery: String,
    onLaunchInput: () -> Unit,
    onClear: () -> Unit,
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
                    modifier = Modifier.padding(bottom = SEARCH_DIALOG_TITLE_GAP),
                    textAlign = TextAlign.Center
                )
            }

            item {
                Chip(
                    onClick = {
                        onDismiss()
                        onLaunchInput()
                    },
                    label = { Text(inputLabel) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Keyboard,
                            contentDescription = inputLabel,
                            modifier = Modifier.size(SEARCH_DIALOG_ICON_SIZE)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors()
                )
            }

            // Offered only when there is something to clear, so the dialog does not present an
            // action that would do nothing on a list nobody has narrowed yet.
            if (currentQuery.isNotEmpty()) {
                item {
                    Chip(
                        onClick = onClear,
                        label = { Text(clearLabel) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = clearLabel,
                                modifier = Modifier.size(SEARCH_DIALOG_ICON_SIZE)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }
        }
    }
}
