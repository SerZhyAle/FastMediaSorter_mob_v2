package com.sza.fastmediasorter.data.weather

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.model.weather.WeatherCondition
import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.model.weather.WeatherSnapshot
import com.sza.fastmediasorter.domain.model.weather.WeatherUnit
import com.sza.fastmediasorter.domain.repository.WeatherRepository
import com.sza.fastmediasorter.domain.repository.WeatherResult
import com.sza.fastmediasorter.domain.weather.WeatherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    @ApplicationContext context: Context,
    private val provider: WeatherProvider,
) : WeatherRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val cache = mutableMapOf<String, WeatherSnapshot>()

    // Two weather cells on the same desktop wake up together; without this they would fire the same
    // request twice on every refresh tick.
    private val mutex = Mutex()

    // Dispatchers.IO, not the caller's context: the gadget calls this from the main thread and both the
    // SharedPreferences mirror and the provider call are blocking work.
    override suspend fun current(location: WeatherLocation): WeatherResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentLocked(location)
        }
    }

    override suspend fun search(query: String): List<WeatherLocation> =
        provider.searchLocations(query, Locale.getDefault().language)

    private suspend fun currentLocked(location: WeatherLocation): WeatherResult {
        val key = cacheKey(location)
        val cached = cache[key] ?: readFromDisk(key, location)?.also { cache[key] = it }
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.observedAtMs < TTL_MS) {
            return WeatherResult.Fresh(cached)
        }
        val fetched = provider.currentWeather(location, preferredUnit())
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
        )
    }

    private fun writeToDisk(key: String, snapshot: WeatherSnapshot) {
        prefs.edit()
            .putLong(key + SUFFIX_TIME, snapshot.observedAtMs)
            .putFloat(key + SUFFIX_TEMPERATURE, snapshot.temperature.toFloat())
            .putString(key + SUFFIX_UNIT, snapshot.unit.name)
            .putString(key + SUFFIX_CONDITION, snapshot.condition.name)
            .putBoolean(key + SUFFIX_IS_DAY, snapshot.isDay)
            .apply()
    }

    /** A renamed or dropped enum constant must degrade to "no cache", not to a crash on read. */
    private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private fun preferredUnit(): WeatherUnit =
        if (Locale.getDefault().country in FAHRENHEIT_COUNTRIES) WeatherUnit.FAHRENHEIT else WeatherUnit.CELSIUS

    private companion object {
        const val PREFS_NAME = "weather_cache"
        val TTL_MS = TimeUnit.MINUTES.toMillis(20)
        const val KEY_FORMAT = "%.2f_%.2f"
        const val SUFFIX_TIME = "_time"
        const val SUFFIX_TEMPERATURE = "_temp"
        const val SUFFIX_UNIT = "_unit"
        const val SUFFIX_CONDITION = "_cond"
        const val SUFFIX_IS_DAY = "_day"
        val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM")
    }
}
