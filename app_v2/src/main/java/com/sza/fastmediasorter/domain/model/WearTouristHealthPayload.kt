package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

// S4150: keys pinned - this one travels watch to phone, and the phone is the minified side here.

/**
 * S4150: the watch Tourist heart rate and body temperature. Null means the watch had no reading, so the
 * tile shows its hint rather than a zero.
 */
data class WearTouristHealthPayload(
    @SerializedName("heartRateBpm") val heartRateBpm: Int?,
    @SerializedName("bodyTemperatureCelsius") val bodyTemperatureCelsius: Float?,
)
