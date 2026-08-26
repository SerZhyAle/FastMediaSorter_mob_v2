package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val SQUARE_RATIO = 1f
private val CELL_MIN_TARGET = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val FALLBACK_INSET = 8.dp

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
 * @param captionMaxLines how many lines the caption may occupy. The default reproduces the previous
 * single-line behaviour for every caller that does not ask, so a screen showing short category or
 * source names keeps the cell height it had.
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
    captionMaxLines: Int = 1,
    fallback: @Composable (Modifier) -> Unit
) {
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(SQUARE_RATIO)
                .clip(WearCellShape),
            contentAlignment = Alignment.Center
        ) {
            CellPicture(thumbnail = thumbnail, fallback = fallback)
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.caption3,
            maxLines = captionMaxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
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
