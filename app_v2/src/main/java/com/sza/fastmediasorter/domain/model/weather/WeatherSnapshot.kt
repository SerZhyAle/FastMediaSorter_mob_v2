package com.sza.fastmediasorter.domain.model.weather

/**
 * S0426: a place the weather is shown for. Persisted inside a launcher GADGET cell's `target`, so the
 * codec below is the single source of truth for that encoding - the picker writes it, the gadget reads
 * it, and neither owns the format.
 */
data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String,
) {

    /** `lat,lon,label` - the label goes last because it is the only part allowed to contain commas. */
    fun encode(): String = "$latitude,$longitude,$label"

    companion object {

        fun decode(raw: String?): WeatherLocation? {
            // limit = 3 keeps a label with commas intact - it is always the last field.
            val parts = raw?.trim().orEmpty().split(',', limit = PART_COUNT)
            if (parts.size < PART_COUNT) return null
            val latitude = parts[0].toDoubleOrNull()
            val longitude = parts[1].toDoubleOrNull()
            val label = parts[2].trim()
            return if (latitude == null || longitude == null || label.isEmpty()) {
                null
            } else {
                WeatherLocation(latitude, longitude, label)
            }
        }

        private const val PART_COUNT = 3
    }
}

enum class WeatherUnit {
    CELSIUS,
    FAHRENHEIT,
}

/**
 * The condition groups the gadget can draw. Deliberately coarse: the strategic scope is a temperature
 * plus one glyph, so WMO's 28 distinct codes collapse into the seven shapes an icon set can express.
 */
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    RAIN,
    SNOW,
    THUNDERSTORM,
    UNKNOWN,
    ;

    companion object {

        /** WMO 4677 weather codes as served by Open-Meteo's `weather_code` field. */
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            CODE_CLEAR, CODE_MAINLY_CLEAR -> CLEAR
            CODE_PARTLY_CLOUDY -> PARTLY_CLOUDY
            CODE_OVERCAST -> CLOUDY
            in CODE_FOG_RANGE -> FOG
            in CODE_DRIZZLE_RANGE, in CODE_RAIN_RANGE, in CODE_SHOWERS_RANGE -> RAIN
            in CODE_FREEZING_RANGE, in CODE_SNOW_RANGE, in CODE_SNOW_SHOWERS_RANGE -> SNOW
            in CODE_THUNDERSTORM_RANGE -> THUNDERSTORM
            else -> UNKNOWN
        }

        private const val CODE_CLEAR = 0
        private const val CODE_MAINLY_CLEAR = 1
        private const val CODE_PARTLY_CLOUDY = 2
        private const val CODE_OVERCAST = 3
        private val CODE_FOG_RANGE = 45..48
        private val CODE_DRIZZLE_RANGE = 51..55
        private val CODE_FREEZING_RANGE = 56..57
        private val CODE_RAIN_RANGE = 61..65
        private val CODE_SNOW_RANGE = 66..77
        private val CODE_SHOWERS_RANGE = 80..82
        private val CODE_SNOW_SHOWERS_RANGE = 85..86
        private val CODE_THUNDERSTORM_RANGE = 95..99
    }
}

/** One reading for one place. [observedAtMs] is our fetch time - it drives the staleness decision. */
data class WeatherSnapshot(
    val location: WeatherLocation,
    val temperature: Double,
    val unit: WeatherUnit,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val observedAtMs: Long,
)
