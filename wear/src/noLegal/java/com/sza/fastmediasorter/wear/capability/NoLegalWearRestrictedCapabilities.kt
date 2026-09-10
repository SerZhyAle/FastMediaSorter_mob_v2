package com.sza.fastmediasorter.wear.capability

import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import javax.inject.Inject

/**
 * S2486: the answers for the flavor that is not distributed through the store.
 *
 * `noLegal` is sideloaded, so the Play review never sees it and the capabilities WO-P6 refuses are offered
 * here - in the release as much as in the debug build. The owner's dictation of 2026-09-03 states the reason
 * in one line: these resources can only be entered in the version that does not go through the store.
 *
 * Second capability in `wear/src/noLegal/` after S2165's system-information contributor, and the first with
 * a counterpart in `wear/src/standard/`.
 */
class NoLegalWearRestrictedCapabilities @Inject constructor() : WearRestrictedCapabilities {

    override val offersCredentialEntry: Boolean = true

    /**
     * S2457: the heart-rate permissions are declared in this flavor's own manifest, so the diagnostic has
     * a path here and the Apps catalog offers its row.
     */
    override val offersBodySensorDiagnostics: Boolean = true

    /**
     * S2812: the water flashlight exists to survive a wet screen, and a shade that still pulls down over it
     * hands that wet screen the airplane-mode toggle. The sideload build takes the lock task mode the store
     * review would refuse.
     */
    override val locksSystemShade: Boolean = true
}
