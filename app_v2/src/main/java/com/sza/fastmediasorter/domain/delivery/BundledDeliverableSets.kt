package com.sza.fastmediasorter.domain.delivery

/**
 * Which [DeliverableSet]s ship inside the current build (vs delivered on demand). Flavor-specific:
 * Phase 02 ships a default that reports every set as bundled (nothing stripped yet); Phase 05
 * replaces it per flavor when the artifacts are removed from the base build.
 */
interface BundledDeliverableSets {
    fun contains(set: DeliverableSet): Boolean
}
