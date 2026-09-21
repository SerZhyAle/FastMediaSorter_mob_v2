package com.sza.fastmediasorter.wear.capability

import com.sza.fastmediasorter.wear.domain.capability.WearAppearanceDefaults
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import javax.inject.Inject

/**
 * S3362: the appearance answers for the flavor Play distributes.
 *
 * Black, because Wear OS review item WO-V13 asks for a black background for all apps and the watch a
 * reviewer opens has chosen nothing yet. The animated wallpaper it replaces is also the module's one
 * continuously running animation, so the artifact under review starts without it.
 *
 * Nothing is removed: the branded animation, the still and the rest of the list stay in the screen
 * settings, and a watch whose owner picks one keeps it.
 */
class StandardWearAppearanceDefaults @Inject constructor() : WearAppearanceDefaults {

    override val startingBackgroundMode: WearBackgroundMode = WearBackgroundMode.NONE
}
