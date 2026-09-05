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
import timber.log.Timber

/**
 * Constant by design (S2000, strategic 3.3.9): the watch draws light content, so a scrim that varied
 * with the chosen picture would make contrast a property of the owner's photo rather than a
 * guarantee. Matched to the value already tuned for arbitrary album art on the audio player, which
 * is the same worst case - a bright, uncontrolled image under white text.
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
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
                        .background(Color.Black.copy(alpha = SCRIM_ALPHA))
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
                        .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                )
            }

            is WearBackground.Image -> {
                DeliveredFrame(image = background)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                )
            }

            is WearBackground.None -> {
                // Black screen background: outer Box background is already Color.Black.
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
        Timber.d(
            "S2541: DeliveredFrame path=%s stamp=%d decoded=%b",
            image.file.path,
            image.lastModified,
            bitmap != null
        )
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
