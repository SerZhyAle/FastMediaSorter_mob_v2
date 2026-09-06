package com.sza.fastmediasorter.wear.tile

import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget

/**
 * S2511: the one rule turning a drawable into the id a tile addresses it by.
 *
 * A ProtoLayout image is named by a string in the layout and published under the same string in the
 * resources response, and those are two different calls in two different files. Deriving both from this
 * function is what keeps them from drifting: a mismatch draws nothing at all and reports no error.
 */
fun tileImageResourceId(drawableResId: Int): String = "drawable_$drawableResId"

/**
 * A stable id for the tapped element, distinct per shortcut.
 *
 * The renderer reports which element was clicked by this id, so two buttons sharing one would be
 * indistinguishable in any later diagnosis of a mis-routed tap.
 */
fun WearLaunchTarget.clickId(): String = when (this) {
    is WearLaunchTarget.Destination -> "open_destination_${id.name}"
    is WearLaunchTarget.Open -> "open_target"
    is WearLaunchTarget.Pick -> "pick_target_${kind.name}"
    is WearLaunchTarget.File -> "open_file"
}
