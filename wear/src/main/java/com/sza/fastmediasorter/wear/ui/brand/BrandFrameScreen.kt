package com.sza.fastmediasorter.wear.ui.brand

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.sza.fastmediasorter.wear.R
import kotlinx.coroutines.delay
import timber.log.Timber
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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

/** S2556: hour-minute-second skeletons handed to `getBestDateTimePattern`, 24-hour and 12-hour. */
private const val TIME_SKELETON_24H = "Hms"
private const val TIME_SKELETON_12H = "hms"
private const val MILLIS_PER_SECOND = 1000L

/**
 * S2556: the wall-clock time, re-emitted on every second boundary for as long as this frame is
 * composed - the frame's beat is short, so what the time has to be right about is the instant it
 * appears, not the tick after it.
 *
 * The pattern is derived, never literal: [DateFormat.getBestDateTimePattern] follows the device's
 * own 12/24-hour setting and the locale's field order, which is what the strategic spec asks for
 * instead of the frame imposing a format of its own.
 */
@Composable
private fun currentTimeText(): String {
    val context = LocalContext.current
    val formatter = remember(context) {
        val locale = context.resources.configuration.locales[0]
        val skeleton = if (DateFormat.is24HourFormat(context)) TIME_SKELETON_24H else TIME_SKELETON_12H
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
    // Sleeping to the next boundary rather than a flat second: a flat delay lands wherever the first
    // composition happened to fall, so the shown seconds would trail the device's own by a constant
    // fraction for the whole life of the frame.
    val time = produceState(LocalTime.now().format(formatter), formatter) {
        while (true) {
            value = LocalTime.now().format(formatter)
            delay(MILLIS_PER_SECOND - System.currentTimeMillis() % MILLIS_PER_SECOND)
        }
    }
    return time.value
}

/**
 * S1981: the branded first frame - shown once per cold start, before the permissions/navigation
 * branch in `WearApp`, carrying the real "Fast Media Sorter" wordmark the system splash cannot
 * render (its icon slot only accepts a picture, never real text - strategic §4/§5 ADR-2).
 *
 * S2556: the same column now also carries the current time with seconds. The beat is deliberately
 * unchanged - the frame was already holding it, and the time is what makes the hold worth something
 * rather than a reason to hold longer.
 *
 * Self-dismissing: no interaction is expected, [onTimeout] fires once after
 * [BRAND_FRAME_DURATION_MS] and the caller is responsible for not recomposing this screen again
 * within the same `Activity` instance (strategic §6 item 4).
 */
@Composable
fun BrandFrameScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        Timber.d("S2556: wear brand frame composed, holding ${BRAND_FRAME_DURATION_MS}ms")
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
            // S2556: one typography step below the wordmark - the brand is what the frame is for and
            // the time is what makes the beat worth spending, in that order. Kept inside the same
            // column so it stays within the round glass the icon and wordmark already fit.
            Text(
                text = currentTimeText(),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = LOGO_WORDMARK_GAP)
            )
        }
    }
}
