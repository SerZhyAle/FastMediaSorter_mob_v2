package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default: every set is still bundled in the build. Correct until Phase 05 strips the artifacts
 * out of the base, at which point flavor-specific implementations replace this binding (store:
 * none bundled; sideload/VR: TRANSLATION bundled).
 */
@Singleton
class DefaultBundledDeliverableSets @Inject constructor() : BundledDeliverableSets {
    override fun contains(set: DeliverableSet): Boolean = true
}
