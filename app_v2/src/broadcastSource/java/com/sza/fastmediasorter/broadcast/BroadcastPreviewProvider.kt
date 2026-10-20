package com.sza.fastmediasorter.broadcast

import android.content.Context
import android.os.Handler
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import com.pedro.library.view.GlStreamInterface
import com.pedro.library.view.preview.MultiPreviewConfig
import com.pedro.rtspserver.RtspServerCamera2
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the preview on the control screen to the camera of the running [VideoBroadcastService].
 *
 * The session streams through its offscreen renderer for its whole life, and the preview is a copy
 * surface added to that renderer (`GlStreamInterface.addMultiPreviewSurface`): adding and removing the
 * copy never touches the camera, so a display transition gaps nothing (S4347). The previous design
 * bound a view-owned renderer to the session, and the library answers that rebinding by closing and
 * reopening the camera - the measured 0.75..1.25 s freeze at every display sleep and wake.
 *
 * Video mute is state of the renderer, and every session builds a new one, so the mute flag is kept
 * here and re-applied when a session attaches.
 */
@Singleton
class BroadcastPreviewProvider @Inject constructor(
    @ApplicationContext appContext: Context,
) : BroadcastPreviewBinder {

    private val mainHandler = Handler(appContext.mainLooper)

    @Volatile
    private var camera: RtspServerCamera2? = null

    @Volatile
    private var videoMuted = false

    // S3157: the scope is @Singleton because this bridges the service and the control screen, two owners
    // with different lifetimes, so the preview is held weakly. detach() still clears it; the container it
    // was added to is the strong owner while the screen lives.
    private var previewView: WeakReference<SurfaceView>? = null

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = Unit

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            addPreviewCopy()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            removePreviewCopies()
        }
    }

    override fun attach(container: ViewGroup) {
        // The control screen attaches on every live state it renders; a view already in this container
        // keeps its surface callbacks and rebinds itself, so adding a second one is churn (S4281).
        if (previewView?.get()?.parent === container) return
        detach()
        val view = SurfaceView(container.context)
        view.holder.addCallback(surfaceCallback)
        container.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        previewView = WeakReference(view)
    }

    override fun detach() {
        val view = previewView?.get() ?: return
        view.holder.removeCallback(surfaceCallback)
        removePreviewCopies()
        (view.parent as? ViewGroup)?.removeView(view)
        previewView = null
    }

    /** Called from the service's IO scope; the surface side of the bind must run on the main thread. */
    fun attachCamera(liveCamera: RtspServerCamera2) {
        camera = liveCamera
        applyMute()
        mainHandler.post { addPreviewCopy() }
    }

    fun detachCamera() {
        camera = null
        videoMuted = false
    }

    fun setVideoMuted(muted: Boolean) {
        videoMuted = muted
        applyMute()
    }

    /**
     * The add/remove pair never touches the camera, so the copy can be refused, lost to a display
     * transition or re-added at a wake with nothing but a log line - the broadcast must outlive any
     * preview failure, so it stays headless (S4347).
     */
    @Suppress("TooGenericExceptionCaught")
    private fun addPreviewCopy() {
        val renderer = offscreenRenderer() ?: return
        val view = previewView?.get()?.takeIf { it.holder.surface.isValid } ?: return
        val frame = view.holder.surfaceFrame
        // A copy scales the encoded image; rotating the control screen does not rotate a running stream.
        val size = renderer.encoderSize
        val portrait = size.x < size.y
        try {
            Timber.d("S4347: addMultiPreviewSurface portrait=$portrait")
            renderer.addMultiPreviewSurface(
                view.holder.surface,
                MultiPreviewConfig(width = frame.width(), height = frame.height(), isPortrait = portrait),
            )
        } catch (e: Throwable) {
            Timber.w(e, "Broadcast preview: copy surface refused, the stream stays headless")
        }
        applyMute()
    }

    private fun removePreviewCopies() {
        offscreenRenderer()?.removeAllMultiPreviewSurfaces()
    }

    /** Null while no session runs; a session whose renderer is not the offscreen one is named once. */
    private fun offscreenRenderer(): GlStreamInterface? {
        val liveCamera = camera ?: return null
        return liveCamera.glInterface as? GlStreamInterface ?: run {
            Timber.w("Broadcast preview: renderer is not the offscreen one, the stream stays headless")
            null
        }
    }

    private fun applyMute() {
        val liveCamera = camera ?: return
        val renderer = liveCamera.glInterface
        if (videoMuted) {
            renderer.muteVideo()
        } else {
            renderer.unMuteVideo()
        }
    }
}
