package com.sza.fastmediasorter.di

import org.junit.Assert.assertEquals
import org.junit.Test

class GlideAppModuleTest {

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