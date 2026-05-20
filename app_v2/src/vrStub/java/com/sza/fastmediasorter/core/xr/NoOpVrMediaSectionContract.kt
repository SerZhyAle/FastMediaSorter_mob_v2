package com.sza.fastmediasorter.core.xr

import androidx.fragment.app.Fragment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op contract used by phone-only flavors (`standard`, `lite`, `photos`, `legacy`).
 *
 * The Media settings layout always declares the VR section (header + container) so that
 * inflation is identical across flavors; this implementation tells `MediaSettingsFragment`
 * to leave them hidden.
 */
@Singleton
class NoOpVrMediaSectionContract @Inject constructor() : VrMediaSectionContract {

    override val isAvailable: Boolean = false

    override fun createFragment(): Fragment? = null
}
