package com.sza.fastmediasorter.wear.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.sza.fastmediasorter.wear.domain.model.WearBackground
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import timber.log.Timber

/**
 * S2000: the one layer drawn behind every screen of the watch app.
 *
 * Takes the resolved answer rather than the stored mode, so the fallback from a missing frame is
 * decided once in ResolveWearBackgroundUseCase instead of here.
 *
 * S2864: the scrim stays the one contrast mechanism, but over a delivered photo its amount follows
 * the frame's own measured luminance - [WearWallpaperScrimPolicy]. The fixed amount this file drew
 * before could not carry S2000's contrast guarantee across an arbitrary photo, and the white frame
 * that washed the home captions out proved it; branded wallpapers keep the tuned floor, because
 * their brightness is fixed at build time (S2544, S2729).
 */
@Composable
fun WearAppBackground(
    background: WearBackground,
    running: Boolean,
    modifier: Modifier = Modifier
) {
    Timber.d("S2544: dimmer wallpaper applied bg=%s running=%b", background, running)
    Timber.d("S2729: second-pass dimmer applied bg=%s running=%b", background, running)
    // S2522: under a light scheme the content is dark, so the veil that has to sit between it and an
    // arbitrary photo is the light one. Only the side flips - S2864 moved the amount into the scrim
    // policy, which sizes it off the photo's own luminance.
    val opposing = if (WearAppTheme.colors.isLight) Color.White else Color.Black
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(opposing)
    ) {
        when (background) {
            is WearBackground.BrandedAnimation -> {
                WaveParticleBackground(
                    modifier = Modifier.fillMaxSize(),
                    running = running,
                    intent = AnimationIntent.DECORATIVE
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(opposing.copy(alpha = WearWallpaperScrimPolicy.FLOOR_ALPHA))
                )
            }

            is WearBackground.BrandedStill -> {
                WaveParticleBackground(
                    modifier = Modifier.fillMaxSize(),
                    running = false,
                    intent = AnimationIntent.DECORATIVE
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(opposing.copy(alpha = WearWallpaperScrimPolicy.FLOOR_ALPHA))
                )
            }

            is WearBackground.Image -> {
                val measured = DeliveredFrame(image = background)
                val scrimAlpha =
                    WearWallpaperScrimPolicy.alphaFor(measured, isLightScrim = WearAppTheme.colors.isLight)
                Timber.d("S2864: photo scrim measured=%s alpha=%s", measured, scrimAlpha)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(opposing.copy(alpha = scrimAlpha))
                )
            }

            is WearBackground.None -> {
                // Plain screen background: the outer Box is already filled with the opposing side.
            }
        }
    }
}

/**
 * Keyed on the path so a redelivery under the reserved name is picked up while a recomposition on the
 * same path costs nothing - decoding per frame would put a file read on every draw.
 *
 * Returns the frame's measured luminance beside drawing it: S2864 sizes the scrim over the photo from
 * this one measurement, so it is taken where the decode already happens and nowhere else.
 *
 * A frame that fails to decode draws nothing and leaves the opposing fill of the enclosing box
 * showing, which is the "black is what shows before a background is ready" case, not a third
 * background - and answers null, which the scrim policy reads as the floor.
 */
@Composable
private fun DeliveredFrame(image: WearBackground.Image): Float? {
    val frame: Pair<ImageBitmap, Float>? = remember(image.file.path, image.lastModified) {
        val bitmap = BitmapFactory.decodeFile(image.file.path)
        Timber.d("S2541: DeliveredFrame path=%s stamp=%d ok=%b", image.file.path, image.lastModified, bitmap != null)
        bitmap?.let { it.asImageBitmap() to WearWallpaperScrimPolicy.averageLuminance(it) }
    }
    if (frame != null) {
        Image(
            bitmap = frame.first,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
    return frame?.second
}
