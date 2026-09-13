package com.sza.fastmediasorter.broadcast

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.ViewGroup
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.RtspServerCamera2
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the preview on the control screen to the camera of the running [VideoBroadcastService].
 *
 * `OpenGlView` owns no `SurfaceTexture` until its surface exists, and `replaceView` reads it at once, so
 * the view reaches the camera from `surfaceChanged` and goes back to the headless renderer
 * (`replaceView(Context)`) when the surface is destroyed. Handing it over from `onCreate` crashed the
 * control screen while the service kept streaming (S3058 run 1).
 *
 * Video mute is state of the renderer, and every `replaceView` builds a new renderer, so the mute flag is
 * kept here and re-applied after each swap.
 */
@Singleton
class BroadcastPreviewProvider @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) : BroadcastPreviewBinder {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var camera: RtspServerCamera2? = null

    @Volatile
    private var videoMuted = false

    private var previewView: OpenGlView? = null
    private var boundView: OpenGlView? = null

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = Unit

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            bindView()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            unbindView()
        }
    }

    override fun attach(container: ViewGroup) {
        detach()
        val view = OpenGlView(container.context)
        view.holder.addCallback(surfaceCallback)
        container.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        previewView = view
    }

    override fun detach() {
        val view = previewView ?: return
        view.holder.removeCallback(surfaceCallback)
        unbindView()
        (view.parent as? ViewGroup)?.removeView(view)
        previewView = null
    }

    /** Called from the service's IO scope; the view side of the swap must run on the main thread. */
    fun attachCamera(liveCamera: RtspServerCamera2) {
        camera = liveCamera
        applyMute(liveCamera)
        mainHandler.post { bindView() }
    }

    fun detachCamera() {
        camera = null
        videoMuted = false
        mainHandler.post { boundView = null }
    }

    fun setVideoMuted(muted: Boolean) {
        videoMuted = muted
        camera?.let(::applyMute)
    }

    // Kotlin's null intrinsic inside the library throws NullPointerException when the renderer is not
    // initialised yet; the broadcast must outlive a preview that could not attach, so it stays headless.
    @Suppress("TooGenericExceptionCaught")
    private fun bindView() {
        val liveCamera = camera ?: return
        val view = previewView?.takeIf { it !== boundView && it.holder.surface.isValid } ?: return
        try {
            liveCamera.replaceView(view)
            boundView = view
        } catch (e: Throwable) {
            Timber.w(e, "Broadcast preview: renderer not ready, the stream stays headless")
            try {
                liveCamera.replaceView(appContext)
            } catch (t: Throwable) {
                Timber.w(t, "Broadcast preview fallback to headless failed")
            }
        }
        applyMute(liveCamera)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun unbindView() {
        val liveCamera = camera
        if (boundView != null && liveCamera != null) {
            try {
                liveCamera.replaceView(appContext)
                applyMute(liveCamera)
            } catch (e: Throwable) {
                Timber.w(e, "Broadcast preview unbind failed")
            }
        }
        boundView = null
    }

    private fun applyMute(liveCamera: RtspServerCamera2) {
        val renderer = liveCamera.glInterface
        if (videoMuted) {
            renderer.muteVideo()
        } else {
            renderer.unMuteVideo()
        }
    }
}
