package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationSlotResolverTest {

    private fun resource(id: Long) = MediaResource(id = id, name = "r$id", path = "/r$id", type = ResourceType.LOCAL)

    private val destinations = (1L..12L).map(::resource)

    // MediaResource carries a System.currentTimeMillis() default, so two separately built instances
    // are never equal - the assertions compare ids, not instances.
    private fun resourceId(slot: Long) = resource(slot).id

    @Test
    fun `slot 1 resolves to the first destination`() {
        assertEquals(resourceId(1), DestinationSlotResolver.destinationFor(1, destinations)?.id)
    }

    @Test
    fun `slot 10 resolves to the tenth destination`() {
        assertEquals(resourceId(10), DestinationSlotResolver.destinationFor(10, destinations)?.id)
    }

    @Test
    fun `slot past the list resolves to null`() {
        assertNull(DestinationSlotResolver.destinationFor(4, destinations.take(3)))
    }

    @Test
    fun `slot outside 1 to slot count resolves to null`() {
        assertNull(DestinationSlotResolver.destinationFor(0, destinations))
        assertNull(DestinationSlotResolver.destinationFor(DestinationSlotResolver.SLOT_COUNT + 1, destinations))
    }

    @Test
    fun `any slot on an empty list resolves to null`() {
        assertNull(DestinationSlotResolver.destinationFor(1, emptyList()))
        assertNull(DestinationSlotResolver.destinationFor(10, emptyList()))
    }

    @Test
    fun `slotOf round trips destinationFor within the slot range`() {
        (1..DestinationSlotResolver.SLOT_COUNT).forEach { slot ->
            val destination = DestinationSlotResolver.destinationFor(slot, destinations)
            assertEquals(slot, DestinationSlotResolver.slotOf(destination!!.id, destinations))
        }
    }

    @Test
    fun `slotOf returns null for an absent resource and past the slot range`() {
        assertNull(DestinationSlotResolver.slotOf(SlotResolverCases.ABSENT_ID, destinations))
        assertNull(DestinationSlotResolver.slotOf(11, destinations))
    }
}

/** Named literals keep bare numbers out of the assertions (detekt MagicNumber). */
private object SlotResolverCases {
    const val ABSENT_ID = 99L
}
