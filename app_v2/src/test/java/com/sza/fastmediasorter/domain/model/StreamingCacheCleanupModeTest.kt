package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [StreamingCacheCleanupMode.fromName] and its default.
 */
class StreamingCacheCleanupModeTest {

    @Test
    fun `default is ASK`() {
        assertEquals(StreamingCacheCleanupMode.ASK, StreamingCacheCleanupMode.DEFAULT)
    }

    @Test
    fun `fromName resolves every constant`() {
        assertEquals(StreamingCacheCleanupMode.ASK, StreamingCacheCleanupMode.fromName("ASK"))
        assertEquals(StreamingCacheCleanupMode.AUTO_DELETE, StreamingCacheCleanupMode.fromName("AUTO_DELETE"))
        assertEquals(StreamingCacheCleanupMode.AUTO_KEEP, StreamingCacheCleanupMode.fromName("AUTO_KEEP"))
    }

    @Test
    fun `fromName falls back to the default for null or unknown`() {
        assertEquals(StreamingCacheCleanupMode.ASK, StreamingCacheCleanupMode.fromName(null))
        assertEquals(StreamingCacheCleanupMode.ASK, StreamingCacheCleanupMode.fromName("auto_keep"))
        assertEquals(StreamingCacheCleanupMode.ASK, StreamingCacheCleanupMode.fromName("nope"))
    }
}
