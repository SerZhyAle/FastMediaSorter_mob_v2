package com.sza.fastmediasorter.wear.ui.player.unsupported

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentFormat
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionSwallow

private val REFUSAL_GLYPH_SIZE = 32.dp
private val TEXT_TOP_PADDING = 8.dp

/**
 * What a file in a format the watch does not render opens instead of a player.
 *
 * S2006: a document used to fall through the router's last branch and open the **audio** player over
 * itself, which is a wrong answer to the user's action rather than a missing feature. This screen was
 * the right answer for every document alike.
 *
 * S2532: it is no longer the answer to every document - the watch reads text itself now - so this is
 * the answer to a format it does not render, and it says which one. It still carries no action: what
 * a wearer can do about a PDF belongs to the "open on the phone" transport (S2142), not here, and a
 * button that led nowhere would be a second wrong answer. The way back is the platform dismiss
 * gesture, as on every other screen in this module.
 *
 * @param format the refused format, as the route argument named it. Only the unreadable entries of
 * [WearDocumentFormat] ever reach here; a readable one opens the reader instead.
 */
@Composable
fun UnsupportedFileScreen(format: WearDocumentFormat) {
    WearScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .rotaryActionSwallow()
                .padding(horizontal = TEXT_TOP_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface,
                modifier = Modifier.size(REFUSAL_GLYPH_SIZE)
            )
            Text(
                text = stringResource(R.string.wear_unsupported_file_title),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = TEXT_TOP_PADDING)
            )
            Text(
                text = stringResource(
                    R.string.wear_unsupported_file_message_format,
                    stringResource(format.labelRes)
                ),
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = TEXT_TOP_PADDING)
            )
        }
    }
}
