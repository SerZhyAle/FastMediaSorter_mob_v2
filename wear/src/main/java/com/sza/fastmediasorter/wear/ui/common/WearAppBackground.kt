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
 * Constant by design (S2000, strategic 3.3.9): the scrim always opposes the content, so contrast is a
 * guarantee of the app rather than a property of the owner's photo - a scrim that varied with the
 * chosen picture would make it the latter. Matched to the value already tuned for arbitrary album art
 * on the audio player, which is the same worst case: a bright, uncontrolled image under the content.
 *
 * S2522 made the DIRECTION follow the scheme while leaving the amount fixed. Before it the file could
 * say "the watch draws light content" as a fact; a light scheme removes that, so the fill and the
 * scrim now take whichever side keeps the content readable.
 */
private const val SCRIM_ALPHA = 0.30f

/**
 * S2000: the one layer drawn behind every screen of the watch app.
 *
 * Takes the resolved answer rather than the stored mode, so the fallback from a missing frame is
 * decided once in ResolveWearBackgroundUseCase instead of here.
 */
@Composable
fun WearAppBackground(
    background: WearBackground,
    running: Boolean,
    modifier: Modifier = Modifier
) {
    Timber.d("S2544: dimmer wallpaper applied bg=%s running=%b", background, running)
    // S2522: under a light scheme the content is dark, so the veil that has to sit between it and an
    // arbitrary photo is the light one. Only the side flips - the amount stays the constant above.
    val opposing = if (WearAppTheme.colors.isLight) Color.White else Color.Black
    Timber.d("S2522: background layer light=%b", WearAppTheme.colors.isLight)
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
                        .background(opposing.copy(alpha = SCRIM_ALPHA))
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
                        .background(opposing.copy(alpha = SCRIM_ALPHA))
                )
            }

            is WearBackground.Image -> {
                DeliveredFrame(image = background)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(opposing.copy(alpha = SCRIM_ALPHA))
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
 * A frame that fails to decode draws nothing and leaves the black fill of the enclosing box showing,
 * which is the "black is what shows before a background is ready" case, not a third background.
 */
@Composable
private fun DeliveredFrame(image: WearBackground.Image) {
    val frame: ImageBitmap? = remember(image.file.path, image.lastModified) {
        val bitmap = BitmapFactory.decodeFile(image.file.path)?.asImageBitmap()
        Timber.d("S2541: DeliveredFrame path=%s stamp=%d ok=%b", image.file.path, image.lastModified, bitmap != null)
        bitmap
    }
    if (frame != null) {
        Image(
            bitmap = frame,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
