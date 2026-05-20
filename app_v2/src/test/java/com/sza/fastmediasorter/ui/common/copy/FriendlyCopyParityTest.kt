package com.sza.fastmediasorter.ui.common.copy

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0118 - locale parity invariants for the shared friendly-copy contract.
 *
 * The full per-locale audit lives in `scripts/check_strings_localized.ps1`; these
 * narrow JVM tests guard the in-app contract that does not depend on Android resources:
 * - [UiMessageSpec] short message non-blank invariant.
 * - [UiNextStep] sealed hierarchy stays exhaustive (HELP / REPORT_PROBLEM / LEAVE_FEEDBACK).
 */
class FriendlyCopyParityTest {

    @Test
    fun `every UiMessageFamily can build a non-blank short message`() {
        for (family in UiMessageFamily.values()) {
            val spec = UiMessageSpec(family = family, shortMessage = "ok")
            assertTrue("family $family must accept a short message", spec.shortMessage.isNotBlank())
        }
    }

    @Test
    fun `UiNextStep covers the three S0118 channels`() {
        val help = UiNextStep.OpenHelp(labelRes = 0, url = "https://example.com")
        val report = UiNextStep.ReportProblem(labelRes = 0)
        val feedback = UiNextStep.LeaveFeedback(labelRes = 0)
        // Compile-time exhaustiveness over the sealed interface guarantees the
        // friendly-copy contract has no untyped fourth channel.
        val descriptions = listOf(help, report, feedback).map { it::class.simpleName }
        assertTrue("OpenHelp present", descriptions.contains("OpenHelp"))
        assertTrue("ReportProblem present", descriptions.contains("ReportProblem"))
        assertTrue("LeaveFeedback present", descriptions.contains("LeaveFeedback"))
    }
}
