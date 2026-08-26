package com.sza.fastmediasorter.wear.ui.brand

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import kotlinx.coroutines.delay

/**
 * S1981: how long the frame holds itself, in ms - matches the system splash's own
 * `windowSplashScreenAnimationDuration` (`values-v31/themes.xml`) so this added beat does not
 * double the pause the user already accepts today (strategic §3.2, ADR-1).
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
            // Same mark the system splash already showed - the frame is a continuation of it,
            // not a second, visually distinct splash (strategic §2 goal 2). Image, not Icon: the
            // drawable carries its own two-tone gradient fill and Icon would flatten it to a
            // single tint colour.
            Image(
                painter = painterResource(R.drawable.ic_splash_app_brand),
                // Decorative: the wordmark right below already carries the same information
                // (strategic §3.2 Доступность).
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
