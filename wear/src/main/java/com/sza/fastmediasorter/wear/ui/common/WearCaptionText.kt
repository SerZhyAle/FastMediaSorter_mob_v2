package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.LocalTextStyle
import androidx.wear.compose.material.Text
import timber.log.Timber

private const val CEILING_SP = 16f
private const val FLOOR_SP = 11f
private const val STEP_SP = 1f

/**
 * The owner requested double the first-pass width after bright type icons still overwhelmed the white
 * fill. The on-watch handoff checks that this wider stroke remains legible at [WearCaptionScale.Floor].
 */
private val OUTLINE_WIDTH = 2.dp

/**
 * The size rules every watch list row and cell caption obeys.
 *
 * S2129: the watch module had no auto-sizing at all - each screen took a different library style,
 * which is why captions read as ranging "from small to very small" while looking bigger on a
 * one-word label. The fix is one scale rather than a raised constant, so a screen inherits the
 * rule instead of restating a number.
 *
 * The ceiling and floor are fixed by the strategic spec (§3.3), not chosen here: below the floor
 * a caption on round glass is worse than truncated, so shrinking stops and ellipsis takes over.
 */
object WearCaptionScale {

    /** Starting size for every caption. */
    val Ceiling: TextUnit = CEILING_SP.sp

    /** Smallest size shrinking may reach; below this the caption truncates instead. */
    val Floor: TextUnit = FLOOR_SP.sp

    /**
     * The next size down from [current], or null once the floor is reached.
     *
     * Total and terminating by construction, which is the property the shrink loop rests on: a step
     * that could answer below the floor, or never answer null, would recompose forever. A [current]
     * already below the floor answers null rather than stepping further down.
     */
    fun nextSmaller(current: TextUnit): TextUnit? {
        val candidate = current.value - STEP_SP
        if (candidate < FLOOR_SP) {
            return null
        }
        return candidate.sp
    }
}

/**
 * A caption that starts at [WearCaptionScale.Ceiling] and steps down until it fits.
 *
 * Hand-rolled on purpose: `androidx.wear.compose:compose-material` is pinned at 1.2.1 on Compose
 * BOM 2024.02.00, where `Text` has no `autoSize` parameter. Do not swap this for a library call
 * that does not exist at this version.
 *
 * The chosen size is remembered per [text] and [maxLines] so it settles on one value instead of
 * oscillating between two. A width change alone - a column-count change, not something the watch
 * does while a list is on screen - therefore keeps the size already settled, which can be smaller
 * than the new width would allow but is never too large to fit.
 *
 * Colour and the remaining typography come from the ambient text style, so a caller inside a chip
 * slot keeps that slot's colour; only the size is owned here.
 *
 * @param color overrides that inherited colour. The default [Color.Unspecified] is Compose's own
 * "keep whatever the ambient style says", so a caller that does not ask is unaffected. A caption
 * drawn over a picture has to state its colour, because the slot it inherits from knows nothing about
 * what is now behind it (S2177).
 * @param outlineColor turns on a second, lower drawing pass that strokes the same glyphs, so the
 * caption reads over an arbitrary picture without anything being drawn behind it (S2467). Both passes
 * take their size from the one [WearCaptionScale] state below, which is the whole reason this lives
 * here and not in the calling cell: two separate calls would each shrink on their own measurement and
 * the outline would slide off the fill on the first long file name.
 */
@Composable
fun WearCaptionText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    outlineColor: Color = Color.Unspecified
) {
    var size by remember(text, maxLines) { mutableStateOf(WearCaptionScale.Ceiling) }
    val shrinkOnOverflow: (TextLayoutResult) -> Unit = { result ->
        if (result.hasVisualOverflow) {
            WearCaptionScale.nextSmaller(size)?.let { smaller -> size = smaller }
        }
    }
    if (outlineColor == Color.Unspecified) {
        Text(
            text = text,
            color = color,
            fontSize = size,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = modifier,
            onTextLayout = shrinkOnOverflow
        )
        return
    }
    Timber.d("S2467: WearCaptionText draws the 2 dp outlined caption")
    val outlineStyle = LocalTextStyle.current.copy(
        drawStyle = Stroke(
            width = with(LocalDensity.current) { OUTLINE_WIDTH.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    Box(modifier = modifier) {
        // The stroke pass reports no layout on purpose. Both passes read the same `size`, so letting
        // each report an overflow would step the scale twice per frame and the caption would jump two
        // sizes down at once instead of one.
        Text(
            text = text,
            color = outlineColor,
            fontSize = size,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
            style = outlineStyle
        )
        Text(
            text = text,
            color = color,
            fontSize = size,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
            onTextLayout = shrinkOnOverflow
        )
    }
}
