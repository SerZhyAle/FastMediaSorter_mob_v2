package com.sza.fastmediasorter.core.util

import android.content.Context
import timber.log.Timber

/**
 * One-time GMS availability check cached for the process lifetime.
 * Result is used by [com.sza.fastmediasorter.core.ui.BaseActivity] to surface
 * a non-blocking update recommendation to the user.
 *
 * The cached [status] reflects the default GMS version check (`minApkVersion = 0`), which only
 * verifies that the installed GMS is at least as new as the version `play-services-auth` was
 * built against. Specific Google APIs require newer GMS than that baseline - callers that need
 * such a stricter check should use [recheckFor] with the appropriate minimum version constant.
 */
object GmsAvailabilityChecker {

    enum class Status { OK, UPDATE_REQUIRED, UNAVAILABLE }

    /**
     * Minimum Google Play Services version (`com.google.android.gms` versionCode) required by
     * Credential Manager's `credentials-play-services-auth` provider plugin. Devices below this
     * version (e.g. emulators pinned to older system images) fail Credential Manager calls with
     * `GetCredentialProviderConfigurationException: no provider dependencies found` - the generic
     * `isGooglePlayServicesAvailable(context)` returns SUCCESS for them, so a stricter check is
     * needed before invoking `CredentialManager.getCredential()`.
     *
     * Value `230815045` corresponds to GMS `23.08.15` (the first GMS release that exposes the
     * Credential Manager backport provider that `androidx.credentials:credentials-play-services-auth:1.3.0`
     * binds to). Bump this constant when the credentials library is upgraded.
     */
    const val MIN_GMS_VERSION_FOR_CREDENTIAL_MANAGER: Int = 230815045

    private const val PREFS_KEY_WARNING_SEEN = "gms_warning_seen"

    @Volatile
    var status: Status = Status.OK
        private set

    fun check(context: Context) {
        // XR / VR headsets (Meta Quest, Android XR devices) do not ship Google Play Services
        // and there is no "update Play Services" CTA the user can actually act on. Skip the
        // check entirely so [status] stays [Status.OK] by default - this naturally suppresses
        // the BaseActivity snackbar and the GeneralSettingsFragment CTA row.
        // [recheckFor] is intentionally NOT guarded: cloud sign-in flows that probe Credential
        // Manager still need an honest live answer (it will simply report UNAVAILABLE on Quest).
        if (XrDeviceProbe.isXrDevice(context)) {
            Timber.i("GmsAvailabilityChecker: XR device detected, skipping GMS availability check")
            return
        }
        // S0403: same reasoning, one step earlier - a build that does not link Play Services cannot
        // offer the "update Play Services" CTA either, and BaseActivity raises its snackbar on ANY
        // non-OK cached status. Leaving the cache at OK is what keeps that entry point absent.
        // [evaluateLive] and [recheckFor] stay honest for callers that branch on the real answer.
        if (!GmsAvailabilityProbe.IS_SUPPORTED) {
            return
        }
        status = evaluate(context, minApkVersion = 0)
    }

    /**
     * Re-runs the availability check against a specific minimum GMS version and updates the cached
     * [status] accordingly. Used by API-specific guards (e.g. Credential Manager sign-in) where the
     * default version baseline is insufficient. Always live - does not consult the cache, because
     * GMS may be updated mid-session via Play Store.
     */
    fun recheckFor(context: Context, minApkVersion: Int): Status {
        val result = evaluate(context, minApkVersion)
        status = result
        return result
    }

    /**
     * S1644: a live answer that deliberately does NOT write [status].
     *
     * [check] returns early on an XR device and leaves the cache at [Status.OK]; [recheckFor] is live but
     * publishes its verdict into the same cache that [com.sza.fastmediasorter.core.ui.BaseActivity] reads
     * to raise its update prompt - so on a device without Play Services it would offer the user a CTA
     * they cannot act on. A caller that needs the truth for its own branch, and no side effect on anyone
     * else's prompt, asks here.
     */
    fun evaluateLive(context: Context): Status = evaluate(context, minApkVersion = 0)

    // S0403: the GMS call itself lives in GmsAvailabilityProbe, which has one copy per flavor
    // (src/playServicesEnabled, src/playServicesDisabled). That is the only reason this file no
    // longer names com.google.android.gms - it is otherwise unchanged.
    private fun evaluate(context: Context, minApkVersion: Int): Status =
        GmsAvailabilityProbe.evaluate(context, minApkVersion)

    fun isWarningSeen(context: Context): Boolean =
        prefs(context).getBoolean(PREFS_KEY_WARNING_SEEN, false)

    fun markWarningSeen(context: Context) =
        prefs(context).edit().putBoolean(PREFS_KEY_WARNING_SEEN, true).apply()

    private fun prefs(context: Context) =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    val needsUpdate get() = status == Status.UPDATE_REQUIRED
    val isUnavailable get() = status == Status.UNAVAILABLE
    val isOk get() = status == Status.OK
}
