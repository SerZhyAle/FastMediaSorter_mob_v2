package com.sza.fastmediasorter.wear.ui.player.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode
import com.sza.fastmediasorter.wear.ui.common.LocalWearGeometryMode
import com.sza.fastmediasorter.wear.ui.common.wearIsCompactScreen
import timber.log.Timber

// Declared as const rather than as a `val ..: Dp` because detekt's MagicNumber is active on this
// module's main sources and exempts a constant declaration but not a property one.
private const val COMMAND_TOUCH_TARGET_DP = 48
private const val COMMAND_GLYPH_DP = 32
private const val BACK_GLYPH_DP = 24
private val COMMAND_GRID_GAP = 4.dp
private const val COMMAND_GRID_COLUMNS = 4

/**
 * S2766: previous, play/pause and next - the three Google names as the primary media commands, and
 * the count at which a 48 dp target fits the small round glass, where four never can.
 */
internal const val PRIMARY_ROW_COLUMNS = 3

/**
 * S2803: the ORIGINAL view restores the pre-S2766 row of four on every glass width - strategic §6
 * item 2 records that no width threshold existed in this layer before the Play edits.
 */
private const val ORIGINAL_ROW_COLUMNS = 4

private const val SECONDARY_ROW_COLUMNS_COMPACT = 2
private const val SECONDARY_ROW_COLUMNS_WIDE = 3

/**
 * How many commands the primary row carries in the view in force.
 *
 * S2803: STORE keeps the three Google names, the count at which a 48 dp target fits the small round
 * glass; ORIGINAL restores the row of four. The players ask this answer and never read the geometry
 * mode themselves - the same property that let the Play shape fix reach every screen as one change.
 */
@Composable
internal fun playerPrimaryRowColumns(): Int =
    if (LocalWearGeometryMode.current == WearGeometryMode.ORIGINAL) {
        ORIGINAL_ROW_COLUMNS
    } else {
        PRIMARY_ROW_COLUMNS
    }

/**
 * S2766: the secondary row carries two commands below the compact-screen breakpoint and three above
 * it. It lives here rather than in a screen because all three players draw the same row, and a
 * composition rule kept per screen is one that drifts per screen.
 *
 * S2803: the ORIGINAL view answers four on every width - the restored composition has no breakpoint
 * of its own - and the STORE comparison below is untouched.
 */
@Composable
internal fun secondaryRowColumns(): Int {
    if (LocalWearGeometryMode.current == WearGeometryMode.ORIGINAL) {
        return ORIGINAL_ROW_COLUMNS
    }
    val compact = wearIsCompactScreen()
    return if (compact) SECONDARY_ROW_COLUMNS_COMPACT else SECONDARY_ROW_COLUMNS_WIDE
}

/**
 * The width a [columns]-command row cannot go under, plus one gap of clearance on each side.
 *
 * S2273: a caller placing this row against a round display needs this before it can ask where the
 * glass is that wide. The clearance is one grid gap rather than a number of its own, because a row
 * placed for exactly its own width lands its outer corners ON the arc, which reads as a mark
 * touching the rim.
 *
 * S2766: the count is a parameter because a row lifted to where the glass is this wide charges the
 * column for the lift, and a shorter row stands lower and gives part of it back.
 *
 * S2766 also raised the per-cell floor from [COMMAND_GLYPH_DP] to [COMMAND_TOUCH_TARGET_DP]. The
 * glyph was the right floor while four commands shared the row: 48 dp each was arithmetically
 * impossible there, so the only question left was whether a mark overhung its neighbour. At three
 * commands the target fits, so the row is placed for the target - and the difference is not
 * cosmetic. Measured on emulator-5556 at 227 dp with the glyph floor: the row was lowered to where
 * the glass holds 112 dp, then charged the chord inset for standing there, and its cells came out
 * 42.7 dp wide. Because [wearBandEdgeOffset] and [wearChordInset] are exact inverses, placing the
 * row for the width it actually needs leaves exactly that width between its sides.
 *
 * @param columns how many cells the row will be divided into.
 */
@Composable
internal fun playerCommandBandWidth(columns: Int = COMMAND_GRID_COLUMNS): Dp {
    // S2803: the floor branches with the column count (strategic pillar 4 / ADR-2). Under ORIGINAL
    // the row is four cells wide again, four 48 dp targets cannot share a narrow round glass, and
    // the 48 dp floor exists for the store branch's review - so the original branch returns to the
    // glyph floor, the 148 dp band S2273 measured and lifted the row by.
    val cellFloor = if (LocalWearGeometryMode.current == WearGeometryMode.ORIGINAL) {
        COMMAND_GLYPH_DP.dp
    } else {
        COMMAND_TOUCH_TARGET_DP.dp
    }
    return cellFloor * columns + COMMAND_GRID_GAP * (columns + 1)
}

/**
 * Gives every player command row one equal-width four-cell grid.
 *
 * @param horizontalPadding clearance the caller has measured against its own screen, subtracted from
 * the cells rather than added around them, so the row keeps its columns instead of losing one.
 * @param columns how many cells the row divides into. S2766: below the compact-screen breakpoint a
 * player draws three primary and two secondary commands, because four 48 dp cells ask for 204 dp of
 * a 192 dp glass and three ask for 152 dp of a 153.6 dp content box.
 */
