package com.sza.fastmediasorter.wear.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.RectangularButton
import timber.log.Timber

private val ROW_GAP = 4.dp
private val STOP_BUTTON_SIZE = 40.dp

/**
 * S2524: the watch's control surface for whatever is playing in the background.
 *
 * Two targets in one row, sized by which one the owner reaches for: returning to the sound is the
 * main scenario (strategic §3.1) and takes the remaining width, stopping is the way out and takes a
 * button. Splitting them is what lets the row be tapped without stopping anything - a single-action
 * row would have to pick one of the two, and either choice loses the other.
 *
 * A session with no library file cannot be reopened - there is no player address for it - so the
 * chip goes inert rather than navigating to a route that resolves to nothing, and the stop button
 * carries the row on its own.
 */
@Composable
fun HomeNowPlayingRow(
    nowPlaying: HomeNowPlaying,
    onOpen: (Long) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Timber.d("S2524: now-playing row visible")
    val stopLabel = stringResource(R.string.wear_now_playing_stop)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Chip(
            onClick = {
                Timber.d("S2524: now-playing row open requested")
                nowPlaying.fileId?.let(onOpen)
            },
            enabled = nowPlaying.fileId != null,
            label = {
                Text(text = nowPlaying.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            secondaryLabel = {
                // The subtitle the store carries names the artist when there is one; the generic
                // line replaces it rather than joining it, because a chip's second line is one line
                // and a round screen truncates the pair before either half is readable.
                Text(
                    text = nowPlaying.subtitle
                        ?: stringResource(R.string.wear_now_playing_row_subtitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ChipDefaults.IconSize)
                )
            },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.weight(1f)
        )

        RectangularButton(
            onClick = {
                Timber.d("S2524: now-playing row stop tapped")
                onStop()
            },
            modifier = Modifier
                .padding(start = ROW_GAP)
                .size(STOP_BUTTON_SIZE)
        ) {
            Icon(imageVector = Icons.Filled.Stop, contentDescription = stopLabel)
        }
    }
}
