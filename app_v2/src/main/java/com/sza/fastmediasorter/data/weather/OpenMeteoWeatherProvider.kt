package com.sza.fastmediasorter.data.weather

import com.sza.fastmediasorter.domain.model.weather.WeatherCondition
import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.model.weather.WeatherSnapshot
import com.sza.fastmediasorter.domain.model.weather.WeatherUnit
import com.sza.fastmediasorter.domain.weather.WeatherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0426: Open-Meteo, keyless and free for non-commercial use. Attribution ("Weather data by
 * Open-Meteo.com", CC-BY 4.0) is an obligation of that licence and is rendered by the gadget.
 */
@Singleton
class OpenMeteoWeatherProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : WeatherProvider {

    override suspend fun currentWeather(location: WeatherLocation, unit: WeatherUnit): WeatherSnapshot? =
        withContext(Dispatchers.IO) {
            val url = (FORECAST_URL.toHttpUrlOrNull() ?: return@withContext null).newBuilder()
                .addQueryParameter(PARAM_LATITUDE, location.latitude.toString())
                .addQueryParameter(PARAM_LONGITUDE, location.longitude.toString())
                .addQueryParameter(PARAM_CURRENT, CURRENT_FIELDS)
                .addQueryParameter(PARAM_TEMPERATURE_UNIT, unit.apiValue())
                .addQueryParameter(PARAM_TIMEZONE, TIMEZONE_AUTO)
                .build()
            val body = fetch(url.toString(), "current weather") ?: return@withContext null
            parseCurrent(body, location, unit)
        }

    override suspend fun searchLocations(query: String, languageTag: String): List<WeatherLocation> =
        withContext(Dispatchers.IO) {
            val url = (GEOCODING_URL.toHttpUrlOrNull() ?: return@withContext emptyList()).newBuilder()
                .addQueryParameter(PARAM_NAME, query)
                .addQueryParameter(PARAM_COUNT, RESULT_LIMIT.toString())
                .addQueryParameter(PARAM_LANGUAGE, languageTag)
                .addQueryParameter(PARAM_FORMAT, FORMAT_JSON)
                .build()
            val body = fetch(url.toString(), "place search") ?: return@withContext emptyList()
            parsePlaces(body)
        }

    /** Returns the response body, or null when the service is unreachable or refuses the request. */
    private fun fetch(url: String, what: String): String? = try {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.i("Open-Meteo %s unavailable (HTTP %d)", what, response.code)
                null
            } else {
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        }
    } catch (e: IOException) {
        Timber.i(e, "Open-Meteo %s unreachable", what)
        null
    }

    private fun parseCurrent(json: String, location: WeatherLocation, unit: WeatherUnit): WeatherSnapshot? = try {
        val current = JSONObject(json).optJSONObject(KEY_CURRENT)
        if (current == null || !current.has(KEY_TEMPERATURE)) {
            Timber.i("Open-Meteo current weather has no reading for %s", location.label)
            null
        } else {
            WeatherSnapshot(
                location = location,
                temperature = current.getDouble(KEY_TEMPERATURE),
                unit = unit,
                condition = WeatherCondition.fromWmoCode(current.optInt(KEY_WEATHER_CODE, UNKNOWN_CODE)),
                isDay = current.optInt(KEY_IS_DAY, 1) == 1,
                observedAtMs = System.currentTimeMillis(),
            )
        }
    } catch (e: JSONException) {
        Timber.w(e, "Open-Meteo current weather malformed")
        null
    }

    private fun parsePlaces(json: String): List<WeatherLocation> = try {
        val results = JSONObject(json).optJSONArray(KEY_RESULTS) ?: return emptyList()
        (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString(KEY_NAME).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WeatherLocation(
                latitude = item.optDouble(KEY_LATITUDE, Double.NaN).takeIf { !it.isNaN() } ?: return@mapNotNull null,
                longitude = item.optDouble(KEY_LONGITUDE, Double.NaN).takeIf { !it.isNaN() } ?: return@mapNotNull null,
                label = labelOf(name, item),
            )
        }
    } catch (e: JSONException) {
        Timber.w(e, "Open-Meteo place search malformed")
        emptyList()
    }

    /** "Kyiv, Kyiv City, UA" - region and country disambiguate the dozens of same-named towns. */
    private fun labelOf(name: String, item: JSONObject): String = listOfNotNull(
        name,
        item.optString(KEY_ADMIN1).takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) },
        item.optString(KEY_COUNTRY_CODE).takeIf { it.isNotBlank() },
    ).joinToString(LABEL_SEPARATOR)

    private fun WeatherUnit.apiValue(): String = when (this) {
        WeatherUnit.CELSIUS -> UNIT_CELSIUS
        WeatherUnit.FAHRENHEIT -> UNIT_FAHRENHEIT
    }

    private companion object {
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"

        const val PARAM_LATITUDE = "latitude"
        const val PARAM_LONGITUDE = "longitude"
        const val PARAM_CURRENT = "current"
        const val PARAM_TEMPERATURE_UNIT = "temperature_unit"
        const val PARAM_TIMEZONE = "timezone"
        const val PARAM_NAME = "name"
        const val PARAM_COUNT = "count"
        const val PARAM_LANGUAGE = "language"
        const val PARAM_FORMAT = "format"

        const val CURRENT_FIELDS = "temperature_2m,weather_code,is_day"
        const val TIMEZONE_AUTO = "auto"
        const val FORMAT_JSON = "json"
        const val UNIT_CELSIUS = "celsius"
        const val UNIT_FAHRENHEIT = "fahrenheit"
        const val RESULT_LIMIT = 8
        const val UNKNOWN_CODE = -1
        const val LABEL_SEPARATOR = ", "

        const val KEY_CURRENT = "current"
        const val KEY_TEMPERATURE = "temperature_2m"
        const val KEY_WEATHER_CODE = "weather_code"
        const val KEY_IS_DAY = "is_day"
        const val KEY_RESULTS = "results"
        const val KEY_NAME = "name"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ADMIN1 = "admin1"
        const val KEY_COUNTRY_CODE = "country_code"
    }
}
