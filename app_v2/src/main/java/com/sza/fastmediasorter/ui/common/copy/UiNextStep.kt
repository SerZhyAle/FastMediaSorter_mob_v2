package com.sza.fastmediasorter.ui.common.copy

/**
 * Single contextual next step offered to the user in a dead-end or problem scenario.
 *
 * S0118 ADR-3: at most one next step per surface. Destinations are:
 * - [OpenHelp]: open the relevant documentation page on the app website.
 * - [ReportProblem]: compose an email to sza@ukr.net for bug reports.
 * - [LeaveFeedback]: open Google Play review for improvement suggestions or general feedback.
 *
 * Carrying [labelRes] allows renderers to display a localized action label without
 * knowing which destination is wired.
 */
sealed interface UiNextStep {

    /** String resource id for the button or link label shown to the user. */
    val labelRes: Int

    /** Open the relevant help page on the app documentation website. */
    data class OpenHelp(
        override val labelRes: Int,
        /** Fully qualified URL resolved by the caller (locale-aware). */
        val url: String,
    ) : UiNextStep

    /** Open a bug-report email draft addressed to sza@ukr.net. */
    data class ReportProblem(
        override val labelRes: Int,
        /** Pre-filled email subject (optional). */
        val subject: String = "",
    ) : UiNextStep

    /** Open Google Play to leave a review or suggestion. */
    data class LeaveFeedback(
        override val labelRes: Int,
    ) : UiNextStep
}
