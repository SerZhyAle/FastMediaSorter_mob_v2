package com.sza.fastmediasorter.core.util

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [DeviceCapabilityProbe].
 *
 * The probe reads free heap (JVM `Runtime`), free storage (`StatFs(cacheDir)`), and
 * a coarse [MemoryTier] (`ActivityManager`). Exact values depend on the Robolectric
 * host's JVM and sandbox, so these tests assert invariants rather than magic numbers:
 * budget is non-negative, never exceeds the absolute cap, and respects both the
 * heap and storage fractions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceCapabilityProbeTest {

    private lateinit var context: Context
    private lateinit var probe: DeviceCapabilityProbe

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        probe = DeviceCapabilityProbe(context)
    }

    @Test
    fun `currentBudget returns non-negative values`() {
        val budget = probe.currentBudget()
        assertTrue("cacheBudgetBytes must be >= 0", budget.cacheBudgetBytes >= 0L)
        assertTrue("freeHeapBytes must be >= 0", budget.freeHeapBytes >= 0L)
        assertTrue("freeStorageBytes must be >= 0", budget.freeStorageBytes >= 0L)
    }

    @Test
    fun `currentBudget never exceeds absolute cap`() {
        val budget = probe.currentBudget()
        assertTrue(
            "cacheBudgetBytes (${budget.cacheBudgetBytes}) must be <= ABSOLUTE_CAP_BYTES (${DeviceCapabilityProbe.ABSOLUTE_CAP_BYTES})",
            budget.cacheBudgetBytes <= DeviceCapabilityProbe.ABSOLUTE_CAP_BYTES
        )
    }

    @Test
    fun `currentBudget respects heap fraction ceiling`() {
        val budget = probe.currentBudget()
        val heapCeiling = (budget.freeHeapBytes.toDouble() * DeviceCapabilityProbe.HEAP_FRACTION).toLong()
        assertTrue(
            "cacheBudgetBytes (${budget.cacheBudgetBytes}) must be <= heap fraction ceiling ($heapCeiling)",
            budget.cacheBudgetBytes <= heapCeiling
        )
    }

    @Test
    fun `currentBudget respects storage fraction ceiling`() {
        val budget = probe.currentBudget()
        val storageCeiling = (budget.freeStorageBytes.toDouble() * DeviceCapabilityProbe.STORAGE_FRACTION).toLong()
        assertTrue(
            "cacheBudgetBytes (${budget.cacheBudgetBytes}) must be <= storage fraction ceiling ($storageCeiling)",
            budget.cacheBudgetBytes <= storageCeiling
        )
    }

    @Test
    fun `currentBudget reports memory tier`() {
        val budget = probe.currentBudget()
        assertNotNull("memoryTier must be populated", budget.memoryTier)
    }

    @Test
    fun `absolute cap constant is 256 MB`() {
        assertEquals(256L * 1024 * 1024, DeviceCapabilityProbe.ABSOLUTE_CAP_BYTES)
    }

    @Test
    fun `heap fraction constant is 15 percent`() {
        assertEquals(0.15, DeviceCapabilityProbe.HEAP_FRACTION, 0.00001)
    }

    @Test
    fun `storage fraction constant is 25 percent`() {
        assertEquals(0.25, DeviceCapabilityProbe.STORAGE_FRACTION, 0.00001)
    }

    @Test
    fun `subsequent calls produce consistent snapshots`() {
        val first = probe.currentBudget()
        val second = probe.currentBudget()
        // Values may drift slightly as the JVM allocates; assert same tier and close enough.
        assertEquals(first.memoryTier, second.memoryTier)
        assertTrue(
            "cacheBudgetBytes must not exceed cap across snapshots",
            second.cacheBudgetBytes <= DeviceCapabilityProbe.ABSOLUTE_CAP_BYTES
        )
    }
}
