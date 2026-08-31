package com.sza.fastmediasorter.launcher

import android.content.ComponentName
import android.content.Context
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract

/** S0404: no-op launcher-mode capability for flavors without the home surface. */
class LauncherModeContractImpl : LauncherModeContract {

    override val isAvailableInBuild: Boolean = false

    override fun homeComponent(context: Context): ComponentName? = null

    override fun openAllApps(context: Context): Boolean = false
}
