package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

// S4152: keys pinned - both payloads cross the module boundary, and the phone is the minified side.

/**
 * S4152: the watch Tourist asking for the weather. The position is already rounded to about a kilometre on
 * the watch; [requestedAtMs] exists so that a repeated request at the same place is still a changed item.
 */
data class WearTouristWeatherRequestPayload(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("requestedAtMs") val requestedAtMs: Long,
)

/**
 * S4152: the weather details the watch Tourist shows. Units are fixed on the wire - Celsius, km/h, metres -
 * and converted on the watch by its own unit system. A null field is a value the provider did not send.
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
