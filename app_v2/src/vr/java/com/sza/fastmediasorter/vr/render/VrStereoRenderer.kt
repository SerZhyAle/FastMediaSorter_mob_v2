package com.sza.fastmediasorter.vr.render

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.sza.fastmediasorter.domain.model.StereoMode
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Per-eye stereo renderer for VR.
 *
 * Rendering strategy:
 * - SBS (full/half): left eye = left half of frame, right eye = right half
 * - OU (over-under): left eye = top half, right eye = bottom half
 * - MONO / 2D content: cinema mode — single frame fills a virtual screen
 *
 * Uses a GLSL UV-crop shader sampling from an OES external texture
 * (provided by [VrVideoSurfaceTextureBridge]).
 *
 * StereoMode is injected from StereoDetectionFacade via VrPlayerActivity —
 * the renderer does NOT detect stereo layout itself.
 *
 * Lifecycle: [initGl] on GL thread after EGL context is current →
 * [renderEye] per frame per eye → [release] on GL thread.
 */
class VrStereoRenderer {

    private var currentStereoMode: StereoMode = StereoMode.MONO

    // GL shader program and attribute/uniform locations — valid after initGl()
    private var shaderProgram: Int = 0
    private var aPositionLoc: Int = -1
    private var aTexCoordLoc: Int = -1
    private var uTextureLoc: Int = -1
    private var uUvOffsetLoc: Int = -1
    private var uUvScaleLoc: Int = -1

    private var quadVbo: Int = 0
    private var isGlInitialized = false

    private val planner = VrRenderPlanner()

    /**
     * Set the stereo mode for the current media.
     * Called by VrPlayerActivity when StereoDetectionFacade returns a result.
     */
    fun setStereoMode(mode: StereoMode) {
        Timber.d("VrStereoRenderer: stereo mode set to $mode")
        currentStereoMode = mode
    }

    fun getCurrentStereoMode(): StereoMode = currentStereoMode

