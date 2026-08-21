package com.sza.fastmediasorter.ui.common.support

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.util.LocaleHelper
import timber.log.Timber

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
    private const val CRASH_REPORT_EMAIL = "serzhyale@gmail.com"
    private const val PLAY_MARKET_URI_PREFIX = "market://details?id="
    private const val PLAY_WEB_URI_PREFIX = "https://play.google.com/store/apps/details?id="
    private const val COMPANION_PUBLISH_GUIDE_URL =
        "https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html"
    private const val COMPANION_HOME_URL = "https://serzhyale.github.io/FastMediaSorter_Lite/"
    private const val WEAR_INSTALL_EN =
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/wear-install.html"
    private const val WEAR_INSTALL_RU =
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/wear-install-ru.html"
    private const val WEAR_INSTALL_UK =
        "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/wear-install-uk.html"

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

    /** S0994: single source for the LITE companion publish-folders guide (EN-only page). */
    fun companionPublishGuideUrl(): String = COMPANION_PUBLISH_GUIDE_URL

    /** Landing page of the Windows companion ("Fast Media Sorter for Windows"). */
    fun companionHomeUrl(): String = COMPANION_HOME_URL

    /** S1883: locale-aware URL for the "put FastMedia on your watch" guide. */
    fun wearInstallGuideUrl(context: Context): String = when (LocaleHelper.getLanguage(context)) {
        "ru" -> WEAR_INSTALL_RU
        "uk" -> WEAR_INSTALL_UK
        else -> WEAR_INSTALL_EN
    }

    private fun openHelp(context: Context): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl(context)))

    private fun reportProblem(subject: String): Intent {
        // mailto: is an opaque URI; Uri.Builder.appendQueryParameter drops the opaque address part
        // (producing "mailto:?subject=..") so the recipient is lost. Concatenate the query manually.
        val uriString = if (subject.isNotBlank()) {
            "$SUPPORT_MAILTO?subject=${Uri.encode(subject)}"
        } else {
            SUPPORT_MAILTO
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uriString))
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
     * Build a crash-report email to the author with an optional log attachment.
     *
     * Uses ACTION_SEND, not ACTION_SENDTO: only ACTION_SEND carries [Intent.EXTRA_STREAM], so a
     * plain mailto:-based intent would silently drop the attachment. The recipient travels in
     * [Intent.EXTRA_EMAIL].
     *
     * A bare ACTION_SEND resolves to the generic share sheet (Drive, Bluetooth, messengers..), which
     * is not what "email the author" means and on some OEM share panels just flickers shut. The
     * mailto: [Intent.setSelector] restricts resolution to email apps while ACTION_SEND still carries
     * the attachment. The selector means the result MUST be launched directly: [Intent.createChooser]
     * strips the selector.
     *
     * Callers should not launch this intent themselves - use [launchCrashReport], which keeps the
     * email-first behavior but falls back to the generic share sheet when no email app is installed.
     */
    fun buildCrashReportEmail(
        subject: String,
        body: String,
        attachmentUri: Uri?,
    ): Intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(CRASH_REPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        if (attachmentUri != null) {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, attachmentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
        selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
    }

    /**
     * Launch the crash-report email with a graceful fallback chain so the report is never lost:
     * 1. Direct email app via the mailto: selector - keeps the recipient + subject pre-filled.
     * 2. No email app: strip the selector and open the generic share sheet so the zip can still
     *    reach Drive, messengers, file managers, etc.
     *
     * Returns false only when even the generic share sheet has no target (fully sandboxed/kiosk
     * device); the caller should then tell the user the report stays saved on internal storage.
     */
    fun launchCrashReport(
        context: Context,
        subject: String,
        body: String,
        attachmentUri: Uri?,
    ): Boolean {
        val emailIntent = buildCrashReportEmail(subject, body, attachmentUri)
        if (context !is Activity) emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(emailIntent)
            return true
        } catch (e: ActivityNotFoundException) {
            Timber.i(e, "SupportIntentFactory: no email app, falling back to generic share sheet")
        }
        // Drop the mailto selector so any share target (not just email apps) can receive the zip.
        val shareIntent = buildCrashReportEmail(subject, body, attachmentUri).apply { selector = null }
        val chooser = Intent.createChooser(shareIntent, subject)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "SupportIntentFactory: no share target available for crash report")
            false
        }
    }

    /**
     * Build an `ACTION_VIEW` intent for an arbitrary documentation [url]. Used by
     * surface-specific help (e.g. F1 keyboard shortcut docs) that resolves the URL
     * via its own resolver but still wants to share the factory's intent shape.
     */
    fun openUrl(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
}
