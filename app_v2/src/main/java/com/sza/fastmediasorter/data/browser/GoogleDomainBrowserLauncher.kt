package com.sza.fastmediasorter.data.browser

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes auth URLs by host: Google domains → Chrome Custom Tabs; everything else → WebView fallback
 * via the caller-supplied lambda (strategic S0200 ADR-4).
 *
 * Callers in `ui/share/`, `ui/settings/auth/`, and `ui/settings/` (Phase 06 Google Account card)
 * use [routeAuthUrl] to keep the host-routing decision in one place. The WebView fallback path is
 * preserved for non-Google sources (Instagram, Facebook, etc.) where cookie-jar handoff is still
 * required for media-URL extraction.
 *
 * @throws CctUnavailableException when [launch] cannot find a CCT-capable browser.
 */
@Singleton
class GoogleDomainBrowserLauncher @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val cctChecker: CctAvailabilityChecker
) {
    /**
     * Launches [url] in Chrome Custom Tabs. Pinned to a specific CCT package so the user's default
     * browser does not redirect the session through an intent picker.
     *
     * @throws CctUnavailableException when no CCT-capable browser is installed.
     */
    fun launch(activityContext: Context, url: String) {
        val cctPackage = cctChecker.resolveCctPackage()
            ?: throw CctUnavailableException("No Chrome Custom Tabs-capable browser installed")
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .build()
        intent.intent.`package` = cctPackage
        intent.launchUrl(activityContext, Uri.parse(url))
    }

    /**
     * Routes [url] based on host:
     *  - Google-domain URL → [launch] via Chrome Custom Tabs (may throw [CctUnavailableException]).
     *  - Anything else → [onWebViewFallback] is invoked with the URL for legacy WebView flow.
     */
    fun routeAuthUrl(activityContext: Context, url: String, onWebViewFallback: (String) -> Unit) {
        if (GoogleDomainMatcher.isGoogleAuthUrl(url)) {
            launch(activityContext, url)
        } else {
            onWebViewFallback(url)
        }
    }
}
