package com.sza.fastmediasorter.wear.domain.model

/**
 * S4150: the Tourist heart rate and body temperature sent to the phone.
 *
 * Both fields are nullable with no default: a missing sensor or permission must reach the phone as
 * "no reading", never as a zero it would show.
 */
data class WearTouristHealthPayload(
    val heartRateBpm: Int?,
    val bodyTemperatureCelsius: Float?,
)
