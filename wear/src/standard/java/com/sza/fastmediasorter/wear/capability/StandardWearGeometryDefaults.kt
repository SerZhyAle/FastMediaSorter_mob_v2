package com.sza.fastmediasorter.wear.capability

import com.sza.fastmediasorter.wear.domain.capability.WearGeometryDefaults
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode
import javax.inject.Inject

/**
 * S2773: the geometry answers for the flavor Play distributes.
 *
 * The store view is the starting one and the only one this build ever draws, because the control that
 * would change it is withheld here. That is the whole of ADR-3: the published build passed a review on
 * screen shape, and a settings row able to undo that hands the rejected shape back to any user who
 * finds it. The owner wears the `noLegal` build, so withholding the control costs him nothing.
 */
class StandardWearGeometryDefaults @Inject constructor() : WearGeometryDefaults {

    override val startingMode: WearGeometryMode = WearGeometryMode.STORE

    override val offersModeSwitch: Boolean = false
}
