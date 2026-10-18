package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaResource

/**
 * MEDIA-VIEWER rule 4.1: slot N is the Nth entry of the full ordered destination list the current
 * resource is part of, so a slot names the same folder whichever resource is open. The visible
 * buttons exclude the current resource and must not renumber the slots.
 */
object DestinationSlotResolver {

    const val SLOT_COUNT = 10

    /** 1-based; slot 10 is the digit 0 key. Null outside 1..[SLOT_COUNT] or past the list. */
    fun destinationFor(slot: Int, orderedDestinations: List<MediaResource>): MediaResource? {
        if (slot < 1 || slot > SLOT_COUNT) return null
        return orderedDestinations.getOrNull(slot - 1)
    }

    /** Inverse of [destinationFor]; null when the resource is absent or past [SLOT_COUNT]. */
    fun slotOf(resourceId: Long, orderedDestinations: List<MediaResource>): Int? {
        val index = orderedDestinations.indexOfFirst { it.id == resourceId }
        if (index < 0 || index >= SLOT_COUNT) return null
        return index + 1
    }
}
