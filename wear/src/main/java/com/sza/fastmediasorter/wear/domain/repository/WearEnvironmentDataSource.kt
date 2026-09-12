package com.sza.fastmediasorter.wear.domain.repository

/** Which environmental quantity a reading measures. */
enum class WearEnvironmentKind {
    ILLUMINANCE,
    PRESSURE,
    MAGNETIC_FIELD
}

/** How much the platform trusts the sample it just handed over. */
enum class WearReadingAccuracy {
    UNRELIABLE,
    LOW,
    MEDIUM,
    HIGH
}

/**
 * One environmental quantity, as this watch answered for it right now.
 *
 * Three shapes rather than a nullable value, because the report has to tell apart two absences the user
 * reads completely differently: a sensor that is not fitted describes the watch, while a sensor that is
 * fitted and stayed silent describes a fault in it (S2459 §6 item 2).
 */
sealed interface WearEnvironmentReading {

    val kind: WearEnvironmentKind

    /**
     * @param accuracy null when the platform declined to rate the sample. It rates one through
     * `onAccuracyChanged`, which is not guaranteed to fire before the first value arrives, so an
     * unrated reading is normal rather than a fault and is shown as the plain measurement.
     */
    data class Measured(
        override val kind: WearEnvironmentKind,
        val value: Float,
        val accuracy: WearReadingAccuracy?
    ) : WearEnvironmentReading

    /** The sensor is fitted but produced no event before the sampling window closed. */
    data class Silent(override val kind: WearEnvironmentKind) : WearEnvironmentReading

    /** This watch does not carry a sensor of this kind at all. */
    data class Unsupported(override val kind: WearEnvironmentKind) : WearEnvironmentReading
}

/**
 * Reads the environmental sensors the user can check against the world in front of them - cover the
 * watch, compare the pressure with a forecast, bring a magnet near it.
 *
 * Sampling is one bounded burst per call and owns no state between calls: the readings exist only while
 * the diagnostics screen is open (S2459 §3.2), so nothing here survives the suspend function that
 * produced it. Body sensors stay out on the S2013 ground S2165 recorded; these three are public and need
 * no permission.
 */
interface WearEnvironmentDataSource {

    /** Null means the watch would not answer at all, as opposed to answering that it has nothing. */
    suspend fun sample(): List<WearEnvironmentReading>?
}
