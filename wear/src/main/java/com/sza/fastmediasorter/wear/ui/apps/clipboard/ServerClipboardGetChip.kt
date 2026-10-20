package com.sza.fastmediasorter.wear.ui.apps.clipboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.SERVER_CLIPBOARD_DETAILS_RES

private val LINE_TOP_PADDING = 4.dp
private val CHIP_ICON_SIZE = 16.dp
private const val UNKNOWN_DEVICE = "-"

/**
 * S4227: "Get the clipboard from the server", under "Send to phone" on the Clipboard screen (strategic ADR-2).
 *
 * Disabled with its reason written underneath when no entry qualifies, so the state is told by text and not only
 * by colour; while a value arrives the progress line and a cancel chip replace the outcome, and the display stays
 * on - the transfer runs only while the person watches it (research 04). Every line is plain text TalkBack reads.
 */
@Composable
internal fun ServerClipboardGetChip(
    server: ServerClipboardUiState,
    onGet: () -> Unit,
    onCancel: () -> Unit,
    openIn: ServerOpenIn,
) {
    KeepScreenOnWhile(server.receiving)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CompactChip(
            onClick = onGet,
            enabled = server.available && !server.receiving,
            label = { Text(stringResource(R.string.wear_server_clipboard_get_action)) },
            icon = { ChipIcon(isCancel = false) },
            modifier = Modifier.fillMaxWidth(),
            colors = ChipDefaults.secondaryChipColors(),
        )
        when {
            !server.available -> InfoLine(stringResource(R.string.wear_server_clipboard_get_unavailable))
            server.stateWhen != null -> InfoLine(
                stringResource(
                    R.string.wear_server_clipboard_state,
                    server.stateWhen,
                    server.stateFrom ?: UNKNOWN_DEVICE,
                )
            )
        }
        if (server.receiving) {
            InfoLine(stringResource(R.string.wear_server_clipboard_progress, server.progressPercent))
            CompactChip(
                onClick = onCancel,
                label = { Text(stringResource(R.string.cancel)) },
                icon = { ChipIcon(isCancel = true) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
            )
        } else {
            OutcomeLines(server)
            if (server.canOpenIn) {
                OpenInChip(enabled = openIn.enabled, failedAppLabel = openIn.failedAppLabel, onClick = openIn.onClick)
            }
        }
    }
}

/**
 * S4381: what the "Open in.." chip under the result line needs - kept together so the get chip takes one extra
 * parameter rather than three.
 */
internal class ServerOpenIn(
    val enabled: Boolean,
    val failedAppLabel: String?,
    val onClick: () -> Unit,
)

@Composable
private fun OutcomeLines(server: ServerClipboardUiState) {
    val outcomeRes = server.outcomeRes ?: return
    val arg = server.outcomeArg
    InfoLine(if (arg == null) stringResource(outcomeRes) else stringResource(outcomeRes, arg))
    server.skipped?.let { InfoLine(stringResource(R.string.wear_server_clipboard_skipped, it)) }
    if (server.showDetailsLine) {
        InfoLine(stringResource(SERVER_CLIPBOARD_DETAILS_RES))
    }
}

@Composable
private fun ChipIcon(isCancel: Boolean) {
    Icon(
        imageVector = if (isCancel) Icons.Filled.Close else Icons.Filled.Download,
        contentDescription = null,
        modifier = Modifier.size(CHIP_ICON_SIZE),
    )
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption3,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LINE_TOP_PADDING),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun KeepScreenOnWhile(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}
