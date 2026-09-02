package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val SQUARE_RATIO = 1f
private val CELL_MIN_TARGET = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val FALLBACK_INSET = 8.dp

private const val PLATE_ALPHA = 0.6f
private const val PLATE_LINE_SPACING = 1.25f
private const val PLATE_MAX_LINES = 4
private val PLATE_INSET = 3.dp
private val PLATE_MARGIN = 2.dp
private val PlateColor = Color.Black.copy(alpha = PLATE_ALPHA)

/**
 * How a cell places its caption.
 *
 * Carried as one value rather than as two parameters so [ThumbnailCell] keeps the parameter count it
 * already had; the two facts are also read together and never separately.
 *
 * @param maxLines lines the caption may occupy while it sits under the picture. The default
 * reproduces the single-line behaviour every caller had before the parameter existed, so a screen
 * showing short category or source names keeps the cell height it had.
 * @param overGroupIcon the screen's own statement that its fallback glyph stands for a whole class -
 * one `ic_cast` for every channel, one glyph per source type - and therefore does not identify the
 * item. It is only half the condition: see [ThumbnailCell].
 */
data class CellCaption(
    val maxLines: Int = 1,
    val overGroupIcon: Boolean = false
)

/**
 * One file cell, drawn identically by both watch file lists.
 *
 * The caption sits inside the click target rather than beside it: the file name is shown in every
 * view mode including the densest one, so the reachable area is the whole cell and the interactive
 * minimum has to be measured over picture plus caption, not over the picture alone.
 *
 * A caller that wants a long press passes [onLongClick] instead of stacking its own gesture detector
 * on the modifier: the caller's modifier is applied outside this cell's own click handler, so the
 * inner handler wins the down and the outer detector never fires at all (S1953).
 *
 * **Where the caption goes (S2177).** Over the picture when [CellCaption.overGroupIcon] is set *and*
 * this cell is actually showing its fallback glyph; under it otherwise. Both halves are needed and
 * neither is sufficient: a Home tile is permanently [WearThumbnail.Unavailable] yet its glyph differs
 * per section, and a channel cell that resolved its favicon is showing a picture of its own. This
 * composable is the only place that sees both facts, which is why the choice is made here.
 *
 * @param fallback the placeholder slot, drawn when no picture is available. The [Modifier] handed to
 * it **is** the placeholder contract (S2003): the glyph fills the cell less [FALLBACK_INSET] per
 * side, and a caller applies that modifier rather than sizing its own glyph. The inset is a constant
 * and not a fraction, so the densest column count still gets the largest glyph the cell can hold.
 * Passing the rule as a parameter is what makes it inherited: a convention would be forgotten on the
 * next screen, a parameter cannot be.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThumbnailCell(
    thumbnail: WearThumbnail,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    captionLayout: CellCaption = CellCaption(),
    fallback: @Composable (Modifier) -> Unit
) {
    val captionOverIcon = captionLayout.overGroupIcon && thumbnail !is WearThumbnail.Ready
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = CELL_MIN_TARGET, minHeight = CELL_MIN_TARGET)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // A cell announces the file by its own name, so the reading never degrades to a position.
            .semantics { contentDescription = caption },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The glyph is now the cell's visible extent, so there is no edge left to draw: the border
        // existed only because a 24 dp icon in a full-width cell left nothing to see.
        val square = Modifier
            .fillMaxWidth()
            .aspectRatio(SQUARE_RATIO)
            .clip(WearCellShape)
        // Only the overlay needs to know the square's height, and BoxWithConstraints buys that with a
        // SubcomposeLayout. Charging every cell in a scrolling watch grid for a measurement half of
        // them never read is why the two cases are separate nodes rather than one parameterised node.
        if (captionOverIcon) {
            BoxWithConstraints(modifier = square, contentAlignment = Alignment.Center) {
                CellPicture(thumbnail = thumbnail, fallback = fallback)
                CaptionPlate(caption = caption, maxLines = linesFitting(maxHeight))
            }
        } else {
            Box(modifier = square, contentAlignment = Alignment.Center) {
                CellPicture(thumbnail = thumbnail, fallback = fallback)
            }
        }
        if (!captionOverIcon) {
            WearCaptionText(
                text = caption,
                maxLines = captionLayout.maxLines,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * "As many lines as fit", read off the cell instead of fixed by a caller.
 *
 * Measured at [WearCaptionScale.Floor] rather than at the ceiling because the caption shrinks toward
 * that floor before it truncates, so the floor is the size at which the most lines are actually
 * reachable. The [PLATE_MAX_LINES] ceiling is not taste: a fifth line in the densest three-column
 * cell needs a size below that floor, and below it a caption on round glass reads worse than a
 * truncated one - the finding S2129 already paid for.
 */
@Composable
private fun linesFitting(available: Dp): Int {
    val lineHeight = with(LocalDensity.current) {
        (WearCaptionScale.Floor.value * PLATE_LINE_SPACING).sp.toDp()
    }
    val usable = available - PLATE_INSET * 2
    return (usable / lineHeight).toInt().coerceIn(1, PLATE_MAX_LINES)
}

/**
 * The caption written on the glyph, on a plate that covers the text and nothing else.
 *
 * Full-bleed tinting would hide what the owner asked the icon to keep doing - the glyph still tells
 * one type from another by its colour and its edges, now read above and below the plate rather than
 * behind the text.
 */
@Composable
private fun CaptionPlate(caption: String, maxLines: Int) {
    WearCaptionText(
        text = caption,
        maxLines = maxLines,
        textAlign = TextAlign.Center,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PLATE_MARGIN)
            .background(PlateColor, WearCellShape)
            .padding(PLATE_INSET)
    )
}

/**
 * A read still running draws the same icon as a file that will never carry one: the cell must not
 * flash a spinner per item while a folder resolves, and the icon is already the honest placeholder.
 */
@Composable
private fun CellPicture(
    thumbnail: WearThumbnail,
    fallback: @Composable (Modifier) -> Unit
) {
    when (thumbnail) {
        is WearThumbnail.Ready -> Image(
            bitmap = thumbnail.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Each list keeps the icon it already showed, so a file with no picture looks unchanged.
        WearThumbnail.Unavailable, WearThumbnail.Loading -> fallback(Modifier.fillMaxSize().padding(FALLBACK_INSET))
    }
}
