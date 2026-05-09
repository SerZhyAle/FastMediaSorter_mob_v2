package com.sza.fastmediasorter.ui.common.support

/**
 * S0118: Single contextual S0118 follow-up destination.
 *
 * Routing canon (strategic ADR-5):
 * - [HELP]:           website docs page (locale-aware URL).
 * - [REPORT_PROBLEM]: bug-report email channel resolved by [SupportIntentFactory].
 * - [LEAVE_FEEDBACK]: Google Play review for improvement suggestions or general feedback.
 *
 * Resolved into Android intents by [SupportIntentFactory].
 */
enum class SupportDestination {
    HELP,
    REPORT_PROBLEM,
    LEAVE_FEEDBACK,
}
