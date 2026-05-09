package com.sza.fastmediasorter.ui.common.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class UiMessageSpecTest {

    // ── short message invariant ─────────────────────────────────────────────

    @Test
    fun `short message - non-blank value is accepted`() {
        val spec = UiMessageSpec.error("Something went wrong.")
        assertEquals("Something went wrong.", spec.shortMessage)
    }

    @Test
    fun `short message - blank value throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            UiMessageSpec(
                family = UiMessageFamily.ERROR,
                shortMessage = "   ",
            )
        }
    }

    @Test
    fun `short message - empty value throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            UiMessageSpec(
                family = UiMessageFamily.ERROR,
                shortMessage = "",
            )
        }
    }

    // ── next step single-step rule ──────────────────────────────────────────

    @Test
    fun `next step - null by default`() {
        val spec = UiMessageSpec.error("Oops.")
        assertNull(spec.nextStep)
    }

    @Test
    fun `next step - single OpenHelp step accepted`() {
        val step = UiNextStep.OpenHelp(labelRes = 0, url = "https://example.com/help")
        val spec = UiMessageSpec(
            family = UiMessageFamily.ERROR,
            shortMessage = "Need help?",
            nextStep = step,
        )
        assertNotNull(spec.nextStep)
        assertEquals(step, spec.nextStep)
    }

    @Test
    fun `next step - ReportProblem step accepted`() {
        val step = UiNextStep.ReportProblem(labelRes = 0, subject = "Bug report")
        val spec = UiMessageSpec(
            family = UiMessageFamily.ERROR,
            shortMessage = "That was unexpected.",
            nextStep = step,
        )
        assertEquals(step, spec.nextStep)
    }

    // ── success payload example ─────────────────────────────────────────────

    @Test
    fun `success payload - has correct family and no next step`() {
        val spec = UiMessageSpec.success("Done.")
        assertEquals(UiMessageFamily.SUCCESS, spec.family)
        assertNull(spec.nextStep)
        assertNull(spec.detailedMessage)
    }

    // ── error payload example ───────────────────────────────────────────────

    @Test
    fun `error payload - short message and optional details`() {
        val spec = UiMessageSpec.error(
            shortMessage = "Authentication failed.",
            detailedMessage = "STATUS_LOGON_FAILURE",
        )
        assertEquals(UiMessageFamily.ERROR, spec.family)
        assertEquals("Authentication failed.", spec.shortMessage)
        assertEquals("STATUS_LOGON_FAILURE", spec.detailedMessage)
        assertNull(spec.nextStep)
    }

    // ── empty state example ─────────────────────────────────────────────────

    @Test
    fun `empty state - invite with next step`() {
        val step = UiNextStep.LeaveFeedback(labelRes = 0)
        val spec = UiMessageSpec.emptyState("Nothing here yet.", nextStep = step)
        assertEquals(UiMessageFamily.EMPTY_STATE, spec.family)
        assertEquals(step, spec.nextStep)
    }
}
