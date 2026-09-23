package com.sza.fastmediasorter.wear.capability

import com.sza.fastmediasorter.wear.domain.capability.WearAppearanceDefaults
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import javax.inject.Inject

/**
 * S3362: the appearance answers for the sideload flavor.
 *
 * This build is never submitted, so the concession WO-V13 buys purchases nothing here and the branded
 * animation stays what a fresh install draws - the value S2000 shipped and the one the owner's own
 * watch has had since.
 */
class NoLegalWearAppearanceDefaults @Inject constructor() : WearAppearanceDefaults {

    override val startingBackgroundMode: WearBackgroundMode = WearBackgroundMode.BRANDED_ANIMATION
}
