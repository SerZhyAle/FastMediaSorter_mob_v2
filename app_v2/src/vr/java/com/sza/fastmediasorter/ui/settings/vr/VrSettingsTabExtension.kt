package com.sza.fastmediasorter.ui.settings.vr

import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.xr.XrEnvironment
import com.sza.fastmediasorter.core.xr.XrEnvironmentDetector
import com.sza.fastmediasorter.ui.settings.SettingsTabExtension
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds the 5th "VR" Settings tab on flavors that ship the real XR contracts (vr / noLegal).
 * Visibility is gated by [XrEnvironmentDetector] - the tab appears only on XR-capable
 * devices (Quest / Android XR) regardless of the user's master-toggle state.
 */
@Singleton
class VrSettingsTabExtension @Inject constructor(
    private val detector: XrEnvironmentDetector,
) : SettingsTabExtension {
    override val order: Int = 100  // After the static 0..3 tabs.
    override val tabTitleResId: Int = R.string.settings_tab_vr
    override val isVisible: Boolean
        get() = detector.detect() != XrEnvironment.NONE

    override fun createFragment(): Fragment = VrSettingsFragment()
}
