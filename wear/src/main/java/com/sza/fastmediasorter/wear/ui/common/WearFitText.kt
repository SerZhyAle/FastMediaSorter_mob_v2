package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Text

/** Each overflowing pass keeps this share of the previous size, so the search converges in a few frames. */
private const val SHRINK_FACTOR = 0.9f

/**
 * Below this share of the caller's size the glyph is smaller than the keypad's own gap and reads as a
 * smudge, so shrinking stops and the ellipsis takes over instead.
 */
private const val MIN_SCALE = 0.5f

/**
 * A single-line label that shrinks until it fits the box it was given, then ellipsizes.
 *
 * For the one place where growing is not an option: the calculator keypad is sized by the round glass
 * (S2152, S2493), so a key cannot get taller to hold a label the system font scale enlarged. Without
 * this the label is cut mid-glyph, which is the defect Play photographed in S2755.
 *
 * Distinct from [WearCaptionText] on purpose. That one owns its own sp ceiling and floor because every
 * caption in the module shares one scale; here the ceiling is whatever the caller's [style] already
 * says, since a keypad's per-key type steps are what make the ink on each key comparable and must
 * survive the fit. The size is still expressed in sp, so it keeps tracking the system font scale - the
 * shrink only removes the part that would not fit.
 *
 * The scale is remembered per [text] and per the caller's size, so a settled key does not re-measure
 * and a font-scale change starts the search again from the top.
 */
@Composable
fun WearFitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null
) {
    var scale by remember(text, style.fontSize) { mutableStateOf(1f) }
    Text(
        text = text,
        style = style,
        fontSize = style.fontSize * scale,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && scale > MIN_SCALE) {
                scale = (scale * SHRINK_FACTOR).coerceAtLeast(MIN_SCALE)
            }
        }
    )
}
