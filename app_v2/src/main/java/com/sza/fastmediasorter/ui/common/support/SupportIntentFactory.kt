package com.sza.fastmediasorter.ui.common.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.util.LocaleHelper

/**
 * S0118: Builds Android intents for the three canonical S0118 follow-up channels.
 *
 * Strategic ADR-5 routing:
 * - Help → website (locale-aware URL).
 * - Report a problem → bug-report email destination defined as the [SUPPORT_MAILTO] constant.
 * - Leave feedback → Google Play review.
 *
 * Settings, dialogs, and future error surfaces all call this factory so neither
 * raw URLs nor the bug-report email address need to live next to each call site.
 */
object SupportIntentFactory {

    private const val DOCS_BASE_EN = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/"
    private const val DOCS_BASE_RU = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/index-ru.html"
    private const val DOCS_BASE_UK = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/index-uk.html"
    private const val SUPPORT_MAILTO = "mailto:sza@ukr.net"
    private const val PLAY_MARKET_URI_PREFIX = "market://details?id="
    private const val PLAY_WEB_URI_PREFIX = "https://play.google.com/store/apps/details?id="

    /**
     * Resolve a canonical [SupportDestination] into a launchable [Intent].
     *
     * @param context  Used for locale lookup and the Play Store package id.
     * @param destination  The S0118 follow-up channel.
     * @param emailSubject Optional pre-filled email subject for [SupportDestination.REPORT_PROBLEM].
     */
    fun build(
        context: Context,
        destination: SupportDestination,
        emailSubject: String = "",
    ): Intent = when (destination) {
        SupportDestination.HELP -> openHelp(context)
        SupportDestination.REPORT_PROBLEM -> reportProblem(emailSubject)
        SupportDestination.LEAVE_FEEDBACK -> leaveFeedback(context)
    }

    /** Locale-aware URL for the in-app docs entry page. */
    fun helpUrl(context: Context): String = when (LocaleHelper.getLanguage(context)) {
        "ru" -> DOCS_BASE_RU
        "uk" -> DOCS_BASE_UK
        else -> DOCS_BASE_EN
    }

    private fun openHelp(context: Context): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl(context)))

    private fun reportProblem(subject: String): Intent {
        val builder = Uri.parse(SUPPORT_MAILTO).buildUpon()
        if (subject.isNotBlank()) {
            builder.appendQueryParameter("subject", subject)
        }
        val intent = Intent(Intent.ACTION_SENDTO, builder.build())
        if (subject.isNotBlank()) {
            intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        return intent
    }

    private fun leaveFeedback(context: Context): Intent {
        val pkg = context.packageName
        // Prefer the Play Store app via market://; if it is not installed, the
        // caller can fall back to the web URL via [leaveFeedbackWebFallback].
        val marketUri = Uri.parse("$PLAY_MARKET_URI_PREFIX$pkg")
        return Intent(Intent.ACTION_VIEW, marketUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Web fallback for [SupportDestination.LEAVE_FEEDBACK] when the Play Store app is missing. */
    fun leaveFeedbackWebFallback(context: Context): Intent {
        val pkg = context.packageName
        return Intent(Intent.ACTION_VIEW, Uri.parse("$PLAY_WEB_URI_PREFIX$pkg"))
    }

    /** Default subject prefix used when callers do not pass an explicit one. */
    fun defaultBugSubject(): String =
        "FastMediaSorter ${BuildConfig.VERSION_NAME} bug report"

    /**
     * Build an `ACTION_VIEW` intent for an arbitrary documentation [url]. Used by
     * surface-specific help (e.g. F1 keyboard shortcut docs) that resolves the URL
     * via its own resolver but still wants to share the factory's intent shape.
     */
    fun openUrl(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
}
