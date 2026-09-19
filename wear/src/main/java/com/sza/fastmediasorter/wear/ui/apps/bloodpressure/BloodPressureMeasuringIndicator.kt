package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import java.util.concurrent.TimeUnit

private val RING_SIZE = 96.dp
private val RING_STROKE = 6.dp

// The capture reports progress once a second; animating over the same second turns the steps into a
// continuous sweep instead of a ring that jumps.
private const val PROGRESS_ANIMATION_MS = 1_000

/**
 * S3113: the unmistakable "a measurement is running" state - a heading, a ring filling over the window
 * with the seconds left inside it, and the hold-still hint.
 *
 * Added after the owner's first run on the Galaxy Watch 7, where a one-line progress caption beside the
 * previous value read as "nothing is happening" until the estimate suddenly appeared. One TalkBack node
 * says the same thing in words.
 */
@Composable
internal fun MeasuringIndicator(elapsedMillis: Long, totalMillis: Long) {
    val secondsLeft = TimeUnit.MILLISECONDS.toSeconds((totalMillis - elapsedMillis).coerceAtLeast(0L)).toInt()
    val target = if (totalMillis > 0L) (elapsedMillis.toFloat() / totalMillis).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = PROGRESS_ANIMATION_MS, easing = LinearEasing),
        label = "bloodPressureMeasuringProgress"
    )
    val description = stringResource(R.string.blood_pressure_measuring_description, secondsLeft)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.blood_pressure_measuring),
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.primary
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(RING_SIZE),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize(),
                strokeWidth = RING_STROKE
            )
            Text(
                text = stringResource(R.string.blood_pressure_seconds_short, secondsLeft),
                style = MaterialTheme.typography.title1.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colors.onSurface
            )
        }
        StatusText(stringResource(R.string.blood_pressure_hold_still))
    }
}
