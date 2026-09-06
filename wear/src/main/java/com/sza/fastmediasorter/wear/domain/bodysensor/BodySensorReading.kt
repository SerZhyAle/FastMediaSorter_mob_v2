package com.sza.fastmediasorter.wear.domain.bodysensor

/**
 * Why a heart-rate reading cannot be taken right now.
 *
 * The three refusals strategic S2457 §11 criterion 1 separates - [NO_HARDWARE], [PERMISSION_DENIED] and
 * [PLATFORM_TOO_OLD] - are distinct values precisely because each one gets its own sentence on screen.
 * A single merged "unavailable" would force the screen to guess which sentence to print, and from the
 * UI's side the guess is unguessable: a watch with no sensor and a watch whose permission the user
 * declined look identical unless the domain has already told them apart.
 *
 * Every value here is a refusal. "Nothing asked for yet" is [BodySensorReading.Idle], not a reason.
 */
enum class BodySensorUnavailableReason {

    /**
     * The store build withholds the way in. Play reviews both heart-rate permissions against six admitted
     * use cases and a media sorter matches none, so the capability ships in `noLegal` alone (S2457 ADR-1).
     */
    NOT_OFFERED_IN_THIS_BUILD,

    /**
     * Health Services requires API 30 and this module ships to 28, so API 28 and 29 have no path at all.
     * Named rather than folded into [NO_HARDWARE] because the watch may well have the sensor - what is
     * missing is the service that reads it, and telling the user otherwise would be false.
     */
    PLATFORM_TOO_OLD,

    /** Health Services does not list a heart-rate data type on this watch - the sensor is absent. */
    NO_HARDWARE,

    /** The heart-rate permission is not granted. Which permission that is depends on the API level. */
    PERMISSION_DENIED,

    /**
     * The watch reported it is not being worn. Distinct from [MEASUREMENT_TIMED_OUT] because the sensor
     * answered rather than stayed silent, and the fix is the user's to make - a watch on a desk is the
     * ordinary state of a watch under test, and printing "timed out" for it would misdirect the one
     * person who could act.
     */
    SENSOR_OFF_BODY,

    /** No sample arrived before the measurement window closed. */
    MEASUREMENT_TIMED_OUT,

    /** Health Services refused the registration, or failed part-way through the measurement. */
    MEASUREMENT_FAILED
}

/**
 * Everything the body-sensor diagnostic can be showing at one moment - one reading, one refusal, or
 * neither yet.
 *
 * Deliberately not a struct with nullable fields: a value and a reason are mutually exclusive, and a
 * shape that can hold both at once puts the burden of that invariant on every reader.
 */
sealed interface BodySensorReading {

    /** The path is open and no measurement is running. The starting state, and the state after teardown. */
    data object Idle : BodySensorReading

    /** Registered with the sensor, no sample delivered yet. */
    data object Measuring : BodySensorReading

    /** One heart-rate sample, rounded to whole beats per minute for display. */
    data class HeartRate(val beatsPerMinute: Int) : BodySensorReading

    /** No reading, and [reason] selects the sentence the screen owes the user. */
    data class Unavailable(val reason: BodySensorUnavailableReason) : BodySensorReading
}
