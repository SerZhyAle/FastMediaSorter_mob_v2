package com.sza.fastmediasorter.wear.capability

import com.sza.fastmediasorter.wear.domain.capability.WearGeometryDefaults
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode
import javax.inject.Inject

/**
 * S2773: the geometry answers for the sideload flavor.
 *
 * This build is never submitted, so the concession the review bought purchases nothing here and the
 * original view is what a fresh install draws. The control is offered as well, because comparing the
 * two views on one watch is what opened this ticket - a default alone would let the owner see the
 * layout he asked for and nothing else.
 */
class NoLegalWearGeometryDefaults @Inject constructor() : WearGeometryDefaults {

    override val startingMode: WearGeometryMode = WearGeometryMode.ORIGINAL

    override val offersModeSwitch: Boolean = true
}
