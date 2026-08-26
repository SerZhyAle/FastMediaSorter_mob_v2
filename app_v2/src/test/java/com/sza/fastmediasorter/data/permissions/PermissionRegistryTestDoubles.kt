package com.sza.fastmediasorter.data.permissions

import android.content.ComponentName
import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract

/**
 * S2013: the registry asks the launcher and Network Monitor capability seams which wording the location
 * row should carry, so constructing it in a test needs both seams.
 *
 * The defaults mirror the variant the test actually runs under, which is what keeps every pre-existing
 * assertion judging the same build it judged before. A test that cares about the wording passes the two
 * flags explicitly instead, and so covers a flavor the run itself is not.
 */
internal class FakeLauncherModeContract(
    override val isAvailableInBuild: Boolean = BuildConfig.SUPPORT_LAUNCHER,
) : LauncherModeContract {

    override fun homeComponent(context: Context): ComponentName? = null
}

internal class FakeNetworkMonitorContract(
    override val isAvailableInBuild: Boolean = BuildConfig.SUPPORT_NETWORK_MONITOR,
) : NetworkMonitorContract

internal fun permissionRegistry(
    launcherAvailable: Boolean = BuildConfig.SUPPORT_LAUNCHER,
    networkMonitorAvailable: Boolean = BuildConfig.SUPPORT_NETWORK_MONITOR,
): PermissionRegistryRepositoryImpl = PermissionRegistryRepositoryImpl(
    FakeLauncherModeContract(launcherAvailable),
    FakeNetworkMonitorContract(networkMonitorAvailable),
)
