package com.sza.fastmediasorter.wear.domain.model

/**
 * S3113: where a blood-pressure history value came from.
 *
 * Stored by name, so the order of the constants carries no meaning and a new one never renumbers the rest.
 */
enum class BloodPressureSource {

    /** Typed on the form before calibration existed; every row written by a version-1 database. */
    MANUAL,

    /** A cuff reading entered on the calibration screen together with the pulse wave captured during it. */
    CALIBRATION,

    /** Computed from the pulse wave by the calibration model - never a measurement. */
    ESTIMATE;

    companion object {

        /** An unknown name reads as [MANUAL], so a row written by a later build still opens in this one. */
        fun fromStored(name: String): BloodPressureSource = entries.firstOrNull { it.name == name } ?: MANUAL
    }
}
