package com.sza.fastmediasorter.ui.share

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.data.browser.CctAvailabilityChecker
import com.sza.fastmediasorter.data.browser.CctUnavailableException
import com.sza.fastmediasorter.data.browser.GoogleDomainBrowserLauncher
import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator
import com.sza.fastmediasorter.ui.player.StandalonePlayerActivity
import com.sza.fastmediasorter.ui.share.auth.WebViewAuthDialogFragment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkAutoDownloadResultPresenter @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: SettingsRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val googleDomainBrowserLauncher: GoogleDomainBrowserLauncher,
    private val cctChecker: CctAvailabilityChecker,
) {

    suspend fun present(
        result: LinkAutoDownloadCoordinator.Result,
        hostActivity: AppCompatActivity,
        isAuthRetry: Boolean = false,
        onAuthRetryRequested: suspend (originalUrl: String) -> Unit = {},
    ) {
        val openInPlayer = settings.getSettings().first().linkAutoDownloadOpenInPlayer
        LinkDownloadTrace.tag(
            "post-download UX, openInPlayer=$openInPlayer, outcome=${result::class.java.simpleName}",
        )

        when (result) {
            is LinkAutoDownloadCoordinator.Result.Saved -> {
                if (openInPlayer && result.openInPlayerUri != null) {
                    launchPlayer(hostActivity, result.openInPlayerUri)
                } else {
                    toast(R.string.s0116_toast_saved_to_resource, result.fileName)
                }
            }
            is LinkAutoDownloadCoordinator.Result.FellBackToDownloads -> {
                if (openInPlayer && result.openInPlayerUri != null) {
                    launchPlayer(hostActivity, result.openInPlayerUri)
                } else {
                    toast(R.string.s0116_toast_saved_to_downloads, result.fileName)
                }
            }
            is LinkAutoDownloadCoordinator.Result.BatchCompleted -> {
                if (result.summary.failureCount == 0) {
                    toast(R.string.s0117_toast_batch_saved, result.summary.successCount)
                } else {
                    showBatchSummary(hostActivity, result.summary)
                }
            }
            LinkAutoDownloadCoordinator.Result.Failed.NoNetwork -> toast(R.string.link_autodownload_error_no_network)
            LinkAutoDownloadCoordinator.Result.Failed.Timeout -> toast(R.string.link_autodownload_error_timeout)
            LinkAutoDownloadCoordinator.Result.Failed.NoMediaFound -> toast(R.string.link_autodownload_error_no_media)
            LinkAutoDownloadCoordinator.Result.Failed.UnsupportedYouTubeCommunityPost ->
                toast(R.string.link_autodownload_error_youtube_community_post)
            LinkAutoDownloadCoordinator.Result.Failed.DownloadCorrupted -> toast(R.string.link_autodownload_error_corrupted)
            LinkAutoDownloadCoordinator.Result.Failed.MimeBlocked -> toast(R.string.link_autodownload_error_mime_blocked)
            LinkAutoDownloadCoordinator.Result.Failed.DrmBlocked -> toast(R.string.s0116_toast_drm_blocked)
            LinkAutoDownloadCoordinator.Result.Failed.StreamingDisabled -> toast(R.string.s0116_toast_streaming_disabled)
            is LinkAutoDownloadCoordinator.Result.Failed.MuxFailed -> toast(R.string.s0116_toast_mux_failed, result.codec)
            is LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly -> {
                presentSocialPreviewOnly(result, hostActivity, isAuthRetry, onAuthRetryRequested)
            }
            is LinkAutoDownloadCoordinator.Result.Failed.AuthRequired -> {
                if (openInPlayer) {
                    runCatching {
                        openAuthFlow(hostActivity, result.originalUrl, "s0116_webview_auth_retry")
                    }.onFailure { Timber.w(it, "S0116: auth flow launch failed") }
                    runCatching { onAuthRetryRequested(result.originalUrl) }
                } else {
                    toast(R.string.s0116_toast_auth_required, result.host)
                }
            }
            is LinkAutoDownloadCoordinator.Result.Failed.Other -> toast(R.string.receive_share_cache_failed)
        }
    }

    private suspend fun presentSocialPreviewOnly(
        result: LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly,
        hostActivity: AppCompatActivity,
        isAuthRetry: Boolean,
        onAuthRetryRequested: suspend (originalUrl: String) -> Unit,
    ) {
        val isDismissed = result.accountId
            ?.takeIf { it.isNotBlank() }
            ?.let { authSessionRepository.isDismissedForAccount(result.host, it) }
            ?: authSessionRepository.isDismissedForHost(result.host)
        if (isDismissed) {
            toast(R.string.s0151_toast_content_unavailable)
            return
        }

        // If we already had a session and the auth-retry round also failed — the extractor
        // cannot parse media from this page regardless of credentials.
        // Do NOT show the re-auth dialog again; that creates an infinite loop where the user
        // keeps seeing the already-logged-in WebView and pressing Save, to no avail.
        if (isAuthRetry && result.hadExistingSession) {
            Timber.i(
                "[S0166] retry+session still preview-only — extractor limitation, not an auth issue: host=%s",
                result.host,
            )
            toast(R.string.s0151_toast_content_unavailable)
            return
        }

        // When a session already exists but extraction still failed — toast and stop.
        // The invisible WebView (InvisibleWebViewExtractionStrategy) already ran with the
        // stored cookies. If it returned social-preview-only it means the page structure
        // is not yet supported. Opening a visible browser here would confuse the user.
        if (result.hadExistingSession) {
            Timber.i(
                "[S0166] existing session, extraction still failed — toast only: host=%s",
                result.host,
            )
            toast(R.string.s0151_toast_content_unavailable)
            return
        }

        val loginUrl = KnownAuthResources.matchHost(result.host)?.loginUrl ?: result.originalUrl
        val displayName = result.accountDisplayName
        val title: String
        val message: String
        val positiveLabel: String
        when {
            displayName != null -> {
                title = appContext.getString(R.string.s0155_reauth_title, displayName)
                message = appContext.getString(R.string.s0155_reauth_message, displayName)
                positiveLabel = appContext.getString(R.string.s0155_reauth_positive)
            }
            else -> {
                title = appContext.getString(R.string.s0151_dialog_auth_title, result.host)
                message = appContext.getString(R.string.s0151_dialog_message)
                positiveLabel = appContext.getString(R.string.auth_offer_dialog_add)
            }
        }

        MaterialAlertDialogBuilder(hostActivity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(positiveLabel) { _, _ ->
                hostActivity.supportFragmentManager.setFragmentResultListener(
                    WebViewAuthDialogFragment.RESULT_KEY,
                    hostActivity,
                ) { _, bundle ->
                    hostActivity.supportFragmentManager.clearFragmentResultListener(WebViewAuthDialogFragment.RESULT_KEY)
                    val mediaUrl = bundle.getString(WebViewAuthDialogFragment.RESULT_MEDIA_URL)
                    hostActivity.lifecycleScope.launch {
                        runCatching { onAuthRetryRequested(mediaUrl ?: result.originalUrl) }
                    }
                }
                runCatching {
                    openAuthFlow(hostActivity, loginUrl, "s0151_webview_reauth")
                }.onFailure {
                    Timber.w(it, "S0151/S0155: reauth auth flow launch failed")
                    toast(R.string.s0151_toast_content_unavailable)
                }
            }
            .setNeutralButton(R.string.auth_offer_dialog_skip) { _, _ ->
                toast(R.string.s0151_toast_content_unavailable)
            }
            .setNegativeButton(R.string.s0157_auth_offer_dismiss_always) { _, _ ->
                hostActivity.lifecycleScope.launch {
                    val accountId = result.accountId?.takeIf { it.isNotBlank() }
                    if (accountId != null) {
                        authSessionRepository.markDismissedForAccount(
                            host = result.host,
                            accountId = accountId,
                            displayName = result.accountDisplayName,
                        )
                    } else {
                        authSessionRepository.markDismissed(result.host)
                    }
                }
                toast(R.string.s0151_toast_content_unavailable)
            }
            .show()
    }

    private fun showBatchSummary(
        hostActivity: AppCompatActivity,
        summary: LinkAutoDownloadCoordinator.Result.BatchSummary,
    ) {
        val lines = mutableListOf(
            appContext.getString(
                R.string.s0117_batch_dialog_summary,
                summary.successCount,
                summary.totalItems,
            ),
        )
        summary.failures.take(MAX_DIALOG_FAILURES).forEach { failure ->
            lines += appContext.getString(
                R.string.s0117_batch_dialog_failure_line,
                failure.title,
                renderFailureReason(failure.failure),
            )
        }
        val remaining = summary.failureCount - MAX_DIALOG_FAILURES
        if (remaining > 0) {
            lines += appContext.getString(R.string.s0117_batch_dialog_more_failures, remaining)
        }

        AlertDialog.Builder(hostActivity)
            .setTitle(R.string.s0117_batch_dialog_title)
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun renderFailureReason(failure: LinkAutoDownloadCoordinator.Result.Failed): String {
        return when (failure) {
            LinkAutoDownloadCoordinator.Result.Failed.NoNetwork ->
                appContext.getString(R.string.link_autodownload_error_no_network)
            LinkAutoDownloadCoordinator.Result.Failed.Timeout ->
                appContext.getString(R.string.link_autodownload_error_timeout)
            LinkAutoDownloadCoordinator.Result.Failed.NoMediaFound ->
                appContext.getString(R.string.link_autodownload_error_no_media)
            LinkAutoDownloadCoordinator.Result.Failed.UnsupportedYouTubeCommunityPost ->
                appContext.getString(R.string.link_autodownload_error_youtube_community_post)
            LinkAutoDownloadCoordinator.Result.Failed.DownloadCorrupted ->
                appContext.getString(R.string.link_autodownload_error_corrupted)
            LinkAutoDownloadCoordinator.Result.Failed.MimeBlocked ->
                appContext.getString(R.string.link_autodownload_error_mime_blocked)
            LinkAutoDownloadCoordinator.Result.Failed.DrmBlocked ->
                appContext.getString(R.string.s0116_toast_drm_blocked)
            LinkAutoDownloadCoordinator.Result.Failed.StreamingDisabled ->
                appContext.getString(R.string.s0116_toast_streaming_disabled)
            is LinkAutoDownloadCoordinator.Result.Failed.MuxFailed ->
                appContext.getString(R.string.s0116_toast_mux_failed, failure.codec)
            is LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly ->
                appContext.getString(R.string.s0151_toast_content_unavailable)
            is LinkAutoDownloadCoordinator.Result.Failed.AuthRequired ->
                appContext.getString(R.string.s0116_toast_auth_required, failure.host)
            is LinkAutoDownloadCoordinator.Result.Failed.Other ->
                appContext.getString(R.string.receive_share_cache_failed)
        }
    }

    private fun launchPlayer(host: AppCompatActivity, uri: android.net.Uri) {
        try {
            val intent = Intent(host, StandalonePlayerActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            host.startActivity(intent)
        } catch (throwable: Throwable) {
            Timber.e(throwable, "S0116: failed to launch player for %s", uri)
        }
    }

    private fun toast(stringRes: Int, vararg args: Any) {
        val text = if (args.isEmpty()) appContext.getString(stringRes) else appContext.getString(stringRes, *args)
        Toast.makeText(appContext, text, Toast.LENGTH_LONG).show()
    }

    /**
     * S0200 — host-aware router. Google-domain URLs go to Chrome Custom Tabs; everything else
     * keeps the legacy WebView flow. CCT-unavailable triggers the refusal dialog on [hostActivity].
     */
    private fun openAuthFlow(hostActivity: AppCompatActivity, url: String, tag: String) {
        try {
            googleDomainBrowserLauncher.routeAuthUrl(hostActivity, url) { fallbackUrl ->
                WebViewAuthDialogFragment.newInstance(fallbackUrl)
                    .show(hostActivity.supportFragmentManager, tag)
            }
        } catch (e: CctUnavailableException) {
            Timber.w(e, "LinkAutoDownloadResultPresenter: CCT unavailable for url=%s", url)
            showCctUnavailableDialog(hostActivity) { openAuthFlow(hostActivity, url, tag) }
        }
    }

    private fun showCctUnavailableDialog(hostActivity: AppCompatActivity, onRetry: () -> Unit) {
        MaterialAlertDialogBuilder(hostActivity)
            .setTitle(R.string.s0200_cct_unavailable_title)
            .setMessage(R.string.s0200_cct_unavailable_message)
            .setPositiveButton(R.string.s0200_cct_unavailable_retry) { _, _ ->
                if (cctChecker.isAvailable()) onRetry() else showCctUnavailableDialog(hostActivity, onRetry)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        const val MAX_DIALOG_FAILURES = 5
    }
}