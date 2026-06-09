package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSetContributor
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime view of which deliverable sets remain bundled in the active flavor. The active flavor's
 * Hilt modules contribute the bundled sets; an empty contributor set means nothing is bundled.
 */
@Singleton
class ContributedBundledDeliverableSets @Inject constructor(
    contributors: Set<@JvmSuppressWildcards BundledDeliverableSetContributor>
) : BundledDeliverableSets {

    private val bundledSets = buildSet {
        contributors.forEach { addAll(it.bundledSets()) }
    }

    override fun contains(set: DeliverableSet): Boolean = bundledSets.contains(set)
}