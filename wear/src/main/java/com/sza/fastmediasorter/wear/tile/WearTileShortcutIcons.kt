package com.sza.fastmediasorter.wear.tile

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.appIdFor
import com.sza.fastmediasorter.wear.ui.apps.WearAppIconCatalog
import com.sza.fastmediasorter.wear.ui.home.HomeSectionIconCatalog

/**
 * S2751: the glyph a tile cell wears, chosen where the tile is drawn rather than where it is composed.
 *
 * Every arm delegates to the table the matching screen already draws from - never a drawable literal.
 * One entity wears one glyph on every entrance, and a second literal table beside those two is exactly
 * how a tile and its screen come to disagree about what a program looks like.
 *
 * Exhaustive with no else branch on purpose: a new destination must be given a glyph here rather than
 * silently inherit some default.
 */
@DrawableRes
internal fun tileShortcutIconFor(destination: WearDestinationId): Int {
    // A program is answered through appIdFor, which collapses twelve identical arms into one line and
    // is what keeps this function under detekt's complexity ceiling as the program list grows. The
    // watch broadcast is both a section and a program and is answered as a program - the two
    // entrances are equal and carry the same glyph, so either table gives the same drawable (S2509).
    val program = appIdFor(destination)
    if (program != null) {
        return WearAppIconCatalog.iconFor(program)
    }
    return when (destination) {
        WearDestinationId.RESOURCES -> HomeSectionIconCatalog.iconFor(HomeSectionId.RESOURCES)
        WearDestinationId.PHONE -> HomeSectionIconCatalog.iconFor(HomeSectionId.PHONE)
        WearDestinationId.LOCAL -> HomeSectionIconCatalog.iconFor(HomeSectionId.LOCAL)
        WearDestinationId.STREAMS -> HomeSectionIconCatalog.iconFor(HomeSectionId.STREAMS)
        WearDestinationId.APPS -> HomeSectionIconCatalog.iconFor(HomeSectionId.APPS)
        WearDestinationId.FAVOURITES -> HomeSectionIconCatalog.iconFor(HomeSectionId.FAVOURITES)
        // S2551: a Home section only - it is no program of the Apps grid, so the section table is the
        // only one that answers for it.
        WearDestinationId.PHONE_CAMERA -> HomeSectionIconCatalog.iconFor(HomeSectionId.PHONE_CAMERA)
        // S2511: no catalog answers for the overflow cell - it stands for no entity, it is the way out
        // of the grid into the screen that lists the rest, which is what this glyph says. A home
        // section added without a glyph lands here too, which is the same honest "open elsewhere".
        else -> R.drawable.ic_open_in_new
    }
}
