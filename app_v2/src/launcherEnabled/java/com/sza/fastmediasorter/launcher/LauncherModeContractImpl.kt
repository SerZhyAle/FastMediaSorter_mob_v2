package com.sza.fastmediasorter.launcher

import android.content.ComponentName
import android.content.Context
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.ui.launcher.LauncherHomeActivity

/** S0404: launcher-mode capability for flavors that ship the home surface. */
class LauncherModeContractImpl : LauncherModeContract {

    override val isAvailableInBuild: Boolean = true

    override fun homeComponent(context: Context): ComponentName =
        ComponentName(context, LauncherHomeActivity::class.java)
}
