package com.sza.fastmediasorter.ui.launcher.helpers

import android.graphics.drawable.Animatable
import android.view.View
import android.widget.ImageView
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.domain.model.launcher.LauncherWallpaper
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.player.helpers.AudioWaveParticleView
import com.sza.fastmediasorter.utils.collectOnLifecycle
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

    fun attach() {
        lifecycleOwner.collectOnLifecycle(viewModel.wallpaper) { wallpaper ->
            current = wallpaper
            render(wallpaper)
        }
    }

    /** Foreground edge: resume whichever backdrop is active. Symmetric with [onStop]. */
    fun onStart() {
        when (val wallpaper = current) {
            is LauncherWallpaper.Branded -> wavesLayer.startAnimation()
            is LauncherWallpaper.StaticStripes -> wavesLayer.renderFreshStaticFrame()
            is LauncherWallpaper.Image -> imageAnimatable()?.start()
            is LauncherWallpaper.InstantPhoto -> render(wallpaper)
            is LauncherWallpaper.LiveCamera -> cameraBackground.start(wallpaper.cameraId)
            is LauncherWallpaper.None -> Unit
        }
    }

    /** Background edge: no frames while the desktop is not on screen. Symmetric with [onStart]. */
    fun onStop() {
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
                stopWaves()
                cameraLayer.isVisible = true
                cameraScrim.isVisible = true
                cameraBackground.start(wallpaper.cameraId)
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
        cameraBackground.stop()
        cameraLayer.isVisible = false
        cameraScrim.isVisible = false
    }

    private fun imageAnimatable(): Animatable? = imageLayer.drawable as? Animatable
}
