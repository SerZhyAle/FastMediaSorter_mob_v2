package com.sza.fastmediasorter.core.xr

import androidx.fragment.app.Fragment

/**
 * Flavor-isolated contract that decides whether the Media settings screen should expose a
 * VR controls section, and how to construct its content fragment.
 *
 * Implementations:
 * - `src/vrStub/java/.../core/xr/NoOpVrMediaSectionContract` - phone-only flavors
 *   (`standard`, `lite`, `photos`, `legacy`); always returns `false` / `null`. The Media
 *   layout still ships the section header and container, but `MediaSettingsFragment`
 *   hides them.
 * - `src/vr/java/.../core/xr/VrMediaSectionContractImpl` - VR-capable flavors
 *   (`vr`, `noLegal`); always returns `true` (block is unconditionally visible) and
 *   provides the `VrSettingsBlockFragment`. Per S0249 / ui-clarify 2026-05-19, the block
 *   stays visible even on non-XR devices, with the master toggle disabled and an advisory
 *   notice rendered above it.
 *
 * Keeping this contract in `src/main/` lets `MediaSettingsFragment` call it without seeing
 * any VR-specific types, complying with CLAUDE.md Rule 15.
 */
interface VrMediaSectionContract {

    /** Whether the VR controls section is part of the current build at all. */
    val isAvailable: Boolean

    /** Builds the fragment that renders the VR controls block. Returns null when [isAvailable] is false. */
    fun createFragment(): Fragment?
}
