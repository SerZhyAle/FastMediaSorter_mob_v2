package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The leading-icon sizes every watch list row and cell draws from.
 *
 * S2129: five screens each declared their own copy of the same 24 dp value under three different
 * names, so the compact size introduced beside it would have disagreed with whichever copy was
 * missed. One home for both sizes is what keeps the two densities in step.
 */
object WearListMetrics {

    /** The size a leading icon keeps whenever it still tells one entry from another. */
    val LeadingIconNormal: Dp = 24.dp

    /**
     * The size a leading icon shrinks to once every entry in the list wears the same glyph.
     *
     * Two thirds of the normal size: still a readable silhouette telling the reader what kind of
     * list this is, while handing the freed width to the name, which is the only thing left that
     * distinguishes the entries.
     */
    val LeadingIconCompact: Dp = 16.dp
}
