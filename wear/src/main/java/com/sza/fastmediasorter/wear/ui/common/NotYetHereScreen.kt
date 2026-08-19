package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import timber.log.Timber

/**
 * Destination for a home section whose screen belongs to another ticket.
 *
 * Every section the catalog returns must lead somewhere: a row that navigates to an unregistered
 * route is a dead tap the user cannot tell apart from a bug. [ownerTicket] identifies the ticket that
 * will replace this screen and is logged rather than shown - a ticket id on screen is developer text,
 * which the communication policy keeps out of the interface.
 */
@Composable
fun NotYetHereScreen(ownerTicket: String) {
    Timber.d("NotYetHereScreen shown, owned by %s", ownerTicket)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.wear_coming_soon),
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center
        )
    }
}
