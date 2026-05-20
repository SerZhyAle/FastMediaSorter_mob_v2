package com.sza.fastmediasorter.ui.common.copy

import android.app.Activity
import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.error.ErrorSeverity
import com.sza.fastmediasorter.ui.dialog.ErrorDialog
import com.sza.fastmediasorter.util.AppErrorNotifier

/**
 * S0118: Renders a [UiMessageSpec] onto the appropriate UI surface.
 *
 * Routing rules:
 * - Short form (Snackbar / Toast) via [showShort] - only [UiMessageSpec.shortMessage] is shown.
 * - Detailed form (ErrorDialog) via [showDetailed] - shows [UiMessageSpec.shortMessage] as
 *   the primary text and [UiMessageSpec.detailedMessage] in the collapsible section when present.
 * - Settings-aware routing via [showRespectingSettings] delegates to [showDetailed] or
 *   [showShort] based on the caller's `showDetailedErrors` flag.
 *
 * This object is Android-UI aware but free of feature-specific strings.
 */
object UiMessageProjector {

    /**
     * Show the short form of [spec] as a Snackbar via [AppErrorNotifier].
     *
     * @param activity  Host activity. Must not be finishing or destroyed.
     * @param spec      Message payload. Only [UiMessageSpec.shortMessage] is rendered here.
     * @param severity  Routing severity passed to [AppErrorNotifier].
     * @param screenName Optional label for log output.
     */
    fun showShort(
        activity: Activity,
        spec: UiMessageSpec,
        severity: ErrorSeverity = defaultSeverityFor(spec),
        screenName: String? = null
    ) {
        AppErrorNotifier.show(
            activity = activity,
            message = spec.shortMessage,
            severity = severity,
            screenName = screenName
        )
    }

    /**
     * Show the detailed form of [spec] via [ErrorDialog].
     *
     * The [UiMessageSpec.shortMessage] is the primary text; [UiMessageSpec.detailedMessage]
     * populates the collapsible technical section when present.
     *
     * @param context   Context (Activity preferred for window token safety).
     * @param spec      Message payload.
     * @param title     Dialog title. Defaults to the generic "Error" string.
     */
    fun showDetailed(
        context: Context,
        spec: UiMessageSpec,
        title: String = context.getString(R.string.error)
    ) {
        ErrorDialog.show(
            context = context,
            title = title,
            message = spec.shortMessage,
            details = spec.detailedMessage
        )
    }

    /**
     * Route [spec] to the detailed or short surface based on [showDetailedErrors].
     *
     * When [showDetailedErrors] is `true`, the full [ErrorDialog] is shown.
     * When `false`, only the short Snackbar notification is shown.
     *
     * @param activity           Host activity.
     * @param spec               Message payload.
     * @param showDetailedErrors Setting value from [com.sza.fastmediasorter.domain.model.AppSettings].
     * @param title              Dialog title used only on the detailed path.
     * @param severity           Snackbar severity used only on the short path.
     * @param screenName         Optional label for log output on the short path.
     */
    fun showRespectingSettings(
        activity: Activity,
        spec: UiMessageSpec,
        showDetailedErrors: Boolean,
        title: String = activity.getString(R.string.error),
        severity: ErrorSeverity = defaultSeverityFor(spec),
        screenName: String? = null
    ) {
        if (showDetailedErrors) {
            showDetailed(
                context = activity,
                spec = spec,
                title = title
            )
        } else {
            showShort(
                activity = activity,
                spec = spec,
                severity = severity,
                screenName = screenName
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun defaultSeverityFor(spec: UiMessageSpec): ErrorSeverity = when (spec.family) {
        UiMessageFamily.ERROR, UiMessageFamily.CONFIRMATION -> ErrorSeverity.CRITICAL
        UiMessageFamily.EMPTY_STATE, UiMessageFamily.PROGRESS, UiMessageFamily.SUCCESS ->
            ErrorSeverity.DEBUG_ONLY
    }
}
