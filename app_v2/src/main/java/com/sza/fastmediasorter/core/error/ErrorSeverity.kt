package com.sza.fastmediasorter.core.error

/**
 * Severity level for application error notifications.
 *
 * - [CRITICAL]: shown in both debug and release builds with a red indicator.
 *   Use for errors that impair user-visible functionality (failed file operation,
 *   connection failure, unexpected crash recovery, etc.).
 *
 * - [DEBUG_ONLY]: shown only when [com.sza.fastmediasorter.BuildConfig.DEBUG] is `true`,
 *   with a yellow/amber indicator. Completely suppressed in release builds.
 *   Use for non-fatal diagnostic warnings that are irrelevant to end users.
 */
enum class ErrorSeverity {
    CRITICAL,
    DEBUG_ONLY
}
