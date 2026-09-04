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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.LocalContentColor
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

/** Opacity of the secondary line: subordinate to the caption, still white so it clears the wallpaper. */
private const val SECONDARY_TEXT_ALPHA = 0.7f

/**
 * S2526: A single-column item cell that retains the rich square icon/thumbnail tile layout from grid mode.
 *
 * Instead of degrading into uniform Chip buttons in single-column / list mode, this composable renders
 * the square icon/thumbnail tile beside its caption (with [WearCellShape] and matching
 * placeholder/thumbnail scaling), plus an optional secondary line under the caption.
 *
 * DELIBERATE, DO NOT "RESTORE" - three decisions here look like omissions and are not (owner ruling
 * 2026-09-04, judged on the watch, screenshots under `temp/scratch/`):
 *
 * - **No chip plate.** The row paints NO background and takes no `ChipColors`. It carried
 *   `ChipDefaults.primaryChipColors()`, and the owner called the resulting blue-grey slab ugly. What
 *   stands behind a row now is the app wallpaper. Re-adding a background is a product change, not a
 *   cleanup; the six call sites that used to pass `colors` were emptied in the same change, so a
 *   re-added parameter would arrive unused.
 * - **White caption with a black outline**, stated here rather than inherited. With the plate gone
 *   there is no known colour behind the text - the wallpaper moves and can be any picture - so the
 *   outline pass is the only thing keeping it legible without drawing a box back in. An earlier
 *   revision took the colour from the chip's own `contentColor`, which was the correct fix WHILE a
 *   plate existed and is wrong without one: `onPrimary` is near-black and vanishes on the wallpaper.
 * - **The pair is centred, so the icon column is ragged.** `horizontalArrangement = Arrangement.Center`
 *   plus a non-filling weight put the tile and its label in the middle of the row together. Icons
 *   therefore start at a different x on every row, by design: a left-pinned pair left the right half
 *   of a round display empty, which the owner read as a broken layout. Aligning the icons again
 *   brings that back.
 *
 * @param thumbnail image bitmap or placeholder state.
 * @param caption main text label for the item.
 * @param onClick primary click action.
 * @param modifier external layout modifier.
 * @param onLongClick optional long-press gesture handler.
 * @param captionLayout layout rules for caption lines.
 * @param secondaryText optional detail line (e.g. file size, duration, server address).
 * @param selected whether the cell is highlighted with a selection border.
 * @param fallback composable slot for rendering fallback icons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
// A cell is a layout with slots, not a data carrier: the count is thumbnail + caption + the two
// click roles + four presentation options + one composable slot, each already documented above.
// Bundling them into a parameter object would hide which ones a call site actually sets.
@Suppress("LongParameterList")
fun SingleColumnTileCell(
    thumbnail: WearThumbnail,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    captionLayout: CellCaption = CellCaption(),
    secondaryText: String? = null,
    selected: Boolean = false,
    fallback: @Composable (Modifier) -> Unit
) {
    val containerShape = WearCellShape
    Timber.d("S2526: SingleColumnTileCell composed for %s", caption)
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
        verticalAlignment = Alignment.CenterVertically,
        // The tile and its caption are centred as ONE pair rather than pinned to the left edge. A
        // full-width row whose content starts at the left leaves the right half of a round watch
        // empty, which reads as a broken layout rather than as spare room (owner ruling 2026-09-04).
        // The cost is a ragged icon column down the list, accepted deliberately: the alternative was
        // enlarging the caption, which sizes each row differently because the caption auto-shrinks.
        horizontalArrangement = Arrangement.Center
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

        // No plate stands behind the text any more, so the caption states its own colours: white
        // glyphs stroked in black (S2467's outline pass). What is behind a row is now the wallpaper -
        // a moving, arbitrary picture - and an outline is the only treatment that survives it without
        // drawing a box back in.
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                modifier = Modifier
                    // `fill = false` is what makes the centring visible: a filled weight would take
                    // the whole remaining width and re-create the empty right side the pair is being
                    // moved away from. The weight is still declared so a long caption is capped by
                    // the row instead of pushing the tile off the glass.
                    .weight(1f, fill = false)
                    .padding(start = SINGLE_COLUMN_TEXT_START_PADDING, end = SINGLE_COLUMN_ROW_PADDING_HORIZONTAL),
                verticalArrangement = Arrangement.Center
            ) {
                WearCaptionText(
                    text = caption,
                    maxLines = captionLayout.maxLines.coerceAtLeast(1),
                    textAlign = TextAlign.Start,
                    color = Color.White,
                    outlineColor = Color.Black
                )
                if (!secondaryText.isNullOrEmpty()) {
                    Text(
                        text = secondaryText,
                        style = MaterialTheme.typography.caption2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = SECONDARY_TEXT_ALPHA)
                    )
                }
            }
        }
    }
}
