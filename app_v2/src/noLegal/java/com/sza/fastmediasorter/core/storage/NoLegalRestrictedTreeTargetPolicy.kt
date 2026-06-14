package com.sza.fastmediasorter.core.storage

import javax.inject.Inject
import javax.inject.Singleton

/**
 * noLegal flavor allows restricted SAF tree grants when the platform/user can provide them.
 */
@Singleton
class NoLegalRestrictedTreeTargetPolicy @Inject constructor() : VariantRestrictedTreeTargetPolicy {
    override fun allowsRestrictedTreeTargets(): Boolean = true
}
