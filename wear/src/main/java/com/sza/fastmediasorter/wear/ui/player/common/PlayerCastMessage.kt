@file:Suppress("MatchingDeclarationName")

package com.sza.fastmediasorter.wear.ui.player.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Confirmation
import androidx.wear.compose.material.dialog.Dialog

/**
 * S2531: what the phone answered, in words, over whichever player asked.
 *
 * A timed confirmation rather than a chip the wearer must dismiss: every outcome is a statement
 * about something that already happened on the phone, and none of them asks for a second decision -
 * strategic §2 goal 5 requires the refusals to be readable, not acknowledged.
 */
@Composable
fun PlayerCastMessage(manager: PlayerCastManager) {
    val message by manager.message.collectAsStateWithLifecycle()
    val shown = message ?: return
    Dialog(showDialog = true, onDismissRequest = manager::dismissMessage) {
        Confirmation(onTimeout = manager::dismissMessage) {
            Text(
                text = stringResource(shown),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )
        }
    }
}
