package com.sza.fastmediasorter.core.xr

import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.ui.settings.vr.VrSettingsBlockFragment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real VR media-section contract for `vr` / `noLegal` flavors.
 *
 * Per S0249 / ui-clarify 2026-05-19, the VR controls block is visible on EVERY
 * vr/noLegal build, even on non-XR devices. The `VrSettingsBlockFragment` itself observes
 * [XrDetectionFacade] and shows the advisory / disables the master toggle when no XR runtime
 * is present.
 */
@Singleton
class VrMediaSectionContractImpl @Inject constructor() : VrMediaSectionContract {

    override val isAvailable: Boolean = true

    override fun createFragment(): Fragment = VrSettingsBlockFragment()
}
