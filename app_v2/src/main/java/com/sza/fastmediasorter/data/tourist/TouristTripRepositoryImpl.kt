package com.sza.fastmediasorter.data.tourist

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.model.tourist.ElevationSource
import com.sza.fastmediasorter.domain.model.tourist.PressureSample
import com.sza.fastmediasorter.domain.model.tourist.TouristTripSnapshot
import com.sza.fastmediasorter.domain.repository.TouristTripRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4145: the trip in a small preferences file rather than Room - a handful of scalars and a short sample
 * list need no schema, and an absent or partly unreadable file degrades field by field to an empty trip.
 */
@Singleton
class TouristTripRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : TouristTripRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun load(): TouristTripSnapshot = withContext(Dispatchers.IO) {
        TouristTripSnapshot(
            startedAtMillis = optionalLong(KEY_STARTED_AT),
            lastSampleAtMillis = optionalLong(KEY_LAST_SAMPLE_AT),
            movingMillis = prefs.getLong(KEY_MOVING, 0L),
            distanceMeters = prefs.getFloat(KEY_DISTANCE, 0f).toDouble(),
            gainMeters = prefs.getFloat(KEY_GAIN, 0f).toDouble(),
            lossMeters = prefs.getFloat(KEY_LOSS, 0f).toDouble(),
            maxSpeedKmh = prefs.getFloat(KEY_MAX_SPEED, 0f),
            elevationSource = prefs.getString(KEY_ELEVATION_SOURCE, null)
                ?.let { name -> ElevationSource.entries.firstOrNull { it.name == name } },
            lastElevationMeters = prefs.getFloat(KEY_LAST_ELEVATION, Float.NaN).takeIf { !it.isNaN() }?.toDouble(),
            pressureSamples = decodeSamples(prefs.getString(KEY_PRESSURE_SAMPLES, null)),
        )
    }

    override suspend fun save(snapshot: TouristTripSnapshot) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putLong(KEY_STARTED_AT, snapshot.startedAtMillis ?: NO_LONG)
            .putLong(KEY_LAST_SAMPLE_AT, snapshot.lastSampleAtMillis ?: NO_LONG)
            .putLong(KEY_MOVING, snapshot.movingMillis)
            .putFloat(KEY_DISTANCE, snapshot.distanceMeters.toFloat())
            .putFloat(KEY_GAIN, snapshot.gainMeters.toFloat())
            .putFloat(KEY_LOSS, snapshot.lossMeters.toFloat())
            .putFloat(KEY_MAX_SPEED, snapshot.maxSpeedKmh)
            .putString(KEY_ELEVATION_SOURCE, snapshot.elevationSource?.name)
            .putFloat(KEY_LAST_ELEVATION, snapshot.lastElevationMeters?.toFloat() ?: Float.NaN)
            .putString(KEY_PRESSURE_SAMPLES, encodeSamples(snapshot.pressureSamples))
            .apply()
    }

    private fun optionalLong(key: String): Long? = prefs.getLong(key, NO_LONG).takeIf { it != NO_LONG }

    private fun encodeSamples(samples: List<PressureSample>): String =
        samples.joinToString(SAMPLE_SEPARATOR) { "${it.atMillis}$FIELD_SEPARATOR${it.hectopascals}" }

    /** A malformed entry is dropped alone; the rest of the history still feeds the trend. */
    private fun decodeSamples(raw: String?): List<PressureSample> =
        raw.orEmpty().split(SAMPLE_SEPARATOR).mapNotNull { entry ->
            val parts = entry.split(FIELD_SEPARATOR)
            val at = parts.getOrNull(0)?.toLongOrNull()
            val hpa = parts.getOrNull(1)?.toFloatOrNull()
            if (at != null && hpa != null) PressureSample(at, hpa) else null
        }

    private companion object {
        const val PREFS_NAME = "tourist_trip"
        const val NO_LONG = Long.MIN_VALUE
        const val SAMPLE_SEPARATOR = ";"
        const val FIELD_SEPARATOR = ":"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_LAST_SAMPLE_AT = "last_sample_at"
        const val KEY_MOVING = "moving_ms"
        const val KEY_DISTANCE = "distance_m"
        const val KEY_GAIN = "gain_m"
        const val KEY_LOSS = "loss_m"
        const val KEY_MAX_SPEED = "max_speed_kmh"
        const val KEY_ELEVATION_SOURCE = "elevation_source"
        const val KEY_LAST_ELEVATION = "last_elevation_m"
        const val KEY_PRESSURE_SAMPLES = "pressure_samples"
    }
}
