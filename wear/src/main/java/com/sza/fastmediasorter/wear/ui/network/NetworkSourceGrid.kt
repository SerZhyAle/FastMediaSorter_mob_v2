package com.sza.fastmediasorter.wear.ui.network

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val CELL_BUTTON_SIZE = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val CELL_ICON_SIZE = 24.dp

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
    Chip(
        onClick = { actions.onSourceClick(source.id, source.name) },
        label = {
            Text(text = "${source.name}\n${source.server}")
        },
        secondaryLabel = {
            Text(
                text = stringResource(R.string.hold_to_delete),
                style = MaterialTheme.typography.caption2
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(source.id) {
                detectTapGestures(onLongPress = { actions.onDeleteClick(source) })
            },
        colors = ChipDefaults.primaryChipColors()
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
    Column(
        // A cell announces the resource by its own name, so the reading never degrades to a position.
        modifier = modifier.semantics { contentDescription = source.name },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { actions.onSourceClick(source.id, source.name) },
            // CELL_BUTTON_SIZE is the interactive minimum itself, so a grid cell keeps a reachable
            // target no matter which view mode produced it.
            modifier = Modifier
                .size(CELL_BUTTON_SIZE)
                .pointerInput(source.id) {
                    detectTapGestures(onLongPress = { actions.onDeleteClick(source) })
                },
            colors = ButtonDefaults.primaryButtonColors()
        ) {
            Icon(
                imageVector = Icons.Filled.Storage,
                contentDescription = null,
                modifier = Modifier.size(CELL_ICON_SIZE)
            )
        }
        Text(
            text = source.name,
            style = MaterialTheme.typography.caption3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
