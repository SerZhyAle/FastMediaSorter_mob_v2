package com.sza.fastmediasorter.wear.ui.player.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R

private val VOLUME_BAR_HEIGHT = 4.dp
private val VOLUME_BAR_VERTICAL_PADDING = 6.dp
private const val VOLUME_BAR_CORNER_PERCENT = 50

/**
 * S2140: the current media volume, drawn and never changed here.
 *
 * Read-only on purpose - the owner asked to *see* the level before starting a file so the watch cannot
 * suddenly shout, not to gain a second place to set it. Volume stays the player's to change (the bezel)
 * and the watch's own buttons'; a slider here would put a third owner on one system value.
 *
 * [max] of zero renders an empty track rather than dividing by it: a device that reports no scale yet
 * is a real state on first composition, and an empty bar reads as "not known" instead of "silent".
 */
@Composable
internal fun VolumeIndicatorBar(
    level: Int,
    max: Int,
    modifier: Modifier = Modifier
) {
    val readout = stringResource(R.string.wear_audio_volume_level, level, max)
    val filled = if (max > 0) (level.toFloat() / max).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(percent = VOLUME_BAR_CORNER_PERCENT)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = VOLUME_BAR_VERTICAL_PADDING)
            // One description for the pair: a screen reader that read the bar and the digits separately
            // would say the same number twice.
            .semantics(mergeDescendants = true) { contentDescription = readout },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = readout,
            style = MaterialTheme.typography.caption3,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(VOLUME_BAR_HEIGHT)
                .clip(shape)
                .background(Color.DarkGray)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(filled)
                    .height(VOLUME_BAR_HEIGHT)
                    .clip(shape)
                    .background(MaterialTheme.colors.primary)
            )
        }
    }
}

/**
 * S2140: the media volume as one screen read it, at the moment it was shown.
 *
 * Deliberately a snapshot and not a live feed - strategic section 2 rules out subscribing to system
 * volume changes, so this carries no promise of tracking a change made elsewhere while the screen sits
 * open. It is a warning before playback starts, not a meter.
 */
data class VolumeReadout(
    val level: Int = 0,
    val max: Int = 0
)
