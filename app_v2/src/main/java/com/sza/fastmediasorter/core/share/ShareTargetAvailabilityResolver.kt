package com.sza.fastmediasorter.core.share

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.util.getPackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves whether a [ShareTarget] is currently usable and whether it is on by default (S0452).
 *
 * Single source of device-capability truth for share targets: package-installed, primary Google
 * account presence, and active-internet. Replaces the ad-hoc per-surface checks.
 */
@Singleton
class ShareTargetAvailabilityResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val googleIdentityRepository: GoogleIdentityRepository,
    private val mediaCapabilities: MediaCapabilities,
    settingsRepository: SettingsRepository,
    @ApplicationScope appScope: CoroutineScope,
) {

    // The companion toggle is a DataStore value but every caller here is synchronous, so the flag is
    // mirrored in memory on the application scope - same shape as RemoteSourceAvailabilityGate.
    private val wearCompanionEnabled = MutableStateFlow(false)

    init {
        appScope.launch {
            settingsRepository.getSettings()
                .map { it.enableWearCompanion }
                .collect { wearCompanionEnabled.value = it }
        }
    }

    /** @return true when the target's command may be shown on this device. */
    fun isAvailable(target: ShareTarget): Boolean = when (target.availability) {
        ShareTargetAvailability.ALWAYS -> true
        ShareTargetAvailability.PACKAGE_INSTALLED -> isAnyPackageInstalled(target.packages)
        ShareTargetAvailability.REQUIRES_GOOGLE -> hasGoogle()
        ShareTargetAvailability.REQUIRES_INTERNET -> hasInternet()
        ShareTargetAvailability.REQUIRES_WATCH -> hasWatch()
    }

    /** @return the default on/off value when the user has not explicitly toggled the target. */
    fun isDefaultEnabled(target: ShareTarget): Boolean = when (target.defaultEnabled) {
        ShareTargetDefault.ALWAYS_ON -> true
        ShareTargetDefault.ALWAYS_OFF -> false
        ShareTargetDefault.ON_IF_GOOGLE -> hasGoogle()
        ShareTargetDefault.ON_IF_INTERNET -> hasInternet()
        ShareTargetDefault.ON_IF_WATCH -> hasWatch()
    }

    private fun hasWatch(): Boolean =
        wearCompanionEnabled.value && mediaCapabilities.supportsWearCompanion

    private fun hasGoogle(): Boolean =
        googleIdentityRepository.state.value is PrimaryGoogleAccountState.Bound

    private fun hasInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isAnyPackageInstalled(packages: List<String>): Boolean {
        if (packages.isEmpty()) return false
        val pm = context.packageManager
        return packages.any { pkg ->
            try {
                pm.getPackageInfoCompat(pkg)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                // Expected on devices without this client; absence is not an error.
                Timber.i("ShareTarget package not installed: %s", pkg)
                false
            }
        }
    }
}
