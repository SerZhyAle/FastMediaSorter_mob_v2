package com.sza.fastmediasorter.wear.tile

import androidx.annotation.StringRes
import androidx.wear.protolayout.material.layouts.LayoutDefaults.MultiButtonLayoutDefaults.MAX_BUTTONS
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearTileContent
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileShortcut

/**
 * S2589: the decisions of the tile layout layer, with no [android.content.Context] anywhere near them.
 *
 * Every rule here used to live inside a private method of [WearTileLayoutBuilder] that built ProtoLayout
 * elements in the same breath, and a ProtoLayout element needs a `Context` this module cannot produce - it
 * carries no Robolectric on purpose (S2437). So the grid clamp S2511 left as an open risk, and everything
 * beside it, was unreachable from the JVM suite and could only be checked by hand on a watch. Deciding here
 * and drawing there is what makes the fast loop able to catch a tile defect at all.
 */

/** How many entries of an assigned tile are previewed under its title. */
const val MAX_FAVOURITES_PREVIEW_ENTRIES = 3

/**
 * S3555: the cells a shortcut grid keeps while a running program shares the tile with it.
 *
 * The grid then sits in a `PrimaryLayout` between the label and the chip, where one row of buttons fits;
 * `MultiButtonLayout` itself asks to stand alone once it holds more. Three cells is that one row.
 */
const val RUNNING_GRID_CAPACITY = 3

/**
 * What the shortcut grid will actually draw, and what it had to leave out.
 *
 * [dropped] counts the entries no cell of [shown] carries. It is carried out rather than logged here so the
 * count stays a fact about the data and the warning stays where the rest of the tile's logging is.
 */
data class WearShortcutGridPlan(
    val shown: List<WearTileShortcut>,
    val dropped: Int
)

/**
 * S2511: cuts the shortcut list to what the grid holds, spending the last cell on [overflow] when it cuts.
 *
 * `MultiButtonLayout` throws above its capacity instead of truncating, and an exception inside a tile
 * request hands the system an error tile in place of content - while the two catalogs feeding this grid are
 * documented as growing by a single line, by authors who have no reason to know a tile reads them.
 *
 * Truncating alone was not enough: on the tree that added an eighth section the clamp silently swallowed
 * Favourites, so the tile answered a question the owner never asked it. [overflow] is the cell that leads to
 * the screen listing everything, which turns "one entry disappeared" into "the rest are one tap further".
 * It is passed in rather than built here because it carries a translated label, and a label needs a
 * `Context` this file exists to stay clear of. A null [overflow] keeps the bare clamp, which is what the
 * caller with nowhere to send the owner wants.
 *
 * [capacity] is a parameter so a test can state its own bound instead of depending on the library's value,
 * which is what makes the clamp checkable at all; the default is that value, and a separate test pins the
 * two together.
 */
fun planShortcutGrid(
    entries: List<WearTileShortcut>,
    overflow: WearTileShortcut? = null,
    capacity: Int = MAX_BUTTONS
): WearShortcutGridPlan {
    val keptCount = when {
        entries.size <= capacity -> entries.size
        overflow == null -> capacity
        else -> capacity - 1
    }
    val kept = entries.take(keptCount)
    val overflows = kept.size < entries.size
    return WearShortcutGridPlan(
        shown = if (overflows && overflow != null) kept + overflow else kept,
        dropped = entries.size - kept.size
    )
}

/**
 * S3555: the capacity [planShortcutGrid] is given for [content].
 *
 * The layout draws the grid and the service publishes its glyphs in two separate requests, so both take
 * the capacity from here; two copies of the rule could disagree about which cells exist and leave one
 * drawn without its image.
 */
fun shortcutGridCapacity(content: WearTileContent.Shortcuts): Int =
    if (content.running == null) MAX_BUTTONS else RUNNING_GRID_CAPACITY

/** The entries an assigned tile previews under its title. */
fun planAssignedPreview(entries: List<String>): List<String> = entries.take(MAX_FAVOURITES_PREVIEW_ENTRIES)

/**
 * The label an unassigned tile of [kind] carries.
 *
 * The three grid kinds are unreachable here - they have no assignment, so they never enter that state
 * (S2511) - and are named rather than sent to an `else` so a future kind must still be classified.
 */
@StringRes
fun unassignedLabelRes(kind: WearTileKind): Int = when (kind) {
    WearTileKind.RESOURCE -> R.string.wear_tile_unassigned_resource
    WearTileKind.STREAM -> R.string.wear_tile_unassigned_stream
    WearTileKind.FAVOURITES,
    WearTileKind.PROGRAMS,
    WearTileKind.SECTIONS -> R.string.wear_tile_favourites_empty
}
