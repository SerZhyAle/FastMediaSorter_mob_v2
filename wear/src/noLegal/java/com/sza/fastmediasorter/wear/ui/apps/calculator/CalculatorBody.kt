package com.sza.fastmediasorter.wear.ui.apps.calculator

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode
import com.sza.fastmediasorter.wear.ui.common.LocalWearGeometryMode
import com.sza.fastmediasorter.wear.ui.common.wearChordInset
import com.sza.fastmediasorter.wear.ui.common.wearRingInset
import com.sza.fastmediasorter.wear.ui.common.wearScrollViewportInset
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionScroll

// S3192: the noLegal calculator placement. It is the S3104 layout moved here unchanged - the owner's
// own calculator (ruling 2026-09-16), which the standard build replaces with its own placement.

/**
 * S3104: how much of the value glyph's own height the row drops by, as a divisor of that height
 * (owner ruling 2026-09-14).
 *
 * Stated against the type rather than as a dp literal, so the drop follows the value's size if the
 * watch palette or the type scale is ever restated.
 */
private const val VALUE_ROW_DROP_DIVISOR = 2

/**
 * S2007: empty space below the last row, so the bottom row can be scrolled to the middle of the
 * display instead of being parked against the rim.
 *
 * A round screen narrows towards its edges, and the scaling this keypad dropped was quietly paying
 * for that: a full-size row at the end of the viewport has its outer keys off the glass. Measured on
 * a 480 px round emulator at maximum scroll, the clear key's tap centre landed about 15 px outside a
 * circle of radius 240 - the key could not be pressed at all. The space survives the 2026-08-26
 * ruling that halved the key: a shorter row is likelier to fit at rest, which makes this cheap
 * insurance rather than dead weight, and it costs nothing until scrolled to. This is not the
 * `autoCentering` padding ADR-2
 * rejected: that sat ABOVE the first row and was why the keypad opened on emptiness, while this sits
 * below the last row and costs nothing until the user scrolls down to it.
 */
private val KEYPAD_TRAILING_SPACE = KEY_HEIGHT * 2

/**
 * Owner ruling 2026-09-26, read on a 480 px round watch: the back arrow beside the clear key moves
 * half its own body up and half its body to the right. The body is the 24 dp glyph the arrow draws,
 * not its 40 dp touch box, so the arrow rises into the guard gap without reaching the `=` key above.
 */
private const val BACK_AFFORDANCE_NUDGE_DP = 12

/**
 * S2273: every width this screen places content by, read once so the value row and the keypad cannot
 * drift apart the way they did when one used the module's inset and the other a flat 6 dp.
 */
private data class CalculatorShape(
    val valueRowWidth: Dp,
    val valueRowTop: Dp,
    val keypadPadding: PaddingValues,
    /**
     * S2770: how far above the bottom of the display the keypad's VIEWPORT ends.
     *
     * Bounding the viewport is what makes [keypadPadding] answerable at all: a scrolling row passes
     * through every height its viewport spans, so the width it must fit is the chord at the viewport's
     * worst edge, not the chord where the row happens to rest.
     */
    val keypadViewportBottom: Dp
)

/**
 * S3104: half the height of a digit in the value row, which is how far the row drops (owner ruling
 * 2026-09-14).
 *
 * Read off the style the value is drawn in rather than off a literal, because the whole point of the
 * drop is that the row clears its own glyph by a stated fraction of it.
 */
@Composable
private fun valueRowDrop(): Dp {
    val valueFontSize = MaterialTheme.typography.title1.fontSize
    return with(LocalDensity.current) { valueFontSize.toDp() } / VALUE_ROW_DROP_DIVISOR
}

/**
 * S2273: where the round glass lets this screen put things.
 *
 * S3104: the value row no longer takes the inscribed square's side. That side is one number for the
 * whole glass, so it answers for the worst band of the circle wherever the row actually stands - and
 * once the row is lowered it stands where the chord is wider, which is exactly what the owner asked
 * the drop to buy (two more digits on a 480 px round watch). The width now comes from the chord over
 * the row's own TOP edge, which is the worst edge for anything standing above the centre of the
 * glass, through the same [wearChordInset] the keypad below it already uses.
 *
 * S2770: the keypad no longer takes the ordinary `wearScreenInsets`. That inset is uniform, so it
 * inscribes a RECTANGLE in a round display - the middle of a row gets clearance to spare while the
 * outer keys of the top and bottom rows stand off the arc. Measured on the Galaxy Watch 7, the corner
 * keys of the last row put 3205 px and 3284 px of ink outside a 240 px radius and the backspace key's
 * own centre landed 248.7 px out, past any finger. Bounding the viewport with [wearRingInset] and
 * taking the width from [wearChordInset] over that same edge fits every row at every scroll offset,
 * because no row can reach lower than the viewport it scrolls inside.
 */
