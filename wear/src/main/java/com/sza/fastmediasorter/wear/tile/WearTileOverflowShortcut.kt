package com.sza.fastmediasorter.wear.tile

import android.content.Context
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileShortcut

/**
 * S2511: the cell a shortcut grid spends on "the rest", built in one place for both readers of the grid.
 *
 * The layout draws the grid and the service publishes the glyphs the grid needs, and those are two separate
 * requests from the system. Each has to arrive at the same cell list or the overflow button draws as an
 * empty square: an image is addressed by an id the resources response must already carry. One definition
 * here is what keeps the two from disagreeing about whether the grid overflowed at all.
 */
internal fun overflowShortcut(context: Context): WearTileShortcut = WearTileShortcut(
    destinationId = WearDestinationId.HOME,
    contentDescription = context.getString(R.string.wear_tile_shortcut_more),
    launchTarget = WearLaunchTarget.Destination(WearDestinationId.HOME)
)