    /**
     * Initialise GL resources: compile shaders, link program, create VBO.
     * Must be called on the GL thread with a valid EGL context.
     */
    fun initGl() {
        if (isGlInitialized) return

        // Vertex shader: pass-through position + UV
        val vertexShaderSrc = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            uniform vec2 uUvOffset;
            uniform vec2 uUvScale;
            void main() {
                gl_Position = aPosition;
                // Apply UV offset and scale for per-eye crop
                vTexCoord = uUvOffset + aTexCoord * uUvScale;
            }
        """.trimIndent()

        // Fragment shader: sample from OES external texture with UV-crop
        val fragmentShaderSrc = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSrc)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSrc)

        if (vertexShader == 0 || fragmentShader == 0) {
            Timber.e("VrStereoRenderer: shader compilation failed, VR rendering disabled")
            return
        }

        shaderProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(shaderProgram, vertexShader)
        GLES20.glAttachShader(shaderProgram, fragmentShader)
        GLES20.glLinkProgram(shaderProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(shaderProgram)
            Timber.e("VrStereoRenderer: program link failed: $log")
            GLES20.glDeleteProgram(shaderProgram)
            shaderProgram = 0
            return
        }

        // Shaders attached to program — safe to delete shader objects
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        // Resolve attribute and uniform locations
        aPositionLoc = GLES20.glGetAttribLocation(shaderProgram, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(shaderProgram, "aTexCoord")
        uTextureLoc = GLES20.glGetUniformLocation(shaderProgram, "uTexture")
        uUvOffsetLoc = GLES20.glGetUniformLocation(shaderProgram, "uUvOffset")
        uUvScaleLoc = GLES20.glGetUniformLocation(shaderProgram, "uUvScale")

        // Create fullscreen quad VBO (position XY + texcoord UV interleaved)
        // NDC: (-1,-1) bottom-left → (1,1) top-right
        // UV:  (0,0) top-left → (1,1) bottom-right (SurfaceTexture convention)
        val quadVertices = floatArrayOf(
            // X,    Y,    U,   V
            -1f, -1f,  0f, 1f,  // bottom-left
             1f, -1f,  1f, 1f,  // bottom-right
            -1f,  1f,  0f, 0f,  // top-left
             1f,  1f,  1f, 0f   // top-right
        )
        val vertexBuffer: FloatBuffer = ByteBuffer
            .allocateDirect(quadVertices.size * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
        vertexBuffer.position(0)

        val vboIds = IntArray(1)
        GLES20.glGenBuffers(1, vboIds, 0)
        quadVbo = vboIds[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            quadVertices.size * FLOAT_BYTES,
            vertexBuffer,
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        isGlInitialized = true
        Timber.d("VrStereoRenderer: GL initialized, program=$shaderProgram")
    }

    /**
     * Render the current frame into the already-bound XR render target.
     *
     * Projection, equirect, and cylinder paths intentionally share the same flat-texture
     * blit because the OpenXR compositor is responsible for the final spatial warp.
     * The renderer only decides which per-eye UV crop and viewport rectangle to use.
     */
    fun renderEye(
        context: VrRenderContext,
        oesTextureId: Int,
        descriptor: VrLayerDescriptor,
    ) {
        val plan = planner.buildRenderPlan(context, descriptor)
        renderQuad(
            oesTextureId = oesTextureId,
            uOffset = plan.uv.uOffset,
            vOffset = plan.uv.vOffset,
            uScale = plan.uv.uScale,
            vScale = plan.uv.vScale,
            viewport = plan.viewport,
            targetWidthPx = context.targetWidthPx,
            targetHeightPx = context.targetHeightPx,
            swapchainImageIndex = context.swapchainImageIndex,
        )
    }

    /**
     * Delegates to [VrRenderPlanner.buildRenderPlan].
     * Exposed for call sites that already hold a VrStereoRenderer reference.
     */
    fun buildRenderPlan(context: VrRenderContext, descriptor: VrLayerDescriptor): VrRenderPlanner.RenderPlan =
        planner.buildRenderPlan(context, descriptor)

    /** Delegates to [VrRenderPlanner.calculateUvParams]. */
    fun calculateUvParams(eye: VrEye, descriptor: VrLayerDescriptor): VrRenderPlanner.UvParams =
        planner.calculateUvParams(eye, descriptor)

    /** Delegates to [VrRenderPlanner.calculateCinemaViewport]. */
    fun calculateCinemaViewport(
        targetWidthPx: Int,
        targetHeightPx: Int,
        sourceAspectRatio: Float,
    ): VrRenderPlanner.Viewport = planner.calculateCinemaViewport(targetWidthPx, targetHeightPx, sourceAspectRatio)

    /**
     * Draw a textured quad with the specified UV crop region.
     *
     * @param oesTextureId OES external texture containing the video frame.
     * @param uOffset UV X offset (0.0 = left edge)
     * @param vOffset UV Y offset (0.0 = top edge)
     * @param uScale UV X scale (0.5 = half width for SBS)
     * @param vScale UV Y scale (0.5 = half height for OU)
     * @param viewport Target viewport inside the already-bound XR framebuffer.
     * @param targetWidthPx Full render-target width used when clearing letterbox bars.
     * @param targetHeightPx Full render-target height used when clearing letterbox bars.
     * @param swapchainImageIndex Target swapchain image (framebuffer binding is
     *        handled by OpenXrSessionManager — here we just draw).
     */
    private fun renderQuad(
        oesTextureId: Int,
        uOffset: Float,
        vOffset: Float,
        uScale: Float,
        vScale: Float,
        viewport: VrRenderPlanner.Viewport,
        targetWidthPx: Int,
        targetHeightPx: Int,
        @Suppress("UNUSED_PARAMETER") swapchainImageIndex: Int
    ) {
        if (!isGlInitialized || shaderProgram == 0) {
            Timber.v("VrStereoRenderer: GL not ready, skipping renderQuad")
            return
        }

        // TODO: Bind framebuffer for swapchainImageIndex here when OpenXR native layer is wired.
        // OpenXrSessionManager.bindSwapchainFramebuffer(swapchainImageIndex) would go here.

        val needsLetterboxClear = viewport.x != 0 || viewport.y != 0 ||
            viewport.width != targetWidthPx || viewport.height != targetHeightPx
        if (needsLetterboxClear) {
            GLES20.glViewport(0, 0, targetWidthPx, targetHeightPx)
            // XR swapchain images are reused, so cinema bars must be cleared explicitly.
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        GLES20.glViewport(viewport.x, viewport.y, viewport.width, viewport.height)
        GLES20.glUseProgram(shaderProgram)

        // Bind OES texture to unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glUniform1i(uTextureLoc, 0)

        // Set UV offset and scale for the target eye
        GLES20.glUniform2f(uUvOffsetLoc, uOffset, vOffset)
        GLES20.glUniform2f(uUvScaleLoc, uScale, vScale)

        // Bind quad VBO and set vertex attributes
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)

        // Position attribute: 2 floats at offset 0, stride 4 floats
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, STRIDE, 0)

        // TexCoord attribute: 2 floats at offset 2, stride 4 floats
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, STRIDE, 2 * FLOAT_BYTES)

        // Draw the quad as a triangle strip (4 vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Cleanup
        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glUseProgram(0)
    }

    /** Release GL resources. Must be called on the GL thread. */
    fun release() {
        Timber.d("VrStereoRenderer: releasing GL resources")
        if (shaderProgram != 0) {
            GLES20.glDeleteProgram(shaderProgram)
            shaderProgram = 0
        }
        if (quadVbo != 0) {
            GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
            quadVbo = 0
        }
        isGlInitialized = false
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Timber.e("VrStereoRenderer: glCreateShader failed for type=$type")
            return 0
        }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Timber.e("VrStereoRenderer: shader compile error: $log")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {
        private const val FLOAT_BYTES = 4
        // Stride = 4 floats per vertex (2 position + 2 texcoord) × 4 bytes
        private const val STRIDE = 4 * FLOAT_BYTES
    }
}
