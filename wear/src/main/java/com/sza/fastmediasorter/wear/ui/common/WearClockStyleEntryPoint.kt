package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sza.fastmediasorter.wear.domain.model.WearClockStyle
import com.sza.fastmediasorter.wear.domain.repository.WearClockStyleRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** S3557: reaches the clock-style store from composables that have no view model of their own. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WearClockStyleEntryPoint {
    fun clockStyleRepository(): WearClockStyleRepository
}

/**
 * S3557: the paired phone's clock style, [WearClockStyle.DEFAULT] until the store answers.
 *
 * Read where it is drawn rather than threaded down from the activity: the backdrop, the audio player
 * and the dim clock sit under three unrelated call chains, and the default is today's picture, so the
 * first frame before the store answers looks exactly as it did before the ticket.
 */
@Composable
fun rememberWearClockStyle(): WearClockStyle {
    val context = LocalContext.current
    val repository = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, WearClockStyleEntryPoint::class.java)
            .clockStyleRepository()
    }
    val style by repository.style.collectAsStateWithLifecycle(initialValue = WearClockStyle.DEFAULT)
    return style
}

/** The phone wallpaper's three controls in the form the backdrop renderer takes. */
fun WearClockStyle.backdropTuning(): WaveParticleTuning = WaveParticleTuning(
    intensity = wallpaperIntensity,
    speed = wallpaperAnimationSpeed,
    density = wallpaperParticleDensity
)
