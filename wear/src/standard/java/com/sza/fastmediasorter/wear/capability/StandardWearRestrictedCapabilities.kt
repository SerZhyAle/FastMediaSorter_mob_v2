package com.sza.fastmediasorter.wear.capability

import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import javax.inject.Inject

/**
 * S2486: the answers for the flavor Play distributes.
 *
 * `standard` is the build that goes through the store, so every capability the review refuses is withheld
 * here - in the release AND in the debug build, because the predicate is about the distribution channel and
 * a debug build of this flavor is the same product one step earlier. Keeping a debug carve-out would mean
 * the shipped answer is never the one exercised during development, which is exactly how the build-type
 * gate this replaced went unnoticed.
 *
 * First content of `wear/src/standard/`, which had no source set at all until this ticket. It is not the
 * placeholder `dev/FLAVOR_DEVELOPMENT_RULES.md` Rule 8 bans - a two-sided `@Binds` contract has no
 * implementation unless both sides declare one.
 */
class StandardWearRestrictedCapabilities @Inject constructor() : WearRestrictedCapabilities {

    override val offersCredentialEntry: Boolean = false
}
