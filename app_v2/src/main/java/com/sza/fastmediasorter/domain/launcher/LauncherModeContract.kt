package com.sza.fastmediasorter.domain.launcher

import android.content.ComponentName
import android.content.Context

/**
 * S0404: capability seam for launcher mode (the app acting as the device home screen).
 * Flavors that ship the home surface bind the real implementation from src/launcherEnabled;
 * the rest bind a no-op from src/launcherDisabled, so src/main never guards on BuildConfig.
 */
interface LauncherModeContract {

    /** True when this build compiles the launcher-mode surface (standard / noLegal). */
    val isAvailableInBuild: Boolean

    /** ComponentName of the HOME-filter activity, or null when unavailable in this build. */
    fun homeComponent(context: Context): ComponentName?
}
