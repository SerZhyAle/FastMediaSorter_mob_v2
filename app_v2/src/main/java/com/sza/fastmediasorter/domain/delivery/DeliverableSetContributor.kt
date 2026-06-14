package com.sza.fastmediasorter.domain.delivery

/**
 * Flavor extension point declaring which [DeliverableSet]s a build ships and where their payloads
 * come from (S0386 Phase 05). Each flavor binds its own contributor into the `@Multibinds` set so a
 * flavor declares only the sets it carries; the manifest data source merges all contributed
 * descriptors. Mirrors the established `OcrEngineContributor` multibinding pattern.
 */
interface DeliverableSetContributor {
    fun descriptors(): Map<DeliverableSet, DeliverableSourceDescriptor>
}
