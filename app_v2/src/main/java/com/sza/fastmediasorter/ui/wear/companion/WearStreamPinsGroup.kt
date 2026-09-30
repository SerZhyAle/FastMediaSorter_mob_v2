package com.sza.fastmediasorter.ui.wear.companion

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.PinnedStreamChannel
import com.sza.fastmediasorter.domain.usecase.streams.PinnedStreamMove

/**
 * S4016: the channels the watch raises to the top of its stream list, edited from the phone.
 *
 * The composition site draws this only while Streams is on (strategic §3.4). Picking a channel is the
 * host's job - [onAddChannels] opens the phone's stream picker, whose filters and text search are the
 * reason the list is assembled here rather than on the watch.
 */
@Composable
fun WearStreamPinsGroup(viewModel: WearStreamPinsGroupViewModel, onAddChannels: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val pinned by viewModel.pinned.collectAsState()

    WearCompanionGroup(
        title = stringResource(R.string.wear_stream_pins_group_title),
        summary = stringResource(R.string.wear_stream_pins_summary, pinned.size),
        expanded = expanded,
        tag = "wearGroupStreamPins",
        onExpandedChange = { expanded = it }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = SPACING_SMALL)) {
            Text(
                text = stringResource(R.string.wear_stream_pins_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (pinned.isEmpty()) {
                Text(
                    text = stringResource(R.string.wear_stream_pins_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = SPACING_SMALL).testTag("wearStreamPinsEmpty")
                )
            } else {
                pinned.forEachIndexed { index, source ->
                    PinnedStreamRow(
                        source = source,
                        canMoveUp = index > 0,
                        canMoveDown = index < pinned.lastIndex,
                        onMove = { move -> viewModel.move(source.id, move) },
                        onUnpin = { viewModel.unpin(source.id) }
                    )
                }
            }
            Button(
                onClick = onAddChannels,
                modifier = Modifier.padding(top = SPACING_SMALL).testTag("wearStreamPinsAdd")
            ) {
                Text(stringResource(R.string.wear_stream_pins_add))
            }
            PushPinsNowRow(viewModel)
        }
    }
}

@Composable
private fun PinnedStreamRow(
    source: PinnedStreamChannel,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (PinnedStreamMove) -> Unit,
    onUnpin: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = source.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        RowIconButton(
            iconRes = R.drawable.ic_arrow_upward,
            label = stringResource(R.string.wear_stream_pins_move_up),
            enabled = canMoveUp,
            tag = "wearStreamPinMoveUp",
            onClick = { onMove(PinnedStreamMove.UP) }
        )
        RowIconButton(
            iconRes = R.drawable.ic_arrow_downward,
            label = stringResource(R.string.wear_stream_pins_move_down),
            enabled = canMoveDown,
            tag = "wearStreamPinMoveDown",
            onClick = { onMove(PinnedStreamMove.DOWN) }
        )
        // The filled pin, as on a pinned row of the streams list: the glyph shows the state the tap ends.
        RowIconButton(
            iconRes = R.drawable.ic_pin,
            label = stringResource(R.string.streams_unpin),
            enabled = true,
            tag = "wearStreamPinUnpin",
            onClick = onUnpin
        )
    }
}

@Composable
private fun RowIconButton(
    @DrawableRes iconRes: Int,
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag)) {
        Icon(painter = painterResource(iconRes), contentDescription = label)
    }
}

/** Same shape as the operations group's screenshot row: the button, then what came of the last press. */
@Composable
private fun PushPinsNowRow(viewModel: WearStreamPinsGroupViewModel) {
    val pushing by viewModel.pushInFlight.collectAsState()
    val outcome by viewModel.pushOutcome.collectAsState()

    OutlinedButton(
        onClick = viewModel::pushNow,
        enabled = !pushing,
        modifier = Modifier.padding(top = SPACING_SMALL).testTag("wearStreamPinsPushNow")
    ) {
        Text(stringResource(R.string.wear_stream_pins_push))
    }
    val line = outcome
    if (line != null && !pushing) {
        Text(
            text = stringResource(line),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = SPACING_SMALL).testTag("wearStreamPinsPushOutcome")
        )
    }
}
