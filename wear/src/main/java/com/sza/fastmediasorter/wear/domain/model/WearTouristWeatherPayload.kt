package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.annotations.SerializedName

// S4152: keys pinned and mirrored verbatim from the phone's copy - the two modules share no artifact.

/**
 * S4152: Tourist asking the phone for the weather. The position is rounded to about a kilometre before it
 * leaves; [requestedAtMs] exists so that a repeated request at the same place is still a changed item.
 */
data class WearTouristWeatherRequestPayload(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("requestedAtMs") val requestedAtMs: Long,
)

/**
 * S4152: the phone's weather details for Tourist. Units are fixed on the wire - Celsius, km/h, metres - and
 * converted here by the watch's own unit system. A null field is a value the provider did not send.
 */
data class WearTouristWeatherPayload(
    @SerializedName("observedAtMs") val observedAtMs: Long,
    @SerializedName("apparentTemperatureCelsius") val apparentTemperatureCelsius: Double?,
    @SerializedName("humidityPercent") val humidityPercent: Int?,
    @SerializedName("windSpeedKmh") val windSpeedKmh: Double?,
    @SerializedName("windGustsKmh") val windGustsKmh: Double?,
    @SerializedName("windDirectionDegrees") val windDirectionDegrees: Int?,
    @SerializedName("uvIndex") val uvIndex: Double?,
    @SerializedName("precipitationProbabilityPercent") val precipitationProbabilityPercent: Int?,
    @SerializedName("cloudCoverPercent") val cloudCoverPercent: Int?,
    @SerializedName("visibilityMetres") val visibilityMetres: Double?,
)
