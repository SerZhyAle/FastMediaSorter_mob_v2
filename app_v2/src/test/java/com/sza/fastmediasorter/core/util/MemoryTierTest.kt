package com.sza.fastmediasorter.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [MemoryTier.classify] - pure classification logic without [android.content.Context].
 *
 * Covers the S0207 Phase 02 contract:
 * - Java heap-limit dominates physical RAM,
 * - the 512 MB boundary is inclusive (`<= 512` -> LOW),
 * - HIGH requires both `totalRamGb >= 6.0` and `maxHeapMb > 512`.
 */
class MemoryTierTest {

    @Test
    fun `512 MB heap with 3 GB RAM classifies as LOW (canonical emulator)`() {
        // Canonical S0207 target: emulator with heapMax=512 MB and ~3.82 GB RAM.
        val tier = MemoryTier.classify(
            isLowRamDevice = false,
            totalRamGb = 3.82,
            maxHeapMb = 512L,
        )
        assertEquals(MemoryTier.LOW, tier)
    }

    @Test
    fun `512 MB heap with 6 GB RAM classifies as LOW (Quest 3)`() {
        // Quest 3 reports heapMax=512 MB exactly with >= 6 GB RAM -
        // S0207 explicitly relies on heap-limit dominating tier classification.
        val tier = MemoryTier.classify(
            isLowRamDevice = false,
            totalRamGb = 6.0,
            maxHeapMb = 512L,
        )
        assertEquals(MemoryTier.LOW, tier)
    }

    @Test
    fun `513 MB heap with 6 GB RAM classifies as HIGH (HIGH preservation path)`() {
        val tier = MemoryTier.classify(
            isLowRamDevice = false,
            totalRamGb = 6.0,
            maxHeapMb = 513L,
        )
        assertEquals(MemoryTier.HIGH, tier)
    }

    @Test
    fun `768 MB heap with 8 GB RAM classifies as HIGH`() {
        val tier = MemoryTier.classify(
            isLowRamDevice = false,
            totalRamGb = 8.0,
            maxHeapMb = 768L,
        )
        assertEquals(MemoryTier.HIGH, tier)
    }

    @Test
    fun `768 MB heap with 4 GB RAM classifies as STANDARD`() {
        // Heap > 512 MB and RAM in 3..6 GB band -> STANDARD.
        val tier = MemoryTier.classify(
            isLowRamDevice = false,
            totalRamGb = 4.0,
            maxHeapMb = 768L,
        )
        assertEquals(MemoryTier.STANDARD, tier)
    }

    @Test
    fun `low-RAM-device flag forces LOW regardless of other signals`() {
        val tier = MemoryTier.classify(
            isLowRamDevice = true,
            totalRamGb = 8.0,
            maxHeapMb = 1024L,
        )
        assertEquals(MemoryTier.LOW, tier)
    }

    @Test
    fun `under 3 GB RAM forces LOW even when heap is large`() {
        val tier = MemoryTier.classify(
            isLowRamDevice = false,
            totalRamGb = 2.5,
            maxHeapMb = 1024L,
        )
        assertEquals(MemoryTier.LOW, tier)
    }
}
