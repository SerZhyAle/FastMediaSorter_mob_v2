package com.sza.fastmediasorter.wear.ui.brand

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.sza.fastmediasorter.wear.R
import kotlinx.coroutines.delay

/**
 * S1981: how long the frame holds itself, in ms.
 *
 * S2274 removed `windowSplashScreenAnimationDuration` from `values-v31/themes.xml`, so this is no
 * longer mirroring a theme attribute - it is now the sole owner of the beat, kept at its original
 * value so the pause the user already accepts does not change length.
 */
private const val BRAND_FRAME_DURATION_MS = 700L
private val LOGO_SIZE = 72.dp
private val LOGO_WORDMARK_GAP = 16.dp

/**
 * S1981: the branded first frame - shown once per cold start, before the permissions/navigation
 * branch in `WearApp`, carrying the real "Fast Media Sorter" wordmark the system splash cannot
 * render (its icon slot only accepts a picture, never real text - strategic §4/§5 ADR-2).
 *
 * Self-dismissing: no interaction is expected, [onTimeout] fires once after
 * [BRAND_FRAME_DURATION_MS] and the caller is responsible for not recomposing this screen again
 * within the same `Activity` instance (strategic §6 item 4).
 */
@Composable
fun BrandFrameScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(BRAND_FRAME_DURATION_MS)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LOGO_WORDMARK_GAP)
        ) {
            // Same mark the system splash already showed - the frame is a continuation of it, not a
            // second, visually distinct splash (S1981 strategic §2 goal 2). S2274 moved the splash
            // back to the platform default, which draws the launcher icon, so preserving that
            // invariant means naming the launcher icon here too - and Wear quality rule WO-V15
            // judges everything the user sees at startup, not only the splash window.
            //
            // AsyncImage, not painterResource: `ic_launcher` is an <adaptive-icon>, and
            // painterResource decodes only vectors and rasters - it throws on this one. Coil
            // resolves the adaptive icon through the platform drawable, which applies the device
            // mask itself, so the frame shows the same rounded mark the launcher does.
            AsyncImage(
                model = R.mipmap.ic_launcher,
                // Decorative: the wordmark right below already carries the same information
                // (S1981 strategic §3.2 Доступность).
                contentDescription = null,
                modifier = Modifier.size(LOGO_SIZE)
            )
            Text(
                text = stringResource(R.string.wear_brand_wordmark),
                style = MaterialTheme.typography.title1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = LOGO_WORDMARK_GAP)
            )
        }
    }
}
