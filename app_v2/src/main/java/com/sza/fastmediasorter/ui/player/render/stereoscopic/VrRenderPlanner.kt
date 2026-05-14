package com.sza.fastmediasorter.ui.player.render.stereoscopic

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure-Kotlin UV/viewport computation for VR stereo rendering.
 *
 * Extracted from VrStereoRenderer so that render-plan logic can be unit-tested
 * without a GL context or the vr flavor source set.
 */
class VrRenderPlanner {

    /**
     * Resolve the exact UV crop + viewport to use for the current XR layer type.
     */
    fun buildRenderPlan(context: VrRenderContext, descriptor: VrLayerDescriptor): RenderPlan {
        val uv = when (context.layerType) {
            VrLayerType.PROJECTION,
            VrLayerType.EQUIRECT_2,
            VrLayerType.CYLINDER,
            VrLayerType.QUAD_CINEMA,
            -> calculateUvParams(context.eye, descriptor)
        }

        val viewport = when (context.layerType) {
            VrLayerType.QUAD_CINEMA -> calculateCinemaViewport(
                targetWidthPx = context.targetWidthPx,
                targetHeightPx = context.targetHeightPx,
                sourceAspectRatio = context.sourceAspectRatio,
            )
            VrLayerType.PROJECTION,
            VrLayerType.EQUIRECT_2,
            VrLayerType.CYLINDER,
            -> Viewport(0, 0, context.targetWidthPx, context.targetHeightPx)
        }

        return RenderPlan(uv = uv, viewport = viewport)
    }

    /**
     * Calculate UV parameters for a given eye from the layer descriptor.
     */
    fun calculateUvParams(eye: VrEye, descriptor: VrLayerDescriptor): UvParams {
        val rect = if (eye == VrEye.LEFT) descriptor.leftEyeUv else descriptor.rightEyeUv
        return UvParams(
            uOffset = rect.uOffset,
            vOffset = rect.vOffset,
            uScale = rect.uScale,
            vScale = rect.vScale,
        )
    }

    /**
     * Cinema mode: letterbox/pillarbox flat content to match source aspect ratio
     * inside the XR quad render target.
     */
    fun calculateCinemaViewport(
        targetWidthPx: Int,
        targetHeightPx: Int,
        sourceAspectRatio: Float,
    ): Viewport {
        if (targetWidthPx <= 0 || targetHeightPx <= 0) {
            return Viewport(0, 0, targetWidthPx.coerceAtLeast(0), targetHeightPx.coerceAtLeast(0))
        }

        val safeSourceAspect = sourceAspectRatio
            .takeIf { it.isFinite() && it > 0f }
            ?: VrRenderContext.DEFAULT_SOURCE_ASPECT_RATIO
        val targetAspect = targetWidthPx.toFloat() / targetHeightPx.toFloat()

        if (abs(safeSourceAspect - targetAspect) < 0.01f) {
            return Viewport(0, 0, targetWidthPx, targetHeightPx)
        }

        return if (safeSourceAspect > targetAspect) {
            // Landscape source in square/portrait target — letterbox (black bars top/bottom)
            val viewportHeight = (targetWidthPx / safeSourceAspect)
                .roundToInt()
                .coerceIn(1, targetHeightPx)
            val viewportY = ((targetHeightPx - viewportHeight) / 2).coerceAtLeast(0)
            Viewport(0, viewportY, targetWidthPx, viewportHeight)
        } else {
            // Portrait source in landscape target — pillarbox (black bars left/right)
            val viewportWidth = (targetHeightPx * safeSourceAspect)
                .roundToInt()
                .coerceIn(1, targetWidthPx)
            val viewportX = ((targetWidthPx - viewportWidth) / 2).coerceAtLeast(0)
            Viewport(viewportX, 0, viewportWidth, targetHeightPx)
        }
    }

    /** UV crop parameters for a single eye. */
    data class UvParams(
        val uOffset: Float,
        val vOffset: Float,
        val uScale: Float,
        val vScale: Float,
    )

    /** Viewport rectangle inside the currently bound XR framebuffer. */
    data class Viewport(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    data class RenderPlan(
        val uv: UvParams,
        val viewport: Viewport,
    )
}