package com.sza.fastmediasorter.core.util

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import timber.log.Timber

/**
 * One-time GMS availability check cached for the process lifetime.
 * Result is used by [com.sza.fastmediasorter.core.ui.BaseActivity] to surface
 * a non-blocking update recommendation to the user.
 */
object GmsAvailabilityChecker {

    enum class Status { OK, UPDATE_REQUIRED, UNAVAILABLE }

    @Volatile
    var status: Status = Status.OK
        private set

    fun check(context: Context) {
        try {
            val code = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context)
            status = when (code) {
                ConnectionResult.SUCCESS -> Status.OK
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                    Timber.w("GmsAvailabilityChecker: Google Play Services update required (code=$code)")
                    Status.UPDATE_REQUIRED
                }
                else -> {
                    Timber.w("GmsAvailabilityChecker: Google Play Services unavailable (code=$code)")
                    Status.UNAVAILABLE
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "GmsAvailabilityChecker: check failed")
            status = Status.UNAVAILABLE
        }
    }

    val needsUpdate get() = status == Status.UPDATE_REQUIRED
    val isUnavailable get() = status == Status.UNAVAILABLE
    val isOk get() = status == Status.OK
}
