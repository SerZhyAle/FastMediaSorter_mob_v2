package com.sza.fastmediasorter.di

import com.bumptech.glide.load.EncodeStrategy
import com.bumptech.glide.load.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GlideAppModuleTest {

    /**
     * S1026 regression lock. Glide reserves [EncodeStrategy.NONE] for "no encoder registered"
     * (DecodeJob:592) and its encode switch handles only SOURCE/TRANSFORMED, so returning NONE from a
     * registered encoder throws `IllegalArgumentException: Unknown strategy: NONE` on every fresh
     * animated decode under a RESOURCE/ALL disk-cache strategy. That shipped once already; the failure
     * is invisible to unit tests unless the strategy itself is asserted here.
     */
    @Test
    fun `animated drawable encoder never reports the NONE strategy`() {
        val strategy = AnimatedImageDrawableNoOpEncoder().getEncodeStrategy(Options())

        assertNotEquals(EncodeStrategy.NONE, strategy)
        assertEquals(EncodeStrategy.SOURCE, strategy)
    }

    @Test
    fun `startup cache is clamped to minimum 4 MB`() {
        val clamped = GlideAppModule.clampStartupMemoryCacheBytes(2L * 1024L * 1024L)

        assertEquals(4L * 1024L * 1024L, clamped)
    }

    @Test
    fun `startup cache keeps coordinator budget when above minimum`() {
        val clamped = GlideAppModule.clampStartupMemoryCacheBytes(12L * 1024L * 1024L)

        assertEquals(12L * 1024L * 1024L, clamped)
    }
}