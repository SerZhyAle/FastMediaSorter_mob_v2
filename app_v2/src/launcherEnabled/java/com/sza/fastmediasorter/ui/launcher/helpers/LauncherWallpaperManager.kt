package com.sza.fastmediasorter.ui.launcher.helpers

import android.graphics.drawable.Animatable
import android.view.View
import android.widget.ImageView
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.core.util.AnimationIntent
import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.domain.model.launcher.LauncherWallpaper
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.player.helpers.AudioWaveParticleView
import com.sza.fastmediasorter.utils.collectOnLifecycle
import timber.log.Timber
import java.io.File

/**
 * S1101: owns the desktop wallpaper layer - which of the three backdrops is on screen and whether it is
 * animating. Keeps the activity thin (Rule 3): the activity only forwards its foreground edges.
 *
 * Exactly one layer is live at a time. Switching modes stops the layer being left behind rather than
 * merely hiding it, because every backdrop costs frames while it runs: a hidden-but-running animation, or
 * an unbound-but-still-open camera, would keep waking the device for frames nobody sees.
 *
 * Crop-to-fill is a plain `centerCrop` on the image layer rather than a call into the photo-frame scale
 * chooser: that chooser deliberately falls back to fit-center when image and screen orientations differ,
 * which is right for viewing a photo and wrong for a wallpaper, where the owner asked for overflow to be
 * cropped in every case.
 *
 * S2076: the camera backdrop is the one mode that raises two views - the preview and the scrim above it,
 * which keeps icon labels legible over an arbitrary live scene.
 */
/**
 * S2730: the three user-tunable numbers of the branded backdrop, carried together.
 *
 * One value rather than three flows because the manager sets all three on the same view and a change to
 * any of them re-seeds the same frame - three collectors would re-seed it three times for one edit.
 */
data class LauncherWallpaperTuning(
    val intensity: Float,
    val animationSpeed: Float,
    val particleDensity: Float,
)

