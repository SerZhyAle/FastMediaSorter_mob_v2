package com.sza.fastmediasorter.data.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfNonDecodableCacheTest {

    private val cache = PdfNonDecodableCache()

    @Test
    fun `unmarked key is not known`() {
        assertFalse(cache.isKnownNonDecodable(PdfNonDecodableCache.keyFor("/a.pdf", 10L, 20L)))
    }

    @Test
    fun `marked key is known`() {
        val key = PdfNonDecodableCache.keyFor("/a.pdf", 10L, 20L)
        cache.markNonDecodable(key)
        assertTrue(cache.isKnownNonDecodable(key))
    }

    @Test
    fun `keys differing only in length are different`() {
        val first = PdfNonDecodableCache.keyFor("/a.pdf", 10L, 20L)
        val second = PdfNonDecodableCache.keyFor("/a.pdf", 11L, 20L)
        cache.markNonDecodable(first)
        assertFalse(cache.isKnownNonDecodable(second))
    }

    @Test
    fun `keys differing only in last modified time are different`() {
        val first = PdfNonDecodableCache.keyFor("/a.pdf", 10L, 20L)
        val second = PdfNonDecodableCache.keyFor("/a.pdf", 10L, 21L)
        cache.markNonDecodable(first)
        assertFalse(cache.isKnownNonDecodable(second))
    }

    @Test
    fun `same inputs produce the same key`() {
        assertEquals(
            PdfNonDecodableCache.keyFor("/a.pdf", 10L, 20L),
            PdfNonDecodableCache.keyFor("/a.pdf", 10L, 20L)
        )
    }

    @Test
    fun `eldest key is evicted after capacity is exceeded`() {
        val small = PdfNonDecodableCache(maxEntries = 3)
        val keys = (0..3).map { PdfNonDecodableCache.keyFor("/f$it.pdf", it.toLong(), 0L) }
        keys.forEach { small.markNonDecodable(it) }

        assertFalse(small.isKnownNonDecodable(keys.first()))
        assertTrue(small.isKnownNonDecodable(keys.last()))
    }
}
