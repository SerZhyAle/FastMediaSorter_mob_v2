package com.sza.fastmediasorter.vr.render

import com.sza.fastmediasorter.domain.model.StereoMode
import org.junit.Assert.assertEquals
import org.junit.Test

class VrStereoRendererTest {

    private val renderer = VrRenderPlanner()

    @Test
    fun `buildRenderPlan keeps projection eye crop and fullscreen viewport`() {
        val descriptor = VrLayerDescriptor(
            type = VrLayerType.PROJECTION,
            leftEyeUv = VrUvRect(0f, 0f, 0.5f, 1f),
            rightEyeUv = VrUvRect(0.5f, 0f, 0.5f, 1f),
        )

        val plan = renderer.buildRenderPlan(
            context = buildContext(
                layerType = VrLayerType.PROJECTION,
                stereoMode = StereoMode.SBS_FULL,
                eye = VrEye.RIGHT,
                targetWidthPx = 2048,
                targetHeightPx = 2048,
            ),
            descriptor = descriptor,
        )

        assertEquals(VrRenderPlanner.UvParams(0.5f, 0f, 0.5f, 1f), plan.uv)
        assertEquals(VrRenderPlanner.Viewport(0, 0, 2048, 2048), plan.viewport)
    }

    @Test
    fun `buildRenderPlan keeps spherical stereo flat and fullscreen`() {
        val descriptor = VrLayerDescriptor(
            type = VrLayerType.EQUIRECT_2,
            leftEyeUv = VrUvRect(0f, 0f, 1f, 0.5f),
            rightEyeUv = VrUvRect(0f, 0.5f, 1f, 0.5f),
        )

        val plan = renderer.buildRenderPlan(
            context = buildContext(
                layerType = VrLayerType.EQUIRECT_2,
                stereoMode = StereoMode.EQUIRECT_360_OU,
                eye = VrEye.LEFT,
                targetWidthPx = 4096,
                targetHeightPx = 2048,
            ),
            descriptor = descriptor,
        )

        assertEquals(VrRenderPlanner.UvParams(0f, 0f, 1f, 0.5f), plan.uv)
        assertEquals(VrRenderPlanner.Viewport(0, 0, 4096, 2048), plan.viewport)
    }

    @Test
    fun `calculateCinemaViewport letterboxes landscape source inside square target`() {
        val viewport = renderer.calculateCinemaViewport(
            targetWidthPx = 2000,
            targetHeightPx = 2000,
            sourceAspectRatio = 16f / 9f,
        )

        assertEquals(VrRenderPlanner.Viewport(0, 437, 2000, 1125), viewport)
    }

    @Test
    fun `buildRenderPlan keeps cylinder mono fullscreen`() {
        val descriptor = VrLayerDescriptor(type = VrLayerType.CYLINDER)

        val plan = renderer.buildRenderPlan(
            context = buildContext(
                layerType = VrLayerType.CYLINDER,
                stereoMode = StereoMode.CYLINDER_180,
                eye = VrEye.LEFT,
                targetWidthPx = 3072,
                targetHeightPx = 1536,
            ),
            descriptor = descriptor,
        )

        assertEquals(VrRenderPlanner.UvParams(0f, 0f, 1f, 1f), plan.uv)
        assertEquals(VrRenderPlanner.Viewport(0, 0, 3072, 1536), plan.viewport)
    }

    private fun buildContext(
        layerType: VrLayerType,
        stereoMode: StereoMode,
        eye: VrEye,
        targetWidthPx: Int,
        targetHeightPx: Int,
        sourceAspectRatio: Float = VrRenderContext.DEFAULT_SOURCE_ASPECT_RATIO,
    ) = VrRenderContext(
        layerType = layerType,
        stereoMode = stereoMode,
        eye = eye,
        swapchainImageIndex = 7,
        renderingMode = VrRenderingMode.FULL_STEREO,
        targetWidthPx = targetWidthPx,
        targetHeightPx = targetHeightPx,
        sourceAspectRatio = sourceAspectRatio,
    )
}
