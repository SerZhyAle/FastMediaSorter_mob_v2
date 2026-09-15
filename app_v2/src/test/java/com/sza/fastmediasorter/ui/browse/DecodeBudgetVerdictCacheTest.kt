package com.sza.fastmediasorter.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeBudgetVerdictCacheTest {

    @Test
    fun `an unmeasured path has no cached verdict`() {
        assertNull(DecodeBudgetVerdictCache().cached("/sdcard/a.jpg"))
    }

    @Test
    fun `both outcomes are measured once and then served from the cache`() {
        val cache = DecodeBudgetVerdictCache()
        var reads = 0
        val countedOver: (String) -> Boolean? = {
            reads++
            true
        }
        val countedWithin: (String) -> Boolean? = {
            reads++
            false
        }
        repeat(3) { assertTrue(cache.resolve("/big.png", countedOver)) }
        repeat(3) { assertFalse(cache.resolve("/small.png", countedWithin)) }
        assertEquals(2, reads)
        assertEquals(true, cache.cached("/big.png"))
        assertEquals(false, cache.cached("/small.png"))
    }

    @Test
    fun `an unmeasurable file answers false and is not remembered`() {
        val cache = DecodeBudgetVerdictCache()
        var reads = 0
        val unmeasurable: (String) -> Boolean? = {
            reads++
            null
        }
        repeat(2) { assertFalse(cache.resolve("/broken.jpg", unmeasurable)) }
        assertEquals(2, reads)
        assertNull(cache.cached("/broken.jpg"))
    }

    @Test
    fun `remote and blank paths are not measurable`() {
        assertFalse(DecodeBudgetVerdictCache.isMeasurable(""))
        assertFalse(DecodeBudgetVerdictCache.isMeasurable("smb://host/share/a.jpg"))
        assertFalse(DecodeBudgetVerdictCache.isMeasurable("content://media/external/images/1"))
        assertTrue(DecodeBudgetVerdictCache.isMeasurable("/storage/emulated/0/DCIM/a.jpg"))
    }
}
