package com.sza.fastmediasorter.core.storage

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flavor-safe policy for restricted SAF tree targets such as Android/data and Android/obb.
 */
interface RestrictedTreeTargetPolicy {
    fun allowsRestrictedTreeTargets(): Boolean
}

/**
 * Marker interface for flavor source sets that want to widen restricted-tree behavior.
 */
interface VariantRestrictedTreeTargetPolicy : RestrictedTreeTargetPolicy

/**
 * Conservative default for market/public builds.
 */
@Singleton
class NoOpRestrictedTreeTargetPolicy @Inject constructor() : RestrictedTreeTargetPolicy {
    override fun allowsRestrictedTreeTargets(): Boolean = false
}
