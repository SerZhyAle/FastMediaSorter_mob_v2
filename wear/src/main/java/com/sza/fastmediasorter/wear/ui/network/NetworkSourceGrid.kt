package com.sza.fastmediasorter.wear.ui.network

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.ui.common.LongPressChip
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val CELL_ICON_SIZE = 24.dp

/**
 * The per-type glyph, taken from the phone's own vectors so one entity wears one icon across both
 * apps - the rule `HomeScreen.iconFor` already follows. Before S1952 every row drew the same
 * generic Storage mark, so a NAS share and a cloud account were indistinguishable.
 */
@DrawableRes
private fun iconFor(type: NetworkSourceType): Int = when (type) {
    NetworkSourceType.SMB -> R.drawable.ic_resource_smb
    NetworkSourceType.FTP -> R.drawable.ic_resource_ftp
    NetworkSourceType.SFTP -> R.drawable.ic_resource_sftp
    NetworkSourceType.GOOGLE_DRIVE -> R.drawable.ic_resource_cloud
}

/** One column keeps the informative chip; a grid trades the server line for a reachable cell. */
internal fun ScalingLazyListScope.sourceItems(
    sources: List<SourceItem>,
    columns: Int,
    actions: NetworkSourcesActions
) {
    if (columns == SINGLE_COLUMN) {
        items(sources) { source ->
            SourceChip(source = source, actions = actions)
        }
    } else {
        items(sources.chunked(columns)) { rowSources ->
            SourceRow(sources = rowSources, columns = columns, actions = actions)
        }
    }
}

@Composable
private fun SourceChip(
    source: SourceItem,
    actions: NetworkSourcesActions
) {
    // A library Chip owns its clickable and applies the caller's modifier outside it, so no detector
    // passed in from here can ever see the press - the row has to carry both gestures itself (S1953).
    LongPressChip(
        onClick = {
            actions.onSourceClick(source.id, source.name)
        },
        onLongClick = {
            actions.onSourceLongPress(source)
        },
        label = {
            Text(text = "${source.name}\n${source.server}")
        },
        modifier = Modifier.fillMaxWidth(),
        icon = {
            Icon(
                painter = painterResource(id = iconFor(source.type)),
                contentDescription = null,
                modifier = Modifier.size(CELL_ICON_SIZE),
                tint = Color.Unspecified
            )
        },
        secondaryLabel = {
            Text(
                text = stringResource(R.string.hold_to_delete),
                style = MaterialTheme.typography.caption2
            )
        }
    )
}

/** A short row is padded with empty weights so its cells keep the width of a full row's cells. */
@Composable
private fun SourceRow(
    sources: List<SourceItem>,
    columns: Int,
    actions: NetworkSourcesActions
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        sources.forEach { source ->
            SourceCell(
                source = source,
                modifier = Modifier.weight(1f),
                actions = actions
            )
        }
        repeat(columns - sources.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SourceCell(
    source: SourceItem,
    modifier: Modifier,
    actions: NetworkSourcesActions
) {
    ThumbnailCell(
        thumbnail = WearThumbnail.Unavailable,
        caption = source.name,
        onClick = {
            actions.onSourceClick(source.id, source.name)
        },
        modifier = modifier,
        // The cell's own handler serves both gestures; a detector layered on `modifier` would sit
        // outside it and never fire, which is how the reshape in S1970 lost the long press (S1953).
        onLongClick = {
            actions.onSourceLongPress(source)
        }
    ) { glyphModifier ->
        Icon(
            painter = painterResource(id = iconFor(source.type)),
            contentDescription = null,
            modifier = glyphModifier,
            tint = Color.Unspecified
        )
    }
}
