package com.sza.fastmediasorter.wear.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.wear.WearLogReportOutcome
import com.sza.fastmediasorter.wear.data.wear.WearLogReportRefusalReasons
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets

@Composable
fun AboutSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberScalingLazyListState()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logReportState by viewModel.logReportState.collectAsStateWithLifecycle()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.about),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = stringResource(R.string.version, uiState.appVersion),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption1
                )
            }
            item {
                Text(
                    text = stringResource(R.string.build_number, uiState.buildNumber),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption1
                )
            }
            item {
                SendLogsRow(
                    state = logReportState,
                    onSend = viewModel::sendLogReport
                )
            }
        }
    }
}

/**
 * S1802: the last row of the About list - sends the watch log to the paired phone.
 *
 * The outcome is shown under the row rather than in a dialog: the watch screen is small, and the
 * message has to be readable without leaving the screen the user just pressed on.
 */
@Composable
private fun SendLogsRow(
    state: WearLogReportState,
    onSend: () -> Unit
) {
    val sending = state is WearLogReportState.Sending
    val label = if (sending) {
        stringResource(R.string.about_send_logs_sending)
    } else {
        stringResource(R.string.about_send_logs)
    }

    // A Column, not two siblings: one ScalingLazyColumn item is a single slot, so a bare Chip and
    // Text stack on top of each other - the message rendered over the row until this was added.
    Column(modifier = Modifier.fillMaxWidth()) {
        Chip(
            // A second press while sending would queue an identical report; the view model refuses
            // it too, so the guard survives even if this one is ever lost in a redesign.
            onClick = { if (!sending) onSend() },
            enabled = !sending,
            label = { Text(text = label) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth()
        )

        val message = (state as? WearLogReportState.Finished)?.outcome?.let { outcomeMessageRes(it) }
        if (message != null) {
            Text(
                text = stringResource(message),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption1
            )
        }
    }
}

private fun outcomeMessageRes(outcome: WearLogReportOutcome): Int = when (outcome) {
    is WearLogReportOutcome.Delivered -> R.string.about_send_logs_sent
    is WearLogReportOutcome.NoConnectedPhone -> R.string.about_send_logs_no_phone
    is WearLogReportOutcome.PhoneDidNotAnswer -> R.string.about_send_logs_no_answer
    // A refusal is not always the notification one: the phone also refuses a payload it cannot read
    // or store, and telling the user to switch notifications on would then be plainly wrong.
    is WearLogReportOutcome.PhoneRefused ->
        if (outcome.reason == WearLogReportRefusalReasons.NOTIFICATIONS_DISABLED) {
            R.string.about_send_logs_notifications_off
        } else {
            R.string.about_send_logs_refused
        }
}