@Composable
internal fun PlayerCommandGrid(
    horizontalPadding: Dp = 0.dp,
    columns: Int = COMMAND_GRID_COLUMNS,
    content: @Composable RowScope.(Dp) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
    ) {
        val targetSize = (maxWidth - COMMAND_GRID_GAP * (columns - 1)) / columns
        Timber.d("S2479: PlayerCommandGrid cell size=%s", targetSize)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(COMMAND_GRID_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content(targetSize)
        }
    }
}

/**
 * The command surface of every watch player, and the only one they draw.
 *
 * Two sizes, and they are not the same thing: the [COMMAND_TOUCH_TARGET_DP] box is what a finger
 * hits, the [COMMAND_GLYPH_DP] icon is what an eye reads. Taking the plate away shrinks the mark,
 * never the target, which is why the box keeps its size with nothing painted in it.
 *
 * A caller passing [checked] must also pass a different [icon] per state. The tint is one signal and
 * the accessibility constraint asks for two, so a state told apart by colour alone is not told apart
 * on a watch held at arm's length or by an eye that does not separate those hues.
 *
 * S2766: the box is no longer square, and the two sides are unequal for different reasons. Width is
 * [size], which the glass fixes - four 48 dp commands ask for 204 dp of row and a small round watch is
 * 192 dp across, so no padding anywhere can buy that width back. Height is not fixed by anything: the
 * player column is laid out `SpaceEvenly` and carries slack between its rows, so the box takes the full
 * [COMMAND_TOUCH_TARGET_DP] there for free and the row keeps its horizontal arithmetic untouched.
 * Measured on emulator-5556 at 227 dp on 2026-09-09: 42.5 x 42.5 and 34.2 x 34.2 before, the same widths
 * at 48 dp tall after. The glyph stays tied to the WIDTH, because it is the narrow side that decides
 * whether a mark overhangs its neighbour.
 *
 * [size] is how a grid cell narrows the button, and it is a parameter rather than a `Modifier.size` the
 * caller chains on, because chaining one does NOT work: `Modifier.size(48.dp).then(caller)` fixes the
 * constraints at 48 dp first and the caller's own size is then coerced into them. Measured on
 * emulator-5556 at 192 dp on 2026-09-09 (S2273): every cell drew at 96 px while [PlayerCommandGrid] had
 * computed 71, the row therefore asked for 204 dp of a 154 dp container, `Row` gave the fourth child
 * the nothing that was left, and the accessibility tree carried it as bounds `[0,0][0,0]` - a command
 * that is not merely off-screen but unreachable by touch and by TalkBack alike. That is the shape
 * Google Play photographed. The KDoc that stood here claimed the opposite and was believed for a day.
 *
 * S2140: a button carrying [onLongClick] should also carry [onLongClickLabel]. Without it TalkBack
 * offers the gesture as a bare "double tap and hold", which names the motion and not the command, so a
 * user who cannot see the icon has no way to learn what holding it would do.
 *
 * S2140 also dropped an `enabled` parameter that no caller had ever passed. It defaulted to true and
 * every one of the three players took the default, so it disabled nothing; kept alongside the long-press
 * pair it would have pushed this list to detekt's LongParameterList threshold for a switch nobody threw.
 */
// Eight parameters, exactly as many as before S2273: `size` replaced the `modifier` no caller passed
// any more, so nothing was added. detekt re-raises its baselined finding whenever this signature is
// touched at all, and folding the long-press pair into an object to get under the threshold would
// change nine call sites to silence a count that did not move.
@Suppress("LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlayerCommandButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    size: Dp = COMMAND_TOUCH_TARGET_DP.dp,
    checked: Boolean? = null,
    iconTint: Color? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null
) {
    val isBackIcon = icon.name == Icons.AutoMirrored.Filled.ArrowBack.name
    val tint = if (isBackIcon) {
        MaterialTheme.colors.secondary
    } else if (iconTint != null && checked != false) {
        iconTint
    } else if (checked == true) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.onSurface
    }
    // The mark never grows past its cell. A 32 dp glyph in a cell narrower than that overhangs its
    // neighbour instead of shrinking, which is how a row that merely overflowed became a row of
    // overlapping marks in the frames Google returned.
    val glyphCeiling = if (isBackIcon) BACK_GLYPH_DP.dp else COMMAND_GLYPH_DP.dp
    val glyphSize = minOf(glyphCeiling, size)
    // S2803: under ORIGINAL the box is square again - the pre-S2766 geometry the owner asked back.
    // STORE keeps the height floor, which the column's SpaceEvenly slack pays for.
    val boxHeight = if (LocalWearGeometryMode.current == WearGeometryMode.ORIGINAL) {
        size
    } else {
        maxOf(size, COMMAND_TOUCH_TARGET_DP.dp)
    }

    Box(
        contentAlignment = if (isBackIcon) Alignment.CenterStart else Alignment.Center,
        modifier = Modifier
            .width(size)
            .height(boxHeight)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(glyphSize)
        )
    }
}
