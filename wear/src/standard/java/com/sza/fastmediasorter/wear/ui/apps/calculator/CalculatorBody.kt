package com.sza.fastmediasorter.wear.ui.apps.calculator

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sza.fastmediasorter.wear.ui.common.wearMaxSquareSide
import com.sza.fastmediasorter.wear.ui.common.wearRingInset
import timber.log.Timber

/**
 * S3192: the standard calculator placement - the whole calculator stands inside the largest square
 * the glass draws whole (owner ruling 2026-09-16: a separate calculator for the store build).
 *
 * The noLegal layout takes the chord over each band's worst edge, which puts that band's outer corners
 * exactly ON the arc. `clip-check` judges a corner past the radius by a single pixel as off the glass,
 * and the value row has no scrolling ancestor to excuse it, so a layout that is right only to the
 * rounding is not a layout a review can pass. [wearMaxSquareSide] is 0.70 of the display, short of
 * the side at which a square's corners touch the circle, so every corner here stands inside the glass.
 *
 * The keypad viewport ends at the square's bottom edge and is as wide as the square, so a row can
 * stand nowhere outside the square at any scroll offset - which is also why this placement needs no
 * trailing space below the clear row and no branch on the geometry mode: the store build draws only
 * the store view.
 */
@Composable
internal fun CalculatorBody(
    uiState: CalculatorUiState,
    keypadScrollState: ScrollState,
    onOperation: (String) -> Unit,
    onCopy: (String) -> Unit,
    onKey: (CalculatorKey) -> Unit,
    onLongKey: (CalculatorKey) -> Unit,
    onLeave: () -> Unit
) {
    val squareSide = wearMaxSquareSide()
    val squareInset = wearRingInset()
    LaunchedEffect(squareSide) {
        Timber.d("S3192: standard calculator body squareSide=$squareSide squareInset=$squareInset")
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CalculatorValueRow(
            uiState = uiState,
            placement = Modifier
                .width(squareSide)
                .padding(top = squareInset),
            verticalAlignment = Alignment.CenterVertically,
            onOperation = onOperation,
            onCopy = onCopy
        )
        Column(
            modifier = Modifier
                .width(squareSide)
                .weight(1f)
                // The bottom padding sits BEFORE the scroll, so it shortens the viewport rather than
                // the content: a row scrolled to the bottom then stops at the square's edge.
                .padding(bottom = squareInset)
                .verticalScroll(keypadScrollState)
                .padding(PaddingValues(vertical = KEY_GAP)),
            verticalArrangement = Arrangement.spacedBy(KEY_GAP)
        ) {
            CalculatorKeypadContent(onKey = onKey, onLongKey = onLongKey, onLeave = onLeave)
        }
    }
}
