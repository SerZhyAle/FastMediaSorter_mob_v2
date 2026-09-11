package com.sza.fastmediasorter.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * S2922: pure offline astronomical calculator for solar events (sunrise, sunset, daylight)
 * based on the standard NOAA Solar Position Algorithm.
 */
object SolarCalculator {

    private const val OFFICIAL_ZENITH = 90.83333333333333

    data class SolarTimes(
        val sunriseMillis: Long?,
        val sunsetMillis: Long?,
        val isDaylight: Boolean,
    )

    /**
     * Calculates sunrise and sunset epoch milliseconds for the given latitude, longitude, and reference time.
     */
    fun calculateSunriseSunset(
        latitude: Double,
        longitude: Double,
        timestampMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): SolarTimes {
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), zoneId)
        val localDate = zonedDateTime.toLocalDate()
        val dayOfYear = localDate.dayOfYear

        val sunriseHourUtc = computeSunTimeHour(latitude, longitude, dayOfYear, isSunrise = true)
        val sunsetHourUtc = computeSunTimeHour(latitude, longitude, dayOfYear, isSunrise = false)

        val sunriseMillis = sunriseHourUtc?.let { hour -> toEpochMillis(localDate, hour) }
        val sunsetMillis = sunsetHourUtc?.let { hour -> toEpochMillis(localDate, hour) }

        val isDaylight = when {
            sunriseMillis != null && sunsetMillis != null -> {
                if (sunriseMillis < sunsetMillis) {
                    timestampMillis in sunriseMillis..sunsetMillis
                } else {
                    timestampMillis >= sunriseMillis || timestampMillis <= sunsetMillis
                }
            }
            sunriseMillis == null && sunsetMillis == null -> {
                val approxDeclination = 23.45 * sin(Math.toRadians((360.0 / 365.0) * (dayOfYear - 81)))
                (latitude * approxDeclination) > 0
            }
            else -> true
        }

        return SolarTimes(
            sunriseMillis = sunriseMillis,
            sunsetMillis = sunsetMillis,
            isDaylight = isDaylight,
        )
    }

    private fun computeSunTimeHour(
        latitude: Double,
        longitude: Double,
        dayOfYear: Int,
        isSunrise: Boolean,
    ): Double? {
        val lngHour = longitude / 15.0
        val t = if (isSunrise) {
            dayOfYear + ((6.0 - lngHour) / 24.0)
        } else {
            dayOfYear + ((18.0 - lngHour) / 24.0)
        }

        val meanAnomalyDeg = (0.9856 * t) - 3.289
        val meanAnomalyRad = Math.toRadians(meanAnomalyDeg)

        var trueLongitudeDeg = meanAnomalyDeg +
            (1.916 * sin(meanAnomalyRad)) +
            (0.020 * sin(2.0 * meanAnomalyRad)) +
            282.634
        trueLongitudeDeg = normalizeDegrees(trueLongitudeDeg)
        val trueLongitudeRad = Math.toRadians(trueLongitudeDeg)

        var rightAscensionDeg = Math.toDegrees(atan(0.91764 * tan(trueLongitudeRad)))
        rightAscensionDeg = normalizeDegrees(rightAscensionDeg)

        val lQuadrant = floor(trueLongitudeDeg / 90.0) * 90.0
        val raQuadrant = floor(rightAscensionDeg / 90.0) * 90.0
        rightAscensionDeg += (lQuadrant - raQuadrant)
        val rightAscensionHours = rightAscensionDeg / 15.0

        val sinDec = 0.39782 * sin(trueLongitudeRad)
        val cosDec = cos(asin(sinDec))

        val latRad = Math.toRadians(latitude)
        val zenithRad = Math.toRadians(OFFICIAL_ZENITH)
        val cosH = (cos(zenithRad) - (sinDec * sin(latRad))) / (cosDec * cos(latRad))

        if (cosH > 1.0 || cosH < -1.0) {
            return null
        }

        val hDeg = if (isSunrise) {
            360.0 - Math.toDegrees(acos(cosH))
        } else {
            Math.toDegrees(acos(cosH))
        }
        val hHours = hDeg / 15.0

        val localMeanTime = hHours + rightAscensionHours - (0.06571 * t) - 6.622
        val utHours = localMeanTime - lngHour
        return normalizeHours(utHours)
    }

    private fun normalizeDegrees(value: Double): Double {
        var norm = value % 360.0
        if (norm < 0) norm += 360.0
        return norm
    }

    private fun normalizeHours(value: Double): Double {
        var norm = value % 24.0
        if (norm < 0) norm += 24.0
        return norm
    }

    private fun toEpochMillis(localDate: LocalDate, utcHour: Double): Long {
        val hours = utcHour.toInt()
        val minutesFraction = (utcHour - hours) * 60.0
        val minutes = minutesFraction.toInt()
        val seconds = ((minutesFraction - minutes) * 60.0).toInt()

        val utcDateTime = ZonedDateTime.of(
            localDate.year,
            localDate.monthValue,
            localDate.dayOfMonth,
            hours.coerceIn(0, 23),
            minutes.coerceIn(0, 59),
            seconds.coerceIn(0, 59),
            0,
            ZoneId.of("UTC"),
        )
        return utcDateTime.toInstant().toEpochMilli()
    }
}
