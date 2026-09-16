package com.sza.fastmediasorter.wear.ui.apps.clipboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState

/** The key this screen's list position is remembered under. */
private const val CLIPBOARD_SCREEN_KEY = "apps/clipboard"

private val TITLE_BOTTOM_PADDING = 8.dp
private val ROW_VERTICAL_PADDING = 4.dp
private val ACTION_ICON_SIZE = 16.dp

/**
 * The watch's text clipboard, and the one action that hands it to the paired phone (S3109).
 *
 * The screen offers no "fetch from the phone" counterpart, and cannot: since Android 10 only the
 * foreground app may read its own clipboard, so the phone - which is in the background whenever this
 * watch asks - is unable to answer such a request at all (ADR-1). The phone sends its clipboard from
 * its own companion screen instead, and it lands here without this screen being open.
 */
@Composable
fun ClipboardScreen(
    viewModel: ClipboardViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = CLIPBOARD_SCREEN_KEY)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_clipboard_title),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = TITLE_BOTTOM_PADDING),
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = uiState.preview.ifBlank { stringResource(R.string.wear_clipboard_empty) },
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            item {
                SendToPhoneChip(
                    enabled = !uiState.sending && uiState.hasText,
                    outcomeRes = uiState.outcomeRes,
                    outcomeArg = uiState.outcomeArg,
                    sending = uiState.sending,
                    onClick = viewModel::sendToPhone
                )
            }
        }
    }
}

/**
 * Sends the clipboard to the paired phone, and says underneath what came of it.
 *
 * The outcome is a line of text rather than a toast or a dialog: the watch has no Snackbar host, and
 * a button that works silently is what the strategic risk list names as the thing to avoid.
 */
@Composable
private fun SendToPhoneChip(
    enabled: Boolean,
    outcomeRes: Int?,
    outcomeArg: String?,
    sending: Boolean,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CompactChip(
            onClick = onClick,
            enabled = enabled,
            label = { Text(stringResource(R.string.wear_clipboard_send_action)) },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(ACTION_ICON_SIZE)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ChipDefaults.secondaryChipColors()
        )
        if (outcomeRes != null && !sending) {
            Text(
                text = if (outcomeArg == null) {
                    stringResource(outcomeRes)
                } else {
                    stringResource(outcomeRes, outcomeArg)
                },
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ROW_VERTICAL_PADDING),
                textAlign = TextAlign.Center
            )
        }
    }
}