class LauncherWallpaperManager(
    private val lifecycleOwner: LifecycleOwner,
    private val imageLayer: ImageView,
    private val wavesLayer: AudioWaveParticleView,
    private val cameraLayer: PreviewView,
    private val cameraScrim: View,
    private val viewModel: LauncherHomeViewModel,
) {

    private val cameraBackground = LauncherCameraBackgroundManager(lifecycleOwner, cameraLayer)

    private var current: LauncherWallpaper = LauncherWallpaper.None

    // S3335: raised while the backdrop shows a still of the last camera frame instead of a live preview.
    // Every start path consults it, so only the resume edge may put the camera back up.
    private var cameraFrozen = false

    init {
        // S2536: the desktop backdrop is ornament, whatever the class it is drawn by. The same view
        // is the audio visualizer elsewhere and stays AMBIENT there - this is the declaration that
        // lets the animation switch reach the wallpaper without freezing the visualizer.
        wavesLayer.intent = AnimationIntent.DECORATIVE
    }

    // Fires on the settings collector's thread; the camera and views are driven from the view's thread.
    private val policyListener: () -> Unit = { cameraLayer.post { refreshWallpaperPolicy() } }

    /**
     * S2536 / S2661 / S3276: wallpaper animation is paused/resumed when animation policy changes.
     */
    private fun startCameraIfPolicyAllows(cameraId: String) {
        // S3335: the frozen still stands until the resume edge lowers the flag itself. Returning to the
        // desktop runs onStart - and the wallpaper flow's STARTED re-emission - while the screen the user
        // is leaving still holds the camera, so binding here would take it back from a surface on screen.
        if (cameraFrozen) return
        val mayAnimate = AnimationPolicy.mayAnimate(AnimationIntent.DECORATIVE)
        if (mayAnimate) {
            stopWaves()
            cameraLayer.isVisible = true
            cameraScrim.isVisible = true
            cameraBackground.start(cameraId)
        } else {
            stopCamera()
            // S3335: a freeze that ends under a policy forbidding animation hands the desktop to the
            // waves, so the still it left behind has to go with the preview.
            clearImage()
            wavesLayer.isVisible = true
            wavesLayer.startAnimation()
        }
    }

    private fun refreshWallpaperPolicy() {
        val mayAnimate = AnimationPolicy.mayAnimate(AnimationIntent.DECORATIVE)
        when (val wallpaper = current) {
            is LauncherWallpaper.Branded -> {
                if (mayAnimate) wavesLayer.startAnimation() else wavesLayer.pauseAnimation()
            }
            is LauncherWallpaper.StaticStripes -> {
                if (mayAnimate) wavesLayer.startAnimation() else wavesLayer.renderFreshStaticFrame()
            }
            is LauncherWallpaper.Image -> {
                if (mayAnimate) imageAnimatable()?.start() else imageAnimatable()?.stop()
            }
            is LauncherWallpaper.InstantPhoto -> {
                if (mayAnimate) imageAnimatable()?.start() else imageAnimatable()?.stop()
            }
            is LauncherWallpaper.LiveCamera -> startCameraIfPolicyAllows(wallpaper.cameraId)
            is LauncherWallpaper.None -> Unit
        }
    }

    fun attach() {
        lifecycleOwner.collectOnLifecycle(viewModel.wallpaper) { wallpaper ->
            current = wallpaper
            render(wallpaper)
        }
        lifecycleOwner.collectOnLifecycle(viewModel.wallpaperTuning) { tuning ->
            wavesLayer.backdropIntensity = tuning.intensity
            wavesLayer.animationSpeedScale = tuning.animationSpeed
            wavesLayer.particleDensityScale = tuning.particleDensity
        }
        lifecycleOwner.collectOnLifecycle(viewModel.animationPalette) { paletteKey ->
            wavesLayer.palette = AudioWaveParticleView.AnimationColorPalette.fromKeyOrDefault(paletteKey)
            if (current is LauncherWallpaper.Branded && wavesLayer.isVisible) {
                wavesLayer.stopAndReset()
                wavesLayer.startAnimation()
            } else if (current is LauncherWallpaper.StaticStripes && wavesLayer.isVisible) {
                wavesLayer.renderFreshStaticFrame()
            }
        }
        // S3335: the still is dropped by the first live frame of the new session, not by the call that
        // asked for it - a bind that fails or takes a second would otherwise leave the desktop blank.
        cameraLayer.previewStreamState.observe(lifecycleOwner) { state ->
            if (state == PreviewView.StreamState.STREAMING && current is LauncherWallpaper.LiveCamera) {
                clearImage()
            }
        }
    }

    /**
     * S3335: foreground edge for the camera backdrop alone - the lens is taken only once the desktop is
     * the surface the user is actually on.
     */
    fun onResume() {
        cameraFrozen = false
        val wallpaper = current
        if (wallpaper is LauncherWallpaper.LiveCamera) startCameraIfPolicyAllows(wallpaper.cameraId)
    }

    /**
     * S3335: releases the lens one lifecycle edge earlier than [onStop], freezing the last frame.
     *
     * Android runs the launcher's `onStop` after the opened screen's `onResume`, so a camera held until
     * then is still bound while the capture screen, the mirror or the torch asks for it.
     */
    fun onPause() {
        if (current !is LauncherWallpaper.LiveCamera || cameraFrozen) return
        cameraFrozen = true
        // A null bitmap means no frame ever reached the preview; there is nothing to freeze, and the
        // camera still has to go.
        cameraLayer.bitmap?.let { lastFrame ->
            imageLayer.setImageBitmap(lastFrame)
            imageLayer.isVisible = true
            // The still has to cover the preview it replaces, and the scrim has to stay above both, or
            // icon labels lose the dimming they had a frame earlier. The preview itself stays visible:
            // a hidden PreviewView has no surface, so it could never report the live frame that ends
            // the freeze.
            imageLayer.bringToFront()
            cameraScrim.bringToFront()
        }
        cameraBackground.stop()
    }

    /** Foreground edge: resume whichever backdrop is active per policy. Symmetric with [onStop]. */
    fun onStart() {
        AnimationPolicy.addLevelListener(policyListener)
        refreshWallpaperPolicy()
    }

    /** Background edge: no frames while the desktop is not on screen. Symmetric with [onStart]. */
    fun onStop() {
        AnimationPolicy.removeLevelListener(policyListener)
        wavesLayer.pauseAnimation()
        imageAnimatable()?.stop()
        cameraBackground.stop()
    }

    private fun render(wallpaper: LauncherWallpaper) {
        when (wallpaper) {
            is LauncherWallpaper.None -> {
                clearImage()
                stopWaves()
                stopCamera()
            }

            is LauncherWallpaper.Branded -> {
                clearImage()
                stopCamera()
                wavesLayer.isVisible = true
                wavesLayer.startAnimation()
            }

            is LauncherWallpaper.StaticStripes -> {
                clearImage()
                stopCamera()
                wavesLayer.isVisible = true
                wavesLayer.renderFreshStaticFrame()
            }

            is LauncherWallpaper.Image -> {
                stopWaves()
                stopCamera()
                imageLayer.isVisible = true
                // Glide decodes stills and GIFs off the same call and sizes the bitmap to the view, so a
                // large wallpaper never lands in memory at full resolution.
                val file = File(wallpaper.absolutePath)
                Glide.with(imageLayer)
                    .load(file)
                    .signature(ObjectKey(file.lastModified()))
                    .into(imageLayer)
            }

            is LauncherWallpaper.InstantPhoto -> {
                stopWaves()
                stopCamera()
                val path = wallpaper.imagePath
                // S2210: no disk probe here. render() runs on the main thread, and both the existence
                // stat and lastModified() tripped StrictMode on device. A non-null path is already proof
                // the capture landed, and the frame's mtime rides along as the cache key.
                if (path != null) {
                    imageLayer.isVisible = true
                    Glide.with(imageLayer)
                        .load(File(path))
                        .signature(ObjectKey(wallpaper.capturedAtMillis))
                        .into(imageLayer)
                } else {
                    clearImage()
                    wavesLayer.isVisible = true
                    wavesLayer.startAnimation()
                }
            }

            is LauncherWallpaper.LiveCamera -> {
                clearImage()
                startCameraIfPolicyAllows(wallpaper.cameraId)
            }
        }
    }

    private fun clearImage() {
        imageAnimatable()?.stop()
        Glide.with(imageLayer).clear(imageLayer)
        imageLayer.setImageDrawable(null)
        imageLayer.isVisible = false
    }

    private fun stopWaves() {
        wavesLayer.stopAndReset()
        wavesLayer.isVisible = false
    }

    private fun stopCamera() {
        // S3335: a backdrop that is no longer the live camera cannot stay frozen, or the next switch back
        // to it would be refused by a flag nothing lowers.
        cameraFrozen = false
        cameraBackground.stop()
        cameraLayer.isVisible = false
        cameraScrim.isVisible = false
    }

    private fun imageAnimatable(): Animatable? = imageLayer.drawable as? Animatable
}