@Composable
private fun calculatorShape(): CalculatorShape {
    // S2773: the mode-aware form of the bound S2770 introduced. In the store view it is still
    // `wearRingInset()`; in the original view it is zero, which returns the full-height viewport whose
    // outer keys the glass cuts.
    val isOriginal = LocalWearGeometryMode.current == WearGeometryMode.ORIGINAL
    val viewportBottom = wearScrollViewportInset()
    val sideInset = wearChordInset(viewportBottom)
    val valueRowTop = (if (isOriginal) 0.dp else wearRingInset()) + valueRowDrop()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val shape = CalculatorShape(
        valueRowWidth = screenWidth - wearChordInset(valueRowTop) * 2,
        valueRowTop = valueRowTop,
        keypadPadding = PaddingValues(
            start = sideInset,
            top = KEY_GAP,
            end = sideInset,
            bottom = KEY_GAP + KEYPAD_TRAILING_SPACE
        ),
        keypadViewportBottom = viewportBottom
    )
    return shape
}

/**
 * The scrolling keypad, drawn under the value row and inside the arc.
 *
 * Its own composable rather than a block inside the screen so the viewport bound and the chord inset
 * that S2770 introduced sit next to each other and cannot be read apart.
 */
@Composable
private fun ColumnScope.CalculatorKeypad(
    shape: CalculatorShape,
    scrollState: ScrollState,
    onKey: (CalculatorKey) -> Unit,
    onLongKey: (CalculatorKey) -> Unit,
    onLeave: () -> Unit
) {
    val isOriginal = LocalWearGeometryMode.current == WearGeometryMode.ORIGINAL
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            // S2770: the bottom padding sits BEFORE the scroll, so it shortens the viewport instead
            // of the content. A row scrolled to the bottom of a full-height viewport stands where the
            // chord is shortest, which no content padding can undo.
            .padding(bottom = shape.keypadViewportBottom)
            // Bound to the same condition as the touch scroll below: the ORIGINAL geometry fits the
            // keypad whole, and the crown must not move a column the layout holds still.
            .then(if (isOriginal) Modifier else Modifier.rotaryActionScroll(scrollState))
            .verticalScroll(scrollState, enabled = !isOriginal)
            .padding(shape.keypadPadding),
        verticalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        CalculatorKeypadContent(
            onKey = onKey,
            onLongKey = onLongKey,
            onLeave = onLeave,
            // Owner ruling 2026-09-26: `C` sits in the key's top-right corner, not at its middle.
            clearLabelAlignment = Alignment.TopEnd,
            backAffordanceShift = DpOffset(x = BACK_AFFORDANCE_NUDGE_DP.dp, y = -BACK_AFFORDANCE_NUDGE_DP.dp)
        )
    }
}

/**
 * S3192: the noLegal placement of the value row and the keypad - the S3104 layout, unchanged.
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
    val shape = calculatorShape()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CalculatorValueRow(
            uiState = uiState,
            // S3104: the keypad column's `top = KEY_GAP` is now the WHOLE gap between the value row
            // and the first keypad row, which is the owner's ruling of 2026-09-14 - the digits stand
            // off the keys by exactly what the keys stand off each other. The row keeps its
            // [TOUCH_TARGET] minimum height, so the free height that minimum leaves has to go
            // somewhere: aligning the children to the BOTTOM spends it above the digits, into the
            // empty band under the clock, instead of below them where the owner measured it as lost
            // space.
            placement = Modifier
                .width(shape.valueRowWidth)
                .padding(top = shape.valueRowTop),
            verticalAlignment = Alignment.Bottom,
            onOperation = onOperation,
            onCopy = onCopy
        )
        CalculatorKeypad(
            shape = shape,
            scrollState = keypadScrollState,
            onKey = onKey,
            onLongKey = onLongKey,
            onLeave = onLeave
        )
    }
}
