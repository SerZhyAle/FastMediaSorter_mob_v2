package com.sza.fastmediasorter.domain.playback

import com.sza.fastmediasorter.domain.model.PrefetchCacheMultiplier
import com.sza.fastmediasorter.domain.model.Protocol
import com.sza.fastmediasorter.domain.model.StreamViabilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PrefetchFormula].
 *
 * The seven worked examples mirror PLAN/spec_adaptive-playback-strategy.md section 5.4
 * and serve as the contract between spec and implementation: if any of these numbers
 * change, the spec has to move in lockstep.
 */
class PrefetchFormulaTest {

    // Large enough not to be the limiting factor in any test that is not specifically
    // about device-budget clamping.
    private val LARGE_BUDGET = 256L * 1024 * 1024

    // ── Seven worked examples from spec §5.4 ────────────────────────────────────

    @Test
    fun `example 1 - fast local 4K uses minimum target`() {
        val plan = PrefetchFormula.compute(
            speedMbps = null,
            bitrateKbps = 18_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.LOCAL,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        assertEquals(StreamViabilityState.VIABLE, plan.viability)
        assertEquals(2, plan.targetPrefetchSec)
    }

    @Test
    fun `example 2 - fast SMB HD produces 4s target`() {
        val plan = PrefetchFormula.compute(
            speedMbps = 120.0,
            bitrateKbps = 5_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.SMB,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        assertEquals(StreamViabilityState.VIABLE, plan.viability)
        assertEquals(4, plan.targetPrefetchSec)
    }

    @Test
    fun `example 3 - slow SMB HD produces 11s target`() {
        val plan = PrefetchFormula.compute(
            speedMbps = 8.0,
            bitrateKbps = 5_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.SMB,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        assertEquals(StreamViabilityState.VIABLE, plan.viability)
        assertEquals(11, plan.targetPrefetchSec)
    }

    @Test
    fun `example 4 - SFTP near-marginal produces 15s target`() {
        val plan = PrefetchFormula.compute(
            speedMbps = 12.0,
            bitrateKbps = 8_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.SFTP,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        // ratio = 8000 / 12000 = 0.667 < 0.7 → still VIABLE
        assertEquals(StreamViabilityState.VIABLE, plan.viability)
        assertEquals(15, plan.targetPrefetchSec)
    }

    @Test
    fun `example 5 - tight Cloud HD is MARGINAL with 28s target`() {
        val plan = PrefetchFormula.compute(
            speedMbps = 8.0,
            bitrateKbps = 6_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.CLOUD,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        // ratio = 6000 / 8000 = 0.75 → MARGINAL
        assertEquals(StreamViabilityState.MARGINAL, plan.viability)
        assertEquals(28, plan.targetPrefetchSec)
    }

    @Test
    fun `example 6 - very slow Cloud 4K is NOT_VIABLE`() {
        val plan = PrefetchFormula.compute(
            speedMbps = 3.0,
            bitrateKbps = 12_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.CLOUD,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        assertEquals(StreamViabilityState.NOT_VIABLE, plan.viability)
        // Still emits a safe floor target so callers that ignore viability don't crash.
        assertTrue("target should be a positive number", plan.targetPrefetchSec >= 2)
    }

    @Test
    fun `example 7 - MORE on slow SMB adds 10s headroom`() {
        val plan = PrefetchFormula.compute(
            speedMbps = 8.0,
            bitrateKbps = 5_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.SMB,
            userMultiplier = PrefetchCacheMultiplier.MORE
        )
        assertEquals(StreamViabilityState.VIABLE, plan.viability)
        // Same inputs as example 3 (11s) + 10s MORE headroom = 21s.
        assertEquals(21, plan.targetPrefetchSec)
    }

    // ── Viability boundary tests ────────────────────────────────────────────────

    @Test
    fun `classifyViability returns MARGINAL at ratio 0_899`() {
        assertEquals(
            StreamViabilityState.MARGINAL,
            PrefetchFormula.classifyViability(0.899)
        )
    }

    @Test
    fun `classifyViability returns NOT_VIABLE at ratio 0_900`() {
        assertEquals(
            StreamViabilityState.NOT_VIABLE,
            PrefetchFormula.classifyViability(0.900)
        )
    }

    @Test
    fun `classifyViability returns VIABLE just below marginal boundary`() {
        assertEquals(
            StreamViabilityState.VIABLE,
            PrefetchFormula.classifyViability(0.699)
        )
    }

    @Test
    fun `classifyViability returns MARGINAL at exact marginal boundary`() {
        assertEquals(
            StreamViabilityState.MARGINAL,
            PrefetchFormula.classifyViability(0.700)
        )
    }

    // ── Input-edge tests ────────────────────────────────────────────────────────

    @Test
    fun `zero speed collapses to NOT_VIABLE regardless of bitrate`() {
        val plan = PrefetchFormula.compute(
            speedMbps = 0.0,
            bitrateKbps = 1,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.SMB,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        assertEquals(StreamViabilityState.NOT_VIABLE, plan.viability)
    }

    @Test
    fun `null bitrate uses protocol baseline ratio`() {
        val plan = PrefetchFormula.compute(
            speedMbps = 50.0,
            bitrateKbps = null,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.CLOUD,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        // CLOUD baseline ratio is 0.45 → VIABLE, and derivation.ratio is not from speed/bitrate.
        assertEquals(StreamViabilityState.VIABLE, plan.viability)
        assertEquals(0.45, plan.derivation.ratio, 0.0001)
    }

    @Test
    fun `null speed uses protocol baseline speed`() {
        val plan = PrefetchFormula.compute(
            speedMbps = null,
            bitrateKbps = 5_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.SMB,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        // SMB baseline speed is 50 Mbps → ratio = 5000 / 50000 = 0.1 → VIABLE.
        assertEquals(StreamViabilityState.VIABLE, plan.viability)
        assertEquals(0.1, plan.derivation.ratio, 0.0001)
    }

    // ── Device and file clamps ──────────────────────────────────────────────────

    @Test
    fun `small device budget caps target below physics result`() {
        // 1_875_000 bytes / (5000 * 125) = 3 seconds at 5 Mbps → upperBound = 3.
        val plan = PrefetchFormula.compute(
            speedMbps = 120.0,
            bitrateKbps = 5_000,
            cacheBudgetBytes = 1_875_000L,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.SMB,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        assertEquals(3, plan.derivation.maxBufferSecByDevice)
        assertTrue(
            "target must not exceed device cap",
            plan.targetPrefetchSec <= plan.derivation.maxBufferSecByDevice
        )
    }

    @Test
    fun `short file duration caps target at 80 percent of remaining`() {
        // 30s clip, position=0 → remaining=30, maxBufferSecByFile = 24.
        val plan = PrefetchFormula.compute(
            speedMbps = 8.0,
            bitrateKbps = 6_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = 30L,
            currentPositionSec = 0L,
            protocol = Protocol.CLOUD,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        assertEquals(24, plan.derivation.maxBufferSecByFile)
        // Physics target for this input is 28s - must be capped by file ceiling.
        assertEquals(24, plan.targetPrefetchSec)
    }

    @Test
    fun `MAX_TARGET_SEC upper clamp engages for unbounded physics`() {
        // ratio just under 0.9 → divisor floor (0.1) kicks in, physics = 70s for CLOUD.
        // Push into NOT_VIABLE territory to exercise divisor floor: ratio = 1.5.
        val plan = PrefetchFormula.compute(
            speedMbps = 5.0,
            bitrateKbps = 7_500,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.CLOUD,
            userMultiplier = PrefetchCacheMultiplier.MORE
        )
        assertEquals(StreamViabilityState.NOT_VIABLE, plan.viability)
        assertTrue("must not exceed hard MAX_TARGET_SEC", plan.targetPrefetchSec <= 90)
    }

    // ── Derivation fractions ────────────────────────────────────────────────────

    @Test
    fun `derivation fractions are consistent with target`() {
        val plan = PrefetchFormula.compute(
            speedMbps = 8.0,
            bitrateKbps = 5_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.SMB,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        // target = 11 → min ≈ 11*0.35 = 4, rebuffer ≈ 11*0.55 = 6, max ≈ 11*3 = 33.
        assertEquals(11, plan.targetPrefetchSec)
        assertEquals(4, plan.minPrefetchSec)
        assertEquals(6, plan.rebufferPrefetchSec)
        assertEquals(33, plan.maxBufferSec)
    }

    @Test
    fun `derivation carries raw inputs for audit`() {
        val plan = PrefetchFormula.compute(
            speedMbps = 8.0,
            bitrateKbps = 5_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = 600L,
            currentPositionSec = 60L,
            protocol = Protocol.SMB,
            userMultiplier = PrefetchCacheMultiplier.MORE
        )
        assertEquals(8.0, plan.derivation.speedMbps!!, 0.0001)
        assertEquals(5_000, plan.derivation.bitrateKbps)
        assertEquals(Protocol.SMB, plan.derivation.protocol)
        assertEquals(PrefetchCacheMultiplier.MORE, plan.derivation.userMultiplier)
        assertEquals(LARGE_BUDGET, plan.derivation.cacheBudgetBytes)
        assertEquals(PrefetchFormula.FORMULA_VERSION, plan.derivation.formulaVersion)
    }

    // ── ratioOf helper ──────────────────────────────────────────────────────────

    @Test
    fun `ratioOf matches compute derivation for same inputs`() {
        val computed = PrefetchFormula.compute(
            speedMbps = 8.0,
            bitrateKbps = 5_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.SMB,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        val helper = PrefetchFormula.ratioOf(8.0, 5_000, Protocol.SMB)
        assertEquals(computed.derivation.ratio, helper, 0.0001)
    }

    @Test
    fun `ratioOf with zero speed signals non-viability`() {
        val ratio = PrefetchFormula.ratioOf(0.0, 5_000, Protocol.SMB)
        // Exact sentinel is an implementation detail; the contract is "classifies NOT_VIABLE".
        assertEquals(
            StreamViabilityState.NOT_VIABLE,
            PrefetchFormula.classifyViability(ratio)
        )
    }

    // ── User-multiplier effect ──────────────────────────────────────────────────

    @Test
    fun `LESS multiplier shrinks target vs AUTO`() {
        val auto = PrefetchFormula.compute(
            speedMbps = 8.0,
            bitrateKbps = 5_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.SMB,
            userMultiplier = PrefetchCacheMultiplier.AUTO
        )
        val less = PrefetchFormula.compute(
            speedMbps = 8.0,
            bitrateKbps = 5_000,
            cacheBudgetBytes = LARGE_BUDGET,
            fileDurationSec = null,
            currentPositionSec = 0L,
            protocol = Protocol.SMB,
            userMultiplier = PrefetchCacheMultiplier.LESS
        )
        assertTrue(
            "LESS ($less) must not exceed AUTO ($auto)",
            less.targetPrefetchSec <= auto.targetPrefetchSec
        )
        assertNotEquals(auto.targetPrefetchSec, less.targetPrefetchSec)
    }
}
