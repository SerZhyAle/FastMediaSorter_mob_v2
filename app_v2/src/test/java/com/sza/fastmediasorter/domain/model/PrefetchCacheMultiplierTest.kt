package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [PrefetchCacheMultiplier.fromName] and its default.
 */
class PrefetchCacheMultiplierTest {

    @Test
    fun `default is AUTO`() {
        assertEquals(PrefetchCacheMultiplier.AUTO, PrefetchCacheMultiplier.DEFAULT)
    }

    @Test
    fun `fromName resolves every constant`() {
        assertEquals(PrefetchCacheMultiplier.LESS, PrefetchCacheMultiplier.fromName("LESS"))
        assertEquals(PrefetchCacheMultiplier.AUTO, PrefetchCacheMultiplier.fromName("AUTO"))
        assertEquals(PrefetchCacheMultiplier.MORE, PrefetchCacheMultiplier.fromName("MORE"))
    }

    @Test
    fun `fromName falls back to the default for null or unknown`() {
        assertEquals(PrefetchCacheMultiplier.AUTO, PrefetchCacheMultiplier.fromName(null))
        assertEquals(PrefetchCacheMultiplier.AUTO, PrefetchCacheMultiplier.fromName("less"))
        assertEquals(PrefetchCacheMultiplier.AUTO, PrefetchCacheMultiplier.fromName("unknown"))
    }
}
