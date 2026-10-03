package com.sza.fastmediasorter.wear.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.sza.fastmediasorter.wear.complication.WearFacePhotoEncoder
import com.sza.fastmediasorter.wear.domain.model.WearBackground
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
 *
 * S3557: the branded animation takes the paired phone's wallpaper palette and controls, so the watch
 * backdrop repeats the phone's launcher wallpaper (strategic ADR-5).
 */
@Composable
fun WearAppBackground(
    background: WearBackground,
    running: Boolean,
    modifier: Modifier = Modifier
) {
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
                val clockStyle = rememberWearClockStyle()
                WaveParticleBackground(
                    modifier = Modifier.fillMaxSize(),
                    running = running,
                    intent = AnimationIntent.DECORATIVE,
                    palette = clockStyle.palette,
                    tuning = clockStyle.backdropTuning(),
                    paletteSeed = clockStyle.sentAt
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(opposing.copy(alpha = WearWallpaperScrimPolicy.FLOOR_ALPHA))
                )
            }

            is WearBackground.BrandedStill -> {
                val clockStyle = rememberWearClockStyle()
                WaveParticleBackground(
                    modifier = Modifier.fillMaxSize(),
                    running = false,
                    intent = AnimationIntent.DECORATIVE,
                    palette = clockStyle.palette,
                    tuning = clockStyle.backdropTuning(),
                    paletteSeed = clockStyle.sentAt
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
 * same path costs nothing.
 *
 * Returns the frame's measured luminance beside drawing it: S2864 sizes the scrim over the photo from
 * this one measurement, so it is taken where the decode already happens and nowhere else.
 *
 * S3988: the decode and the per-pixel luminance pass run off the main thread, not in the composition body - a
 * `remember` block runs on the main thread during composition, on every screen that draws the
 * wallpaper. While the frame loads, and when it fails to decode, nothing is drawn and the opposing
 * fill of the enclosing box shows, which is the "black is what shows before a background is ready"
 * case, not a third background - and the answer is null, which the scrim policy reads as the floor.
 */
@Composable
private fun DeliveredFrame(image: WearBackground.Image): Float? {
    val frame by produceState<Pair<ImageBitmap, Float>?>(null, image.file.path, image.lastModified) {
        value = withContext(Dispatchers.Default) { decodeFrame(image.file) }
    }
    val loaded = frame
    if (loaded != null) {
        Image(
            bitmap = loaded.first,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
    return loaded?.second
}

/**
 * Sampled down to the face edge: the frame covers a screen no larger than
 * [WearFacePhotoEncoder.FACE_EDGE_PX], so a full-resolution decode is heap spent on pixels never shown.
 */
private fun decodeFrame(file: File): Pair<ImageBitmap, Float>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val bitmap = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        val options = BitmapFactory.Options().apply {
            inSampleSize = WearFacePhotoEncoder.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        BitmapFactory.decodeFile(file.path, options)
    } else {
        null
    }
    return bitmap?.let { it.asImageBitmap() to WearWallpaperScrimPolicy.averageLuminance(it) }
}
