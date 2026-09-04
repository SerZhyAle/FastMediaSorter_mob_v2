package com.sza.fastmediasorter.wear.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.wear.WearLogReportOutcome
import com.sza.fastmediasorter.wear.data.wear.WearLogReportRefusalReasons
import com.sza.fastmediasorter.wear.domain.model.WearPortalLinks
import com.sza.fastmediasorter.wear.ui.common.WearLinkRow
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import timber.log.Timber

@Composable
fun AboutSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logReportState by viewModel.logReportState.collectAsStateWithLifecycle()
    val watchPortalState by viewModel.watchPortalState.collectAsStateWithLifecycle()
    val phonePortalState by viewModel.phonePortalState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            centered = true
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
                WearLinkRow(
                    label = stringResource(R.string.about_web_portal),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WearPortalLinks.WEB_PORTAL_URL))
                        val launched = try {
                            context.startActivity(intent)
                            true
                        } catch (e: ActivityNotFoundException) {
                            // Not swallowed: the verdict goes to the view model, which turns it into
                            // the on-screen hint pointing at the row below.
                            Timber.w(e, "No browser to open Wear web portal")
                            false
                        }
                        viewModel.onWatchPortalOpened(launched)
                    },
                    message = portalMessage(watchPortalState)
                )
            }
            item {
                WearLinkRow(
                    label = stringResource(R.string.about_web_portal_on_phone),
                    onClick = viewModel::openPortalOnPhone,
                    message = portalMessage(phonePortalState)
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

/**
 * S2496: the sentence a portal row shows under itself, or null when it has nothing to say.
 *
 * A successful watch launch says nothing on purpose - the browser is already covering the screen, so
 * a message would only be read after coming back to a row the user has finished with.
 */
@Composable
private fun portalMessage(state: WearPortalLinkState): String? {
    val outcome = (state as? WearPortalLinkState.Finished)?.outcome ?: return null
    return stringResource(
        when (outcome) {
            WearPortalLinkOutcome.OPENED_ON_PHONE -> R.string.about_web_portal_phone_opened
            WearPortalLinkOutcome.NO_WATCH_BROWSER -> R.string.about_web_portal_no_browser
            WearPortalLinkOutcome.NO_CONNECTED_PHONE -> R.string.about_web_portal_no_phone
            WearPortalLinkOutcome.PHONE_FAILED -> R.string.about_web_portal_phone_failed
        }
    )
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
