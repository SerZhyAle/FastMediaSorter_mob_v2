package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private val SINGLE_COLUMN_TILE_SIZE: Dp = 52.dp
private val SINGLE_COLUMN_ROW_MIN_HEIGHT: Dp = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val SINGLE_COLUMN_TEXT_START_PADDING: Dp = 8.dp
private val SINGLE_COLUMN_ROW_PADDING_HORIZONTAL: Dp = 4.dp
private val SINGLE_COLUMN_ROW_PADDING_VERTICAL: Dp = 2.dp
private val SELECTION_BORDER_WIDTH: Dp = 2.dp

/**
 * S2526: A single-column item cell that retains the rich square icon/thumbnail tile layout from grid mode.
 *
 * Instead of degrading into uniform Chip buttons in single-column / list mode, this composable renders
 * the square icon/thumbnail tile on the left (with [WearCellShape] and matching placeholder/thumbnail
 * scaling) and the caption text (along with optional secondary text) on the right.
 *
 * @param thumbnail image bitmap or placeholder state.
 * @param caption main text label for the item.
 * @param onClick primary click action.
 * @param modifier external layout modifier.
 * @param onLongClick optional long-press gesture handler.
 * @param captionLayout layout rules for caption lines.
 * @param secondaryText optional detail line (e.g. file size, duration, server address).
 * @param selected whether the cell is highlighted with a selection border.
 * @param colors background and content color definitions.
 * @param fallback composable slot for rendering fallback icons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleColumnTileCell(
    thumbnail: WearThumbnail,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    captionLayout: CellCaption = CellCaption(),
    secondaryText: String? = null,
    selected: Boolean = false,
    colors: ChipColors = ChipDefaults.primaryChipColors(),
    fallback: @Composable (Modifier) -> Unit
) {
    val containerShape = WearCellShape
    Timber.d("S2526: SingleColumnTileCell composed for %s", caption)
    val backgroundPainter = colors.background(enabled = true).value
    val borderModifier = if (selected) {
        Modifier.border(SELECTION_BORDER_WIDTH, MaterialTheme.colors.primary, containerShape)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = SINGLE_COLUMN_ROW_MIN_HEIGHT)
            .clip(containerShape)
            .paint(painter = backgroundPainter, contentScale = ContentScale.Crop)
            .then(borderModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button
            )
            .semantics { contentDescription = caption }
            .padding(
                horizontal = SINGLE_COLUMN_ROW_PADDING_HORIZONTAL,
                vertical = SINGLE_COLUMN_ROW_PADDING_VERTICAL
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(SINGLE_COLUMN_TILE_SIZE)
                .clip(containerShape)
                .background(MaterialTheme.colors.surface, containerShape),
            contentAlignment = Alignment.Center
        ) {
            CellPicture(thumbnail = thumbnail, fallback = fallback)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = SINGLE_COLUMN_TEXT_START_PADDING, end = SINGLE_COLUMN_ROW_PADDING_HORIZONTAL),
            verticalArrangement = Arrangement.Center
        ) {
            WearCaptionText(
                text = caption,
                maxLines = captionLayout.maxLines.coerceAtLeast(1),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            if (!secondaryText.isNullOrEmpty()) {
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.caption2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
