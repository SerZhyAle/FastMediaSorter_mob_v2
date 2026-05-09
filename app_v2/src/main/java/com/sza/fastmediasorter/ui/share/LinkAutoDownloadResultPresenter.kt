package com.sza.fastmediasorter.ui.share

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator
import com.sza.fastmediasorter.ui.player.StandalonePlayerActivity
import com.sza.fastmediasorter.ui.share.auth.WebViewAuthDialogFragment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar M: single entry point projecting [LinkAutoDownloadCoordinator.Result]
 * into the user-visible UX (open-in-player intent, error toast, auth dialog).
 *
 * Reuses the existing S0003 settings (`linkAutoDownloadOpenInPlayer`) — every new
 * outcome surfaced by Phases 03-05 maps through this presenter so `ReceiveShareActivity`
 * stays a thin shell.
 */
@Singleton
class LinkAutoDownloadResultPresenter @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: SettingsRepository,
) {

    /**
     * @param onAuthRetryRequested optional callback for the WebView-auth flow's
     *  retry hook. The presenter itself only launches the dialog; the host can
     *  re-run [LinkAutoDownloadCoordinator.handle] with the original URL.
     */
    suspend fun present(
        result: LinkAutoDownloadCoordinator.Result,
        hostActivity: AppCompatActivity,
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
            LinkAutoDownloadCoordinator.Result.Failed.NoNetwork ->
                toast(R.string.link_autodownload_error_no_network)
            LinkAutoDownloadCoordinator.Result.Failed.Timeout ->
                toast(R.string.link_autodownload_error_timeout)
            LinkAutoDownloadCoordinator.Result.Failed.NoMediaFound ->
                toast(R.string.link_autodownload_error_no_media)
            LinkAutoDownloadCoordinator.Result.Failed.MimeBlocked ->
                toast(R.string.link_autodownload_error_mime_blocked)
            LinkAutoDownloadCoordinator.Result.Failed.DrmBlocked ->
                toast(R.string.s0116_toast_drm_blocked)
            LinkAutoDownloadCoordinator.Result.Failed.StreamingDisabled ->
                toast(R.string.s0116_toast_streaming_disabled)
            is LinkAutoDownloadCoordinator.Result.Failed.MuxFailed ->
                toast(R.string.s0116_toast_mux_failed, result.codec)
            is LinkAutoDownloadCoordinator.Result.Failed.AuthRequired -> {
                if (openInPlayer) {
                    // S0116 §5.1 pillar L: launch the WebView dialog so the user can sign in.
                    // The retry hook is invoked by the host after the dialog dismisses.
                    runCatching {
                        WebViewAuthDialogFragment.newInstance(result.originalUrl)
                            .show(hostActivity.supportFragmentManager, "s0116_webview_auth_retry")
                    }.onFailure { Timber.w(it, "S0116: WebView auth dialog launch failed") }
                    // Caller-side retry hook (no-op default). Wrapped in catch to keep presenter pure.
                    runCatching { onAuthRetryRequested(result.originalUrl) }
                } else {
                    toast(R.string.s0116_toast_auth_required, result.host)
                }
            }
            is LinkAutoDownloadCoordinator.Result.Failed.Other ->
                toast(
                    R.string.receive_share_cache_failed,
                    result.cause.message ?: result.cause::class.java.simpleName,
                )
        }
    }

    private fun launchPlayer(host: AppCompatActivity, uri: android.net.Uri) {
        try {
            val intent = Intent(host, StandalonePlayerActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            host.startActivity(intent)
        } catch (t: Throwable) {
            Timber.e(t, "S0116: failed to launch player for %s", uri)
        }
    }

    private fun toast(stringRes: Int, vararg args: Any) {
        val text = if (args.isEmpty()) appContext.getString(stringRes)
        else appContext.getString(stringRes, *args)
        Toast.makeText(appContext, text, Toast.LENGTH_LONG).show()
    }
}
