package com.sza.fastmediasorter.core.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture

/**
 * S3270: the single place a media3 [MediaController] is torn down in this process.
 *
 * `MediaSessionImpl.dispatchOnPlayerInfoChanged` checks `isConnected` at the top of each loop turn,
 * hands the callback to the controller, and only then writes per-controller state back through
 * `ConnectedControllersManager.updateLastSentTimelineAndTracks`, whose `checkNotNull` reads the
 * record that same turn. Session and controller share this process and this looper, so the callback
 * reaches a [androidx.media3.common.Player.Listener] synchronously; a release taken from inside it
 * removes the record between the check and the write, and the library throws a fatal NPE on the main
 * thread. Upstream androidx/media #3375, a 1.11.0 regression still open, whose own recommendation is
 * exactly this deferral.
 *
 * Every teardown therefore goes out as a looper message: the library's loop finishes first, and the
 * caller no longer has to know whether it is standing inside a callback.
 */
object MediaControllerRelease {

    /** Releases [controller] after the current looper message, if it is not already null. */
    fun release(controller: MediaController?) {
        if (controller == null) return
        post(controller.applicationLooper) { controller.release() }
    }

    /**
     * Releases [future] after the current looper message.
     *
     * @param looper the owning controller's `applicationLooper` when one is already resolved; the
     *   main looper is used otherwise, which is where a controller future is built and completed.
     */
    fun releaseFuture(future: ListenableFuture<MediaController>?, looper: Looper? = null) {
        if (future == null) return
        post(looper ?: Looper.getMainLooper()) { MediaController.releaseFuture(future) }
    }

    private fun post(looper: Looper, block: () -> Unit) {
        Handler(looper).post(block)
    }
}
