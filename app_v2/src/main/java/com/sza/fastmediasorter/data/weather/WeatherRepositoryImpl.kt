package com.sza.fastmediasorter.data.weather

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.core.util.LocaleHelper
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
import timber.log.Timber
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
) : WeatherRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        val cached = cache[key] ?: readFromDisk(key, location)?.also { cache[key] = it }
        val now = System.currentTimeMillis()
        if (cached != null) {
            val ageMs = now - cached.observedAtMs
            val allowFetch = if (forceRefresh) ageMs >= MANUAL_REFRESH_THROTTLE_MS else ageMs >= TTL_MS
            if (!allowFetch) {
                return WeatherResult.Fresh(cached)
            }
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
            // S1907: absent for every entry written before this ticket, so each of the three degrades to
            // "not available" on its own - a pre-S1907 cache still yields a readable temperature card.
            dewPoint = prefs.getFloat(key + SUFFIX_DEW_POINT, Float.NaN).takeIf { !it.isNaN() }?.toDouble(),
            sunrise = readLocalTime(key + SUFFIX_SUNRISE),
            sunset = readLocalTime(key + SUFFIX_SUNSET),
        )
    }

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
            .apply()
    }

    /** A renamed or dropped enum constant must degrade to "no cache", not to a crash on read. */
    private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }

    // S2598: the region of the device, not of the process default - the latter is built from a declared
    // language tag and carries no region at all, which left this branch unreachable and every user in the
    // three countries below on Celsius.
    private fun preferredUnit(): WeatherUnit {
        val fahrenheit = LocaleHelper.systemRegion(context) in FAHRENHEIT_COUNTRIES
        Timber.d("S2598: weather preferredUnit fahrenheit=$fahrenheit")
        return if (fahrenheit) WeatherUnit.FAHRENHEIT else WeatherUnit.CELSIUS
    }

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
        val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM")
    }
}
