package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import androidx.media3.ui.PlayerView
import timber.log.Timber
import java.util.WeakHashMap

/**
 * Pure-visual quarter-turn rotation of the video picture, applied to the view that holds the decoded
 * frame instead of to the Media3 effect chain.
 *
 * The effect-chain rotation was never seen working on a device, and the repository already records
 * that geometric effects do not reach a `texture_view` surface (`PanelStereoCropApplier`). Rotating the
 * frame container does not depend on the pipeline at all, and it is the same idiom
 * `ImageLoadingManager` uses for still images, so image and video rotate by one mechanism.
 *
 * The container is the parent of `PlayerView.videoSurfaceView`: the controller, the buffering spinner
 * and the other overlays are siblings of it, so only the picture turns. After a quarter turn the
 * container's footprint is swapped, so a uniform scale fits it back into the `PlayerView` slot without
 * crop or stretch. The scale depends on both the slot and the frame, so it is recomputed on every
 * layout of the `PlayerView`.
 */
object VideoSurfaceRotationApplier {

    private const val HALF_TURN_DEGREES = 180
    private const val FULL_TURN_DEGREES = 360
    private const val CENTER_FRACTION = 0.5f

    // The value is a plain Int on purpose: a value that referenced the view tree would keep the weakly
    // held key reachable and the entry would never be collected.
    private val angles: MutableMap<PlayerView, Int> = WeakHashMap()

    private val refitOnLayout = View.OnLayoutChangeListener { host, _, _, _, _, _, _, _, _ ->
        (host as? PlayerView)?.let { fit(it, angles[it] ?: 0) }
    }

    /**
     * Rotate the picture shown by [playerView] clockwise by [angleDegrees] and keep it fitted through
     * later layouts. An angle of zero on a view that was never rotated changes nothing.
     */
    fun apply(playerView: PlayerView?, angleDegrees: Int) {
        val host = playerView ?: return
        val angle = ((angleDegrees % FULL_TURN_DEGREES) + FULL_TURN_DEGREES) % FULL_TURN_DEGREES
        if (angle == 0 && !angles.containsKey(host)) return
        angles[host] = angle
        // addOnLayoutChangeListener ignores a listener that is already registered, so repeating this is safe.
        host.addOnLayoutChangeListener(refitOnLayout)
        fit(host, angle)
    }

    /**
     * Counterpart of [apply] for a [PlayerView] that is being discarded or re-inflated: stops the refit
     * and returns its picture to upright, so a view that outlives the rotation does not keep a stale turn.
     */
    fun release(playerView: PlayerView?) {
        val host = playerView ?: return
        host.removeOnLayoutChangeListener(refitOnLayout)
        angles.remove(host)
        (host.videoSurfaceView?.parent as? View)?.let { frame ->
            frame.rotation = 0f
            frame.scaleX = 1f
            frame.scaleY = 1f
        }
    }

    private fun fit(host: PlayerView, angle: Int) {
        val frame = host.videoSurfaceView?.parent as? View ?: return
        val hostWidth = host.width.toFloat()
        val hostHeight = host.height.toFloat()
        val frameWidth = frame.width.toFloat()
        val frameHeight = frame.height.toFloat()
        // Before the first layout there is nothing to fit; the layout listener calls back with real sizes.
        if (minOf(hostWidth, hostHeight, frameWidth, frameHeight) <= 0f) return
        val quarterTurn = angle % HALF_TURN_DEGREES != 0
        val fitScale = if (quarterTurn) {
            val picture = pictureSize(host, frameWidth, frameHeight)
            minOf(hostWidth / picture.second, hostHeight / picture.first)
        } else {
            1f
        }
        frame.pivotX = frameWidth * CENTER_FRACTION
        frame.pivotY = frameHeight * CENTER_FRACTION
        frame.rotation = angle.toFloat()
        frame.scaleX = fitScale
        frame.scaleY = fitScale
        Timber.d("S4682: fit a=$angle host=${hostWidth}x$hostHeight s=$fitScale")
    }

    /**
     * Width and height of the picture as drawn inside the frame container.
     *
     * The container is not always the picture: the internal player lets Media3 shrink it to the video's
     * aspect ratio, but the standalone host keeps it as large as the whole `PlayerView` while the picture
     * is letterboxed inside it. Fitting the container then shrinks the turned picture to a fraction of the
     * slot (measured 0.657 in portrait and 0.195 in landscape on the S21+). The picture rectangle is
     * therefore derived from the player's own video size, centred in the container like the fit resize
     * mode every host layout declares; an unknown video size falls back to the container itself.
     */
    private fun pictureSize(host: PlayerView, frameWidth: Float, frameHeight: Float): Pair<Float, Float> {
        val aspect = videoAspect(host) ?: return frameWidth to frameHeight
        return if (frameWidth / frameHeight > aspect) {
            frameHeight * aspect to frameHeight
        } else {
            frameWidth to frameWidth / aspect
        }
    }

    /** Displayed width over height of the current video, or null while the player has no video size yet. */
    private fun videoAspect(host: PlayerView): Float? {
        val videoSize = host.player?.videoSize
        if (videoSize == null || videoSize.width <= 0 || videoSize.height <= 0) return null
        val displayWidth = videoSize.width * videoSize.pixelWidthHeightRatio
        val displayHeight = videoSize.height.toFloat()
        val quarterUnapplied = videoSize.unappliedRotationDegrees % HALF_TURN_DEGREES != 0
        return if (quarterUnapplied) displayHeight / displayWidth else displayWidth / displayHeight
    }
}
