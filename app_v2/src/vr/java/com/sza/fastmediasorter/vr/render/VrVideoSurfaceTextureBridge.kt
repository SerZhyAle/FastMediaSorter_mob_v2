package com.sza.fastmediasorter.vr.render

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import timber.log.Timber

/**
 * Bridge between ExoPlayer video output and OpenGL texture for VR rendering.
 *
 * Creates an OES external texture + SurfaceTexture + Surface chain:
 *   ExoPlayer.setVideoSurface(bridge.surface)
 *     → video frames arrive at SurfaceTexture
 *     → updateTexImage() makes the frame available as an OES texture
 *     → VrStereoRenderer samples the texture with per-eye UV-crop shader
 *
 * Lifecycle:
 *   VrPlayerActivity.onCreate  → bridge = VrVideoSurfaceTextureBridge()
 *   VrPlayerActivity.onResume  → bridge.initialize() (requires GL context)
 *   per-frame                  → bridge.updateFrame()
 *   VrPlayerActivity.onDestroy → bridge.release()
 *
 * Thread safety: initialize() and release() must be called on the GL thread.
 * updateFrame() must be called from the render loop (GL thread).
 * [surface] can be passed to ExoPlayer from the main thread after initialize().
 */
class VrVideoSurfaceTextureBridge {

    /** OES external texture ID — valid after [initialize], 0 after [release]. */
    var textureId: Int = 0
        private set

    /** Surface backed by the SurfaceTexture — pass to ExoPlayer.setVideoSurface(). */
    var surface: Surface? = null
        private set

    private var surfaceTexture: SurfaceTexture? = null
    private var isInitialized = false

    /**
     * Create the OES texture, SurfaceTexture, and Surface.
     * Must be called on the GL thread (EGL context must be current).
     */
    fun initialize() {
        if (isInitialized) {
            Timber.w("VrVideoSurfaceTextureBridge: already initialized, skipping")
            return
        }

        // Generate an OES external texture for SurfaceTexture video frames.
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        // Configure the OES texture — SurfaceTexture manages filtering internally,
        // but we set reasonable defaults for the rare case of manual sampling.
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        // SurfaceTexture wraps the OES texture — incoming video frames are delivered here.
        surfaceTexture = SurfaceTexture(textureId).also { st ->
            // Default buffer size will be set by ExoPlayer based on video resolution.
            st.setOnFrameAvailableListener { /* no-op: we poll via updateFrame() in render loop */ }
        }

        // Surface wraps SurfaceTexture — passed to ExoPlayer.setVideoSurface().
        surface = Surface(surfaceTexture)

        isInitialized = true
        Timber.d("VrVideoSurfaceTextureBridge: initialized, textureId=$textureId")
    }

    /**
     * Pull the latest video frame into the OES texture.
     * Must be called once per render loop iteration, on the GL thread,
     * before VrStereoRenderer samples the texture.
     */
    fun updateFrame() {
        surfaceTexture?.updateTexImage()
    }

    /**
     * Release all resources. Must be called on the GL thread.
     * After release, [surface] and [textureId] are no longer valid.
     */
    fun release() {
        if (!isInitialized) return

        surface?.release()
        surface = null

        surfaceTexture?.release()
        surfaceTexture = null

        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }

        isInitialized = false
        Timber.d("VrVideoSurfaceTextureBridge: released")
    }

    /** True if [initialize] has been called and [release] has not. */
    fun isReady(): Boolean = isInitialized && textureId != 0
}
