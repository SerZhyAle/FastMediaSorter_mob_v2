package com.sza.fastmediasorter.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.ui.launcher.LauncherHomeActivity
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherOpenAllAppsRequest
import timber.log.Timber

/** S0404: launcher-mode capability for flavors that ship the home surface. */
class LauncherModeContractImpl : LauncherModeContract {

    override val isAvailableInBuild: Boolean = true

    override fun homeComponent(context: Context): ComponentName =
        ComponentName(context, LauncherHomeActivity::class.java)

    override fun openAllApps(context: Context): Boolean {
        // Callers include the gesture dispatcher, which runs in a Service with no task of its own.
        val intent = LauncherOpenAllAppsRequest.intent(context)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "LauncherModeContractImpl: failed to open All apps") }
            .isSuccess
    }
}
