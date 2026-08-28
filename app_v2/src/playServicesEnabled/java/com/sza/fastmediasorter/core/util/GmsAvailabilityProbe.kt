package com.sza.fastmediasorter.core.util

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import timber.log.Timber

/**
 * The one place `com.google.android.gms.common` is named outside a flavor source set would be
 * [GmsAvailabilityChecker], so it is named here instead (strategic S0403).
 *
 * `playServicesEnabled` carries this real probe, `playServicesDisabled` carries the inert twin
 * under the same FQCN - the LanguageSplitInstaller / ReviewRequestManager pattern. Every flavor
 * mounts exactly one of the two.
 */
internal object GmsAvailabilityProbe {

    /**
     * Whether asking about Play Services is meaningful at all in this build. False in a build that
     * does not link the library, which lets [GmsAvailabilityChecker.check] skip publishing a verdict
     * the user could not act on.
     */
    const val IS_SUPPORTED: Boolean = true

    fun evaluate(context: Context, minApkVersion: Int): GmsAvailabilityChecker.Status {
        return try {
            val code = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context, minApkVersion)
            when (code) {
                ConnectionResult.SUCCESS -> GmsAvailabilityChecker.Status.OK
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                    Timber.w(
                        "GmsAvailabilityChecker: Google Play Services update required " +
                            "(code=$code, minApkVersion=$minApkVersion)"
                    )
                    GmsAvailabilityChecker.Status.UPDATE_REQUIRED
                }
                else -> {
                    Timber.w(
                        "GmsAvailabilityChecker: Google Play Services unavailable " +
                            "(code=$code, minApkVersion=$minApkVersion)"
                    )
                    GmsAvailabilityChecker.Status.UNAVAILABLE
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "GmsAvailabilityChecker: check failed (minApkVersion=$minApkVersion)")
            GmsAvailabilityChecker.Status.UNAVAILABLE
        }
    }
}
