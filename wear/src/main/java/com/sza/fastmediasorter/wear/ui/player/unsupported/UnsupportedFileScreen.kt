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
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import timber.log.Timber

private val REFUSAL_GLYPH_SIZE = 32.dp
private val TEXT_TOP_PADDING = 8.dp

/**
 * What a file the watch cannot play opens instead of a player.
 *
 * S2006: a document used to fall through the router's last branch and open the **audio** player over
 * itself, which is a wrong answer to the user's action rather than a missing feature. This screen is
 * the right answer, and deliberately nothing more: it carries no action, because a viewer for these
 * files is out of the ticket's scope and offering a button that leads nowhere would be a second wrong
 * answer. The way back is the platform dismiss gesture, as on every other screen in this module.
 */
@Composable
fun UnsupportedFileScreen() {
    Timber.d("S2006: unsupported file refused instead of opening the audio player")
    WearScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                text = stringResource(R.string.wear_unsupported_file_message),
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = TEXT_TOP_PADDING)
            )
        }
    }
}
