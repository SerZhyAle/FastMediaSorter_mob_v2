package com.sza.fastmediasorter.wear.tile

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.WearAppId
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
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
internal fun tileShortcutIconFor(destination: WearDestinationId): Int = when (destination) {
    WearDestinationId.RESOURCES -> HomeSectionIconCatalog.iconFor(HomeSectionId.RESOURCES)
    WearDestinationId.PHONE -> HomeSectionIconCatalog.iconFor(HomeSectionId.PHONE)
    WearDestinationId.LOCAL -> HomeSectionIconCatalog.iconFor(HomeSectionId.LOCAL)
    WearDestinationId.STREAMS -> HomeSectionIconCatalog.iconFor(HomeSectionId.STREAMS)
    WearDestinationId.APPS -> HomeSectionIconCatalog.iconFor(HomeSectionId.APPS)
    WearDestinationId.FAVOURITES -> HomeSectionIconCatalog.iconFor(HomeSectionId.FAVOURITES)
    // S2509: read from the Home section rather than the Programs row. The two entrances are equal and
    // carry the same glyph, so either would answer - the section is taken because this destination is
    // listed first among the sections in WearDestinationId.
    WearDestinationId.BROADCAST -> HomeSectionIconCatalog.iconFor(HomeSectionId.BROADCAST)
    // S2551: a Home section only - it is no program of the Apps grid, so the section table is the
    // only one that answers for it.
    WearDestinationId.PHONE_CAMERA -> HomeSectionIconCatalog.iconFor(HomeSectionId.PHONE_CAMERA)
    WearDestinationId.CALCULATOR -> WearAppIconCatalog.iconFor(WearAppId.CALCULATOR)
    WearDestinationId.NETWORK_MONITOR -> WearAppIconCatalog.iconFor(WearAppId.NETWORK_MONITOR)
    WearDestinationId.GAME -> WearAppIconCatalog.iconFor(WearAppId.GAME)
    WearDestinationId.VOICE_RECORDER -> WearAppIconCatalog.iconFor(WearAppId.VOICE_RECORDER)
    WearDestinationId.SYSTEM_INFO -> WearAppIconCatalog.iconFor(WearAppId.SYSTEM_INFO)
    WearDestinationId.WATER_FLASHLIGHT -> WearAppIconCatalog.iconFor(WearAppId.WATER_FLASHLIGHT)
    WearDestinationId.MOTION_MONITOR -> WearAppIconCatalog.iconFor(WearAppId.MOTION_MONITOR)
    WearDestinationId.BODY_SENSOR -> WearAppIconCatalog.iconFor(WearAppId.BODY_SENSOR)
    WearDestinationId.BLOOD_PRESSURE -> WearAppIconCatalog.iconFor(WearAppId.BLOOD_PRESSURE)
    WearDestinationId.STOPWATCH -> WearAppIconCatalog.iconFor(WearAppId.STOPWATCH)
    WearDestinationId.TOURIST -> WearAppIconCatalog.iconFor(WearAppId.TOURIST)
    // S2511: no catalog answers for the overflow cell - it stands for no entity, it is the way out of the
    // grid into the screen that lists the rest, which is what the "open elsewhere" glyph says.
    WearDestinationId.HOME -> R.drawable.ic_open_in_new
}
