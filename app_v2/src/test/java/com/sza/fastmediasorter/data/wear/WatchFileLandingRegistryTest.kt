package com.sza.fastmediasorter.data.wear

import com.sza.fastmediasorter.domain.model.WatchFileLanding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S4282: the registry answers once per landing and never grows past its bound.
 */
class WatchFileLandingRegistryTest {

    private val registry = WatchFileLandingRegistry()

    @Test
    fun `take returns the recorded landing once and then nothing`() {
        val landing = WatchFileLanding(resourceId = 7L, landedAtEpochMillis = 1_000L)
        registry.record("watch-screen-1.png", landing)

        assertEquals(landing, registry.take("watch-screen-1.png"))
        assertNull(registry.take("watch-screen-1.png"))
    }

    @Test
    fun `an unrecorded name answers null`() {
        assertNull(registry.take("never-seen.png"))
    }

    @Test
    fun `a landing without a resource is kept as such`() {
        val landing = WatchFileLanding(resourceId = null, landedAtEpochMillis = 2_000L)
        registry.record("fallback.png", landing)

        assertEquals(landing, registry.take("fallback.png"))
    }

    @Test
    fun `the ninth record evicts the oldest`() {
        repeat(9) { index ->
            registry.record("file-$index.png", WatchFileLanding(index.toLong(), index.toLong()))
        }

        assertNull(registry.take("file-0.png"))
        assertEquals(1L, registry.take("file-1.png")?.resourceId)
        assertEquals(8L, registry.take("file-8.png")?.resourceId)
    }
}
