package com.sza.fastmediasorter.data.weather

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.model.weather.WeatherCondition
import com.sza.fastmediasorter.domain.model.weather.WeatherDetails
import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.model.weather.WeatherSnapshot
import com.sza.fastmediasorter.domain.model.weather.WeatherUnit
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.WeatherRepository
import com.sza.fastmediasorter.domain.repository.WeatherResult
import com.sza.fastmediasorter.domain.weather.WeatherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0426: caches one reading per place and decides what the gadget shows when the network is gone.
 *
 * The disk mirror is what makes a cold start after a night on a shelf render immediately instead of
 * showing an empty block for as long as the first request takes.
 */
@Singleton
class WeatherRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val provider: WeatherProvider,
    private val settingsRepository: SettingsRepository,
) : WeatherRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val cache = mutableMapOf<String, WeatherSnapshot>()

    // Two weather cells on the same desktop wake up together; without this they would fire the same
    // request twice on every refresh tick.
    private val mutex = Mutex()

    // Dispatchers.IO, not the caller's context: the gadget calls this from the main thread and both the
    // SharedPreferences mirror and the provider call are blocking work.
    override suspend fun current(
        location: WeatherLocation,
        forceRefresh: Boolean,
    ): WeatherResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentLocked(location, forceRefresh)
        }
    }

    override suspend fun search(query: String): List<WeatherLocation> =
        provider.searchLocations(query, Locale.getDefault().language)

    private suspend fun currentLocked(location: WeatherLocation, forceRefresh: Boolean): WeatherResult {
        val key = cacheKey(location)
        // S2716: a reading in the other scale is not shown and not converted - it is refetched, so the
        // scale the user just picked reaches the desktop on the next gadget tick instead of at the TTL.
        val requestedUnit = requestedUnit()
        val cached = (cache[key] ?: readFromDisk(key, location)?.also { cache[key] = it })
            ?.takeIf { it.unit == requestedUnit }
        val now = System.currentTimeMillis()
        if (cached != null) {
            val ageMs = now - cached.observedAtMs
            val allowFetch = if (forceRefresh) ageMs >= MANUAL_REFRESH_THROTTLE_MS else ageMs >= TTL_MS
            if (!allowFetch) {
                return WeatherResult.Fresh(cached)
            }
        }
        val fetched = provider.currentWeather(location, requestedUnit)
        return when {
            fetched != null -> {
                cache[key] = fetched
                writeToDisk(key, fetched)
                WeatherResult.Fresh(fetched)
            }

            cached != null -> {
                WeatherResult.Stale(cached, now - cached.observedAtMs)
            }

            else -> {
                WeatherResult.Unavailable
            }
        }
    }

    /** The user's measurement system, resolved per call: the setting may change between two ticks. */
    private suspend fun requestedUnit(): WeatherUnit =
        when (settingsRepository.getSettings().first().unitSystem) {
            UnitSystem.METRIC -> WeatherUnit.CELSIUS
            UnitSystem.IMPERIAL -> WeatherUnit.FAHRENHEIT
        }

    /** Rounded so a hand-typed coordinate and its geocoded twin share one cache entry. */
    private fun cacheKey(location: WeatherLocation): String = String.format(
        Locale.US,
        KEY_FORMAT,
        location.latitude,
        location.longitude,
    )

    private fun readFromDisk(key: String, location: WeatherLocation): WeatherSnapshot? {
        val observedAt = prefs.getLong(key + SUFFIX_TIME, 0L)
        val temperature = prefs.getFloat(key + SUFFIX_TEMPERATURE, Float.NaN)
        val unit = prefs.getString(key + SUFFIX_UNIT, null)?.let { enumValueOrNull<WeatherUnit>(it) }
        val condition = prefs.getString(key + SUFFIX_CONDITION, null)
            ?.let { enumValueOrNull<WeatherCondition>(it) }
        val hasReading = observedAt != 0L && !temperature.isNaN()
        if (!hasReading || unit == null || condition == null) return null
        return WeatherSnapshot(
            location = location,
            temperature = temperature.toDouble(),
            unit = unit,
            condition = condition,
            isDay = prefs.getBoolean(key + SUFFIX_IS_DAY, true),
            observedAtMs = observedAt,
            // S1907: absent for every entry written before this ticket, so each of the three degrades to
            // "not available" on its own - a pre-S1907 cache still yields a readable temperature card.
            dewPoint = prefs.getFloat(key + SUFFIX_DEW_POINT, Float.NaN).takeIf { !it.isNaN() }?.toDouble(),
            sunrise = readLocalTime(key + SUFFIX_SUNRISE),
            sunset = readLocalTime(key + SUFFIX_SUNSET),
            details = readDetails(key),
        )
    }

    /** S4145: absent for every entry written before this ticket, so each field degrades to null on its own. */
    private fun readDetails(key: String): WeatherDetails = WeatherDetails(
        apparentTemperature = readDouble(key + SUFFIX_APPARENT),
        relativeHumidityPercent = readInt(key + SUFFIX_HUMIDITY),
        windSpeedKmh = readDouble(key + SUFFIX_WIND_SPEED),
        windGustsKmh = readDouble(key + SUFFIX_WIND_GUSTS),
        windDirectionDegrees = readInt(key + SUFFIX_WIND_DIRECTION),
        uvIndex = readDouble(key + SUFFIX_UV_INDEX),
        precipitationProbabilityPercent = readInt(key + SUFFIX_PRECIPITATION),
        cloudCoverPercent = readInt(key + SUFFIX_CLOUD_COVER),
        visibilityMetres = readDouble(key + SUFFIX_VISIBILITY),
        pressureMslHpa = readDouble(key + SUFFIX_PRESSURE_MSL),
    )

    private fun writeDetails(editor: SharedPreferences.Editor, key: String, details: WeatherDetails) {
        editor
            .putFloat(key + SUFFIX_APPARENT, details.apparentTemperature.toStoredFloat())
            .putInt(key + SUFFIX_HUMIDITY, details.relativeHumidityPercent ?: NO_INT)
            .putFloat(key + SUFFIX_WIND_SPEED, details.windSpeedKmh.toStoredFloat())
            .putFloat(key + SUFFIX_WIND_GUSTS, details.windGustsKmh.toStoredFloat())
            .putInt(key + SUFFIX_WIND_DIRECTION, details.windDirectionDegrees ?: NO_INT)
            .putFloat(key + SUFFIX_UV_INDEX, details.uvIndex.toStoredFloat())
            .putInt(key + SUFFIX_PRECIPITATION, details.precipitationProbabilityPercent ?: NO_INT)
            .putInt(key + SUFFIX_CLOUD_COVER, details.cloudCoverPercent ?: NO_INT)
            .putFloat(key + SUFFIX_VISIBILITY, details.visibilityMetres.toStoredFloat())
            .putFloat(key + SUFFIX_PRESSURE_MSL, details.pressureMslHpa.toStoredFloat())
    }

    private fun Double?.toStoredFloat(): Float = this?.toFloat() ?: Float.NaN

    private fun readDouble(prefKey: String): Double? =
        prefs.getFloat(prefKey, Float.NaN).takeIf { !it.isNaN() }?.toDouble()

    private fun readInt(prefKey: String): Int? = prefs.getInt(prefKey, NO_INT).takeIf { it != NO_INT }

    /** A stamp this app wrote, so a corrupt one is a bug elsewhere - it degrades, it does not throw. */
    private fun readLocalTime(prefKey: String): LocalTime? =
        prefs.getString(prefKey, null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private fun writeToDisk(key: String, snapshot: WeatherSnapshot) {
        prefs.edit()
            .putLong(key + SUFFIX_TIME, snapshot.observedAtMs)
            .putFloat(key + SUFFIX_TEMPERATURE, snapshot.temperature.toFloat())
            .putString(key + SUFFIX_UNIT, snapshot.unit.name)
            .putString(key + SUFFIX_CONDITION, snapshot.condition.name)
            .putBoolean(key + SUFFIX_IS_DAY, snapshot.isDay)
            .putFloat(key + SUFFIX_DEW_POINT, snapshot.dewPoint?.toFloat() ?: Float.NaN)
            .putString(key + SUFFIX_SUNRISE, snapshot.sunrise?.toString())
            .putString(key + SUFFIX_SUNSET, snapshot.sunset?.toString())
            .also { writeDetails(it, key, snapshot.details) }
            .apply()
    }

    /** A renamed or dropped enum constant must degrade to "no cache", not to a crash on read. */
    private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private companion object {
        const val PREFS_NAME = "weather_cache"
        val TTL_MS = TimeUnit.MINUTES.toMillis(20)
        val MANUAL_REFRESH_THROTTLE_MS = TimeUnit.SECONDS.toMillis(60)
        const val KEY_FORMAT = "%.2f_%.2f"
        const val SUFFIX_TIME = "_time"
        const val SUFFIX_TEMPERATURE = "_temp"
        const val SUFFIX_UNIT = "_unit"
        const val SUFFIX_CONDITION = "_cond"
        const val SUFFIX_IS_DAY = "_day"
        const val SUFFIX_DEW_POINT = "_dew"
        const val SUFFIX_SUNRISE = "_sunrise"
        const val SUFFIX_SUNSET = "_sunset"
        const val SUFFIX_APPARENT = "_feels"
        const val SUFFIX_HUMIDITY = "_hum"
        const val SUFFIX_WIND_SPEED = "_wind"
        const val SUFFIX_WIND_GUSTS = "_gust"
        const val SUFFIX_WIND_DIRECTION = "_wdir"
        const val SUFFIX_UV_INDEX = "_uv"
        const val SUFFIX_PRECIPITATION = "_pop"
        const val SUFFIX_CLOUD_COVER = "_cloud"
        const val SUFFIX_VISIBILITY = "_vis"
        const val SUFFIX_PRESSURE_MSL = "_msl"
        const val NO_INT = Int.MIN_VALUE
    }
}
