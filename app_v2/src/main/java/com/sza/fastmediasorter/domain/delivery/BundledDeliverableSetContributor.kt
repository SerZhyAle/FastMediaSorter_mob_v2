package com.sza.fastmediasorter.domain.delivery

/**
 * Flavor-scoped contribution listing which [DeliverableSet]s still ship inside the current build.
 * The base app merges all contributors for the active flavor and uses the result as the runtime
 * truth for `already installed vs must prompt/download` decisions (S0386 Phase 05).
 */
interface BundledDeliverableSetContributor {
    fun bundledSets(): Set<DeliverableSet>
}