package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.core.util.WearUnitScale
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.tourist.WearElevationSource
import com.sza.fastmediasorter.wear.domain.tourist.WearPressureTrendResult
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import com.sza.fastmediasorter.wear.domain.tourist.WearTripMotionPolicy
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import java.util.Locale
import kotlin.math.abs

/**
 * S4146: the promoted card of the metrics added by S4146. Each one is the shared label and value plus,
 * where the reading needs it, a second line in words - the source, the calibration state, the trend
 * or the warning - so nothing is conveyed by colour alone.
 */
@Composable
fun HeroExtraMetricView(metric: TouristMetricType, state: WearTouristState, isMetric: Boolean) {
    HeroTextView(
        label = touristMetricLabel(metric),
        value = heroValue(metric, state, isMetric),
        detail = heroDetail(metric, state, isMetric),
    )
}

/** Below API 34 the GPS height is over the ellipsoid; the card says so instead of passing it off as sea level. */
@Composable
fun HeroEllipsoidNote(state: WearTouristState) {
    if (state.altitudeMeters != null && state.gpsAltitudeIsEllipsoid) {
        HeroDetailText(stringResource(R.string.wear_tourist_altitude_ellipsoid))
    }
}

@Composable
private fun heroValue(metric: TouristMetricType, state: WearTouristState, isMetric: Boolean): String =
    when (metric) {
        TouristMetricType.ELEVATION_GAIN_LOSS -> if (state.elevationSource == null) {
            stringResource(R.string.wear_tourist_status_accumulating)
        } else {
            "+${lengthValue(state.gainMeters, isMetric)} ${lengthUnit(isMetric)}"
        }
        TouristMetricType.GPS_ACCURACY -> state.gpsAccuracyMeters?.let {
            "${lengthValue(it.toDouble(), isMetric)} ${lengthUnit(isMetric)}"
        } ?: stringResource(R.string.wear_tourist_no_fix)
        TouristMetricType.LEVEL -> {
            val pitch = state.pitchDegrees
            val roll = state.rollDegrees
            if (pitch == null || roll == null) {
                stringResource(R.string.wear_tourist_status_sensor_waiting)
            } else {
                stringResource(R.string.wear_tourist_detail_level, pitch.toInt().toString(), roll.toInt().toString())
            }
        }
        TouristMetricType.MAGNETIC_FIELD -> state.magneticMicrotesla?.let {
            "${String.format(Locale.US, "%.0f", it)} ${stringResource(R.string.wear_tourist_unit_microtesla)}"
        } ?: stringResource(R.string.wear_tourist_status_sensor_waiting)
        else -> touristMetricValue(metric, state, isMetric)
    }

@Composable
private fun heroDetail(metric: TouristMetricType, state: WearTouristState, isMetric: Boolean): String? =
    when (metric) {
        TouristMetricType.PRESSURE -> state.pressureHpa?.let { pressureTrendDetail(state.pressureTrend) }
        TouristMetricType.BAROMETRIC_ALTITUDE ->
            state.baroAltitudeMeters?.takeIf { state.baroAltitudeApproximate }?.let {
                stringResource(R.string.wear_tourist_status_approximate)
            }
        TouristMetricType.ELEVATION_GAIN_LOSS -> state.elevationSource?.let { source ->
            stringResource(
                R.string.wear_tourist_detail_descent,
                lengthValue(state.lossMeters, isMetric).toString(),
                lengthUnit(isMetric),
                elevationSourceWord(source),
            )
        }
        TouristMetricType.AVERAGE_PACE -> state.avgSpeedKmh.takeIf { state.movingMillis > 0L }?.let {
            formatPaceText(it, isMetric)
        }
        TouristMetricType.GPS_ACCURACY -> WearTripMotionPolicy.gpsAccuracyGrade(state.gpsAccuracyMeters)?.let {
            gpsAccuracyWord(it)
        }
        TouristMetricType.DECLINATION -> state.declinationDegrees?.let {
            stringResource(R.string.wear_tourist_detail_declination, String.format(Locale.US, "%.1f", abs(it)))
        }
        TouristMetricType.MAGNETIC_FIELD -> state.magneticMicrotesla?.let {
            if (state.magneticInterference) {
                stringResource(R.string.wear_tourist_magnetic_interference)
            } else {
                stringResource(R.string.wear_tourist_magnetic_normal)
            }
        }
        else -> null
    }

@Composable
private fun pressureTrendDetail(trend: WearPressureTrendResult?): String = when (trend) {
    is WearPressureTrendResult.Ready ->
        "${pressureTrendWord(trend.trend)} ${String.format(Locale.US, "%+.1f", trend.deltaHpa)} " +
            stringResource(R.string.wear_tourist_unit_hpa)
    WearPressureTrendResult.Accumulating, null -> stringResource(R.string.wear_tourist_status_accumulating)
}

@Composable
private fun elevationSourceWord(source: WearElevationSource): String = when (source) {
    WearElevationSource.BAROMETER -> stringResource(R.string.wear_tourist_elevation_source_barometer)
    WearElevationSource.GPS -> stringResource(R.string.wear_tourist_elevation_source_gps)
}

private fun lengthValue(metres: Double, isMetric: Boolean): Int =
    if (isMetric) metres.toInt() else WearUnitScale.metresToFeet(metres).toInt()

@Composable
private fun HeroTextView(label: String, value: String, detail: String?) {
    Text(
        text = label,
        style = MaterialTheme.typography.caption2,
        color = WearAppTheme.colors.touristAccent,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.title1,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onSurface,
        textAlign = TextAlign.Center,
    )
    if (detail != null) {
        HeroDetailText(detail)
    }
}

@Composable
private fun HeroDetailText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption3,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
