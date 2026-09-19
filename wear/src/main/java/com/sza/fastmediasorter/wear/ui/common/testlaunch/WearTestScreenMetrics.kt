package com.sza.fastmediasorter.wear.ui.common.testlaunch

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

private const val DP_PER_DENSITY_UNIT = 160

/** S3201: the density and configuration the watch UI is laid out with for this launch. */
data class WearTestScreenMetrics(
    val density: Density,
    val configuration: Configuration,
)

/**
 * S3201: the metrics that lay the watch UI out as if its shorter edge were [screenDp].
 *
 * The in-app replacement for `wm density`, which changes the whole device and outlives the test. Both
 * values move together: the shape layer in `WearScreenScaffold` sizes itself from
 * `LocalConfiguration.screenWidthDp`, while every `dp` turns into pixels through `LocalDensity`, so
 * scaling one without the other would draw a small-glass layout at the large glass's pixel size.
 *
 * A null [screenDp] returns the current values unchanged, so the caller provides both locals on every
 * launch and a plain launch re-provides what was already there.
 */
@Composable
fun rememberWearTestScreenMetrics(screenDp: Int?): WearTestScreenMetrics {
    val baseConfiguration = LocalConfiguration.current
    val baseDensity = LocalDensity.current
    return remember(screenDp, baseConfiguration, baseDensity) {
        if (screenDp == null) {
            WearTestScreenMetrics(baseDensity, baseConfiguration)
        } else {
            val shorterEdgeDp = minOf(baseConfiguration.screenWidthDp, baseConfiguration.screenHeightDp)
            val density = baseDensity.density * shorterEdgeDp / screenDp
            val scale = baseDensity.density / density
            val configuration = Configuration(baseConfiguration).apply {
                screenWidthDp = (baseConfiguration.screenWidthDp * scale).roundToInt()
                screenHeightDp = (baseConfiguration.screenHeightDp * scale).roundToInt()
                smallestScreenWidthDp = minOf(screenWidthDp, screenHeightDp)
                densityDpi = (density * DP_PER_DENSITY_UNIT).roundToInt()
            }
            WearTestScreenMetrics(Density(density, baseDensity.fontScale), configuration)
        }
    }
}
