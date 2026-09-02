package com.sza.fastmediasorter.ui.cameracapture.helpers

import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.cameracapture.model.PhotoProfile
import timber.log.Timber

/**
 * S1262: the single owner of which photo profile is active.
 *
 * Profiles are exclusive by construction - every application clears the previous one first, so two
 * recipes can never be half-applied at once. The manager holds no view or camera references; it
 * drives the existing session primitives through [Actions], which is what makes the whole state
 * machine testable with fakes.
 *
 * S1658 revokes S1262's ADR-2, which read "a profile never owns the camera over an explicit user
 * action" and dropped the profile on every lens switch. A lens switch is not a cancellation of the
 * profile: it is a move to the other lens's own set, which the host restores through [restore]. An
 * explicit profile choice made after such a switch still outranks the restored one and becomes that
 * lens's new remembered value. Moving into video mode still resets, because profiles are photo-only.
 */
class CameraProfileApplyManager(private val actions: Actions) {

    /**
     * The session primitives a recipe is built from. Deliberately narrow - the manager decides
     * *which* primitives a profile uses and in what order, not how any of them work.
     */
    interface Actions {
        fun setNightMode(enabled: Boolean)
        fun setBokeh(enabled: Boolean)
        fun setSport(enabled: Boolean)
        fun setMacro(enabled: Boolean)
        fun setTorch(enabled: Boolean)
        fun switchToFrontLens()
        fun switchToMainBackLens()
        fun resetZoomTo1x()
    }

    var activeProfile: PhotoProfile = PhotoProfile.NORMAL
        private set

    private var cachedSupportedProfiles: List<PhotoProfile>? = null

    /** The menu's contents. Callers must render from this rather than re-deriving availability. */
    fun availableProfiles(capabilities: CameraRuntimeCapabilities): List<PhotoProfile> {
        val current = PhotoProfile.available(capabilities)
        val cached = cachedSupportedProfiles
        if (cached == null || (!capabilities.isFront && current.size >= cached.size)) {
            cachedSupportedProfiles = current
            return current
        }
        return cached
    }

    /**
     * Apply [profile], or reset when it is already active - re-tapping the current entry is how a
     * user turns a profile off, so the menu needs no separate "none" row.
     */
    fun apply(profile: PhotoProfile) {
        if (profile == activeProfile) {
            resetToNormal("re-selected the active profile")
            return
        }
        clearActiveProfile()
        activeProfile = profile
        Timber.d("CameraProfileApplyManager: applying %s", profile)
        when (profile) {
            PhotoProfile.NORMAL -> Unit
            PhotoProfile.DOCUMENT -> {
                actions.switchToMainBackLens()
                actions.resetZoomTo1x()
                actions.setTorch(true)
            }
            PhotoProfile.NIGHT -> actions.setNightMode(true)
            PhotoProfile.PORTRAIT -> actions.setBokeh(true)
            PhotoProfile.SELFIE -> actions.switchToFrontLens()
            PhotoProfile.MACRO -> actions.setMacro(true)
            PhotoProfile.SPORT -> actions.setSport(true)
        }
    }

    /**
     * S1658: marks [profile] active because the entered lens remembered it, without replaying its
     * recipe. The session has already written the matching intents (see `restorePerLensState`), so
     * driving the actions here would rebind a second time to reach the state the bind is about to
     * produce.
     */
    fun restore(profile: PhotoProfile) {
        if (profile == activeProfile) return
        Timber.d("CameraProfileApplyManager: restoring %s for the entered lens", profile)
        activeProfile = profile
    }

    /** Drop back to NORMAL. [reason] is logged, because most resets are triggered indirectly. */
    fun resetToNormal(reason: String) {
        if (activeProfile == PhotoProfile.NORMAL) return
        Timber.d("CameraProfileApplyManager: %s left for NORMAL - %s", activeProfile, reason)
        clearActiveProfile()
        activeProfile = PhotoProfile.NORMAL
    }

    /**
     * Re-check the active profile against a fresh capability snapshot after a rebind. A lens that
     * cannot honour it drops the profile rather than leaving a menu entry ticked that does nothing.
     */
    fun reconcile(capabilities: CameraRuntimeCapabilities) {
        if (activeProfile.isAvailable(capabilities)) return
        releaseWithoutClearing("not available on the bound lens")
    }

    /**
     * Drop the profile without replaying its clear sweep.
     *
     * Used where the session has already reset its own intents - a rebind onto another lens and a
     * move into video mode both do. Replaying the sweep there would rebind the camera a second time
     * from inside the callback that reported the first rebind, and fight the action the user just
     * took rather than undo anything.
     */
    fun releaseWithoutClearing(reason: String) {
        if (activeProfile == PhotoProfile.NORMAL) return
        Timber.d("CameraProfileApplyManager: %s released - %s", activeProfile, reason)
        activeProfile = PhotoProfile.NORMAL
    }

    /**
     * Clear whatever the active profile turned on.
     *
     * Only SELFIE returns the lens here. MACRO also moves the lens on devices with dedicated
     * close-focus optics, but its own primitive restores the pre-macro lens (S1189) - switching
     * again from here would fight that restore rather than help it.
     */
    private fun clearActiveProfile() {
        actions.setNightMode(false)
        actions.setBokeh(false)
        actions.setSport(false)
        actions.setMacro(false)
        if (activeProfile == PhotoProfile.DOCUMENT) actions.setTorch(false)
        if (activeProfile == PhotoProfile.SELFIE) actions.switchToMainBackLens()
    }
}
