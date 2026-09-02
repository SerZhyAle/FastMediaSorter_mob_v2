package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.ui.unit.Dp
import com.sza.fastmediasorter.wear.domain.model.WearContentType

/**
 * How much width a list row spends on its leading icon.
 *
 * S2129: on a watch the name is the only thing that separates two entries, and an icon repeated
 * down the whole column separates nothing while still taking the width the name needs.
 */
enum class WearRowDensity {

    /** The icon carries information - it keeps its full size. */
    NORMAL,

    /** Every entry wears the same glyph - the icon shrinks and the name takes the width. */
    COMPACT;

    /** The leading-icon size this density draws at. */
    val leadingIconSize: Dp
        get() = when (this) {
            NORMAL -> WearListMetrics.LeadingIconNormal
            COMPACT -> WearListMetrics.LeadingIconCompact
        }
}

/**
 * The density a whole list is drawn at, decided once from the types it resolved.
 *
 * Strategic ADR-3: the verdict is taken for the list rather than per frame. Thumbnails arrive
 * asynchronously, so a per-frame rule would re-lay-out a row under the reading finger - which is
 * also why [canProduceThumbnails] describes what the list *may* yet load, not what it holds now.
 *
 * Strategic §6.2: the boundary is this predicate alone. A resource carrying its own icon stays in
 * [WearRowDensity.NORMAL] because its type sits beside others in [types], with no special case for
 * where the entry came from.
 *
 * An empty list answers [WearRowDensity.NORMAL]: nothing has been proven uniform yet, and the first
 * entry to arrive must not make the icons jump.
 */
fun rowDensityFor(
    types: Collection<WearContentType>,
    canProduceThumbnails: Boolean
): WearRowDensity = when {
    types.isEmpty() -> WearRowDensity.NORMAL
    canProduceThumbnails -> WearRowDensity.NORMAL
    types.distinct().size > 1 -> WearRowDensity.NORMAL
    else -> WearRowDensity.COMPACT
}
