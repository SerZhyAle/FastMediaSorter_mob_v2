package com.sza.fastmediasorter.ui.launcher.helpers

import android.graphics.drawable.Animatable
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.domain.model.launcher.LauncherWallpaper
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.player.helpers.AudioWaveParticleView
import com.sza.fastmediasorter.utils.collectOnLifecycle
import java.io.File

/**
 * S1101: owns the desktop wallpaper layer - which of the two backdrops is on screen and whether it is
 * animating. Keeps the activity thin (Rule 3): the activity only forwards its foreground edges.
 *
 * Exactly one layer is live at a time. Switching modes stops the layer being left behind rather than
 * merely hiding it, because both backdrops animate: a hidden-but-running animation would keep waking
 * the device for frames nobody sees.
 *
 * Crop-to-fill is a plain `centerCrop` on the image layer rather than a call into the photo-frame scale
 * chooser: that chooser deliberately falls back to fit-center when image and screen orientations differ,
 * which is right for viewing a photo and wrong for a wallpaper, where the owner asked for overflow to be
 * cropped in every case.
 */
class LauncherWallpaperManager(
    private val lifecycleOwner: LifecycleOwner,
    private val imageLayer: ImageView,
    private val wavesLayer: AudioWaveParticleView,
    private val viewModel: LauncherHomeViewModel,
) {

    private var current: LauncherWallpaper = LauncherWallpaper.None

    fun attach() {
        lifecycleOwner.collectOnLifecycle(viewModel.wallpaper) { wallpaper ->
            current = wallpaper
            render(wallpaper)
        }
    }

    /** Foreground edge: resume whichever backdrop is active. Symmetric with [onStop]. */
    fun onStart() {
        when (current) {
            is LauncherWallpaper.Branded -> wavesLayer.startAnimation()
            is LauncherWallpaper.StaticStripes -> wavesLayer.renderFreshStaticFrame()
            is LauncherWallpaper.Image -> imageAnimatable()?.start()
            is LauncherWallpaper.None -> Unit
        }
    }

    /** Background edge: no frames while the desktop is not on screen. Symmetric with [onStart]. */
    fun onStop() {
        wavesLayer.pauseAnimation()
        imageAnimatable()?.stop()
    }

    private fun render(wallpaper: LauncherWallpaper) {
        when (wallpaper) {
            is LauncherWallpaper.None -> {
                clearImage()
                stopWaves()
            }

            is LauncherWallpaper.Branded -> {
                clearImage()
                wavesLayer.isVisible = true
                wavesLayer.startAnimation()
            }

            is LauncherWallpaper.StaticStripes -> {
                clearImage()
                wavesLayer.isVisible = true
                wavesLayer.renderFreshStaticFrame()
            }

            is LauncherWallpaper.Image -> {
                stopWaves()
                imageLayer.isVisible = true
                // Glide decodes stills and GIFs off the same call and sizes the bitmap to the view, so a
                // large wallpaper never lands in memory at full resolution.
                Glide.with(imageLayer)
                    .load(File(wallpaper.absolutePath))
                    .into(imageLayer)
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

    private fun imageAnimatable(): Animatable? = imageLayer.drawable as? Animatable
}
