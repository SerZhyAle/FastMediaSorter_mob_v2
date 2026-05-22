package com.sza.fastmediasorter.core.util

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
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
        // check entirely so [status] stays [Status.OK] by default — this naturally suppresses
        // the BaseActivity snackbar and the GeneralSettingsFragment CTA row.
        // [recheckFor] is intentionally NOT guarded: cloud sign-in flows that probe Credential
        // Manager still need an honest live answer (it will simply report UNAVAILABLE on Quest).
        if (XrDeviceProbe.isXrDevice(context)) {
            Timber.i("GmsAvailabilityChecker: XR device detected, skipping GMS availability check")
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

    private fun evaluate(context: Context, minApkVersion: Int): Status {
        return try {
            val code = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context, minApkVersion)
            when (code) {
                ConnectionResult.SUCCESS -> Status.OK
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                    Timber.w("GmsAvailabilityChecker: Google Play Services update required (code=$code, minApkVersion=$minApkVersion)")
                    Status.UPDATE_REQUIRED
                }
                else -> {
                    Timber.w("GmsAvailabilityChecker: Google Play Services unavailable (code=$code, minApkVersion=$minApkVersion)")
                    Status.UNAVAILABLE
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "GmsAvailabilityChecker: check failed (minApkVersion=$minApkVersion)")
            Status.UNAVAILABLE
        }
    }

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
