package com.sza.fastmediasorter.ui.player.entry

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import javax.inject.Inject

/**
 * Routes the player entry decision based on flavor, device class,
 * detected stereo mode and media type.
 *
 * In standard flavor: shows VR install CTA on 3D content.
 * In vr flavor: opens VrPlayerActivity on headset, fallback on phone.
 */
interface PlayerEntryCoordinator {
    fun resolveEntry(request: PlaybackEntryRequest): PlaybackEntryDecision
}

data class PlaybackEntryRequest(
    /** historically true only on the vr flavor; after S0241 always false on every flavor */
    val flavorSupportsVr: Boolean,
    val currentDeviceClass: DeviceClass,
    val detectedStereoMode: StereoMode,
    val mediaType: MediaType
)

enum class DeviceClass {
    PHONE, TABLET, HEADSET
}

sealed class PlaybackEntryDecision {
    /** Open the standard 2D player (no VR involved). */
    data object OpenStandardPlayer : PlaybackEntryDecision()
    /** Show CTA to install the VR edition (standard flavor + 3D content). */
    data object ShowVrInstallCta : PlaybackEntryDecision()
    /** Open VR player (vr flavor + headset available). */
    data object OpenVrPlayer : PlaybackEntryDecision()
    /** Show phone fallback screen (vr flavor + no headset). */
    data object ShowPhoneFallbackScreen : PlaybackEntryDecision()
}

class PlayerEntryCoordinatorImpl @Inject constructor() : PlayerEntryCoordinator {

    override fun resolveEntry(request: PlaybackEntryRequest): PlaybackEntryDecision {
        // VR flavor path
        if (request.flavorSupportsVr) {
            return if (request.currentDeviceClass == DeviceClass.HEADSET) {
                PlaybackEntryDecision.OpenVrPlayer
            } else {
                PlaybackEntryDecision.ShowPhoneFallbackScreen
            }
        }

        // Standard flavor path: show VR install CTA for any content that benefits
        // from the VR edition — flat stereo (SBS/OU) plus spherical (360°/VR180/Cylinder).
        val needsVr = request.detectedStereoMode.isStereoscopic() ||
                request.detectedStereoMode.isSpherical()
        if (needsVr && (request.mediaType == MediaType.VIDEO || request.mediaType == MediaType.IMAGE)) {
            return PlaybackEntryDecision.ShowVrInstallCta
        }

        return PlaybackEntryDecision.OpenStandardPlayer
    }
}
