package com.sza.fastmediasorter.vr.render

import com.sza.fastmediasorter.domain.model.StereoMode
import timber.log.Timber
import javax.inject.Inject

class DefaultVrLayerFactory @Inject constructor() : VrLayerFactory {

    override fun describe(stereo: StereoMode, renderingMode: VrRenderingMode): VrLayerDescriptor {
        return when {
            stereo == StereoMode.MONO && renderingMode == VrRenderingMode.CINEMA -> quadCinemaDescriptor()
            // OU/SBS are inherently stereoscopic — route to projection regardless of renderingMode.
            // S0078: SBS_FULL/SBS_HALF fell through to QUAD_CINEMA when renderingMode == CINEMA (same bug as OU before its fix).
            stereo == StereoMode.OU -> projectionDescriptor(stereo)
            stereo in PROJECTION_STEREO_MODES -> projectionDescriptor(stereo)
            stereo in EQUIRECT_360_MODES -> equirectDescriptor(stereo, isHalfSphere = false)
            stereo in EQUIRECT_180_MODES -> equirectDescriptor(stereo, isHalfSphere = true)
            stereo == StereoMode.CYLINDER_180 -> cylinderDescriptor()
            else -> {
                // Unsupported combinations stay visible instead of producing a blank XR frame.
                Timber.w(
                    "DefaultVrLayerFactory: unsupported stereo=%s renderMode=%s, falling back to cinema quad",
                    stereo,
                    renderingMode,
                )
                quadCinemaDescriptor()
            }
        }
    }

    private fun projectionDescriptor(stereo: StereoMode): VrLayerDescriptor = VrLayerDescriptor(
        type = VrLayerType.PROJECTION,
        leftEyeUv = leftEyeUv(stereo),
        rightEyeUv = rightEyeUv(stereo),
    )

    private fun quadCinemaDescriptor(): VrLayerDescriptor = VrLayerDescriptor(
        type = VrLayerType.QUAD_CINEMA,
        widthMeters = VrLayerDescriptor.DEFAULT_WIDTH_METERS,
        heightMeters = VrLayerDescriptor.DEFAULT_HEIGHT_METERS,
        distanceMeters = VrLayerDescriptor.DEFAULT_DISTANCE_METERS,
    )

    private fun equirectDescriptor(stereo: StereoMode, isHalfSphere: Boolean): VrLayerDescriptor {
        val centralAngle = if (isHalfSphere) {
            VrLayerDescriptor.HALF_DOME_RADIANS
        } else {
            VrLayerDescriptor.FULL_SPHERE_RADIANS
        }
        return VrLayerDescriptor(
            type = VrLayerType.EQUIRECT_2,
            leftEyeUv = leftEyeUv(stereo),
            rightEyeUv = rightEyeUv(stereo),
            radiusMeters = VrLayerDescriptor.DEFAULT_RADIUS_METERS,
            centralHorizontalAngleRadians = centralAngle,
            upperVerticalAngleRadians = VrLayerDescriptor.HALF_SPHERE_RADIANS,
            lowerVerticalAngleRadians = -VrLayerDescriptor.HALF_SPHERE_RADIANS,
        )
    }

    private fun cylinderDescriptor(): VrLayerDescriptor = VrLayerDescriptor(
        type = VrLayerType.CYLINDER,
        radiusMeters = 3f,
        centralHorizontalAngleRadians = VrLayerDescriptor.HALF_DOME_RADIANS,
        widthMeters = 4f,
        heightMeters = 2.25f,
        distanceMeters = 3f,
    )

    private fun leftEyeUv(stereo: StereoMode): VrUvRect = when (stereo) {
        StereoMode.SBS_FULL,
        StereoMode.SBS_HALF,
        StereoMode.EQUIRECT_360_SBS,
        StereoMode.EQUIRECT_180_SBS,
        StereoMode.VR180_FISHEYE_SBS,
        -> VrUvRect(0f, 0f, 0.5f, 1f)

        StereoMode.OU,
        StereoMode.EQUIRECT_360_OU,
        -> VrUvRect(0f, 0f, 1f, 0.5f)

        else -> VrLayerDescriptor.FULL_FRAME_UV
    }

    private fun rightEyeUv(stereo: StereoMode): VrUvRect = when (stereo) {
        StereoMode.SBS_FULL,
        StereoMode.SBS_HALF,
        StereoMode.EQUIRECT_360_SBS,
        StereoMode.EQUIRECT_180_SBS,
        StereoMode.VR180_FISHEYE_SBS,
        -> VrUvRect(0.5f, 0f, 0.5f, 1f)

        StereoMode.OU,
        StereoMode.EQUIRECT_360_OU,
        -> VrUvRect(0f, 0.5f, 1f, 0.5f)

        else -> VrLayerDescriptor.FULL_FRAME_UV
    }

    private companion object {
        val PROJECTION_STEREO_MODES = setOf(
            StereoMode.SBS_FULL,
            StereoMode.SBS_HALF,
            // OU handled by its own mode-independent branch in describe()
        )
        val EQUIRECT_360_MODES = setOf(
            StereoMode.EQUIRECT_360_MONO,
            StereoMode.EQUIRECT_360_SBS,
            StereoMode.EQUIRECT_360_OU,
        )
        val EQUIRECT_180_MODES = setOf(
            StereoMode.EQUIRECT_180_MONO,
            StereoMode.EQUIRECT_180_SBS,
            StereoMode.VR180_FISHEYE_SBS,
        )
    }
}
