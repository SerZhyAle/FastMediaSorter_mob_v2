package com.sza.fastmediasorter.domain.model.networkmonitor

/**
 * S1433: which satellite system a signal came from.
 *
 * Modelled as a closed set rather than passed through as the platform's integer, so a section can label a
 * satellite without owning a mapping table, and an unknown system stays [UNKNOWN] instead of silently
 * becoming GPS.
 */
enum class GnssConstellation { GPS, GLONASS, GALILEO, BEIDOU, QZSS, SBAS, IRNSS, UNKNOWN }

/**
 * S1433: one satellite the receiver can currently see.
 *
 * [cn0DbHz] is null for a satellite the receiver lists but has not decoded a signal from - the platform
 * reports that case as a flat zero, and a zero drawn on a chart reads as "in view, dead signal" rather than
 * "no measurement yet". Elevation and azimuth carry no such ambiguity: zero degrees is a real position on the
 * horizon and is kept as the number it is.
 */
data class GnssSatellite(
    val constellation: GnssConstellation,
    val satelliteId: Int,
    val cn0DbHz: Double?,
    val usedInFix: Boolean,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
)

/**
 * S1433: the position the receiver currently reports.
 *
 * Kept out of [GnssSnapshot.satellites] rather than merged into it: satellite diagnostics answer "is the
 * receiver working", the coordinate answers "where am I", and only the second is location data the user may
 * not want on screen. Separating them lets a section draw the constellation without ever touching a position.
 */
data class GnssCoordinate(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val accuracyMeters: Float?,
    val fixedAtMillis: Long,
)

/**
 * S1433: what the GNSS section shows at one moment.
 *
 * [coordinate] is null until the receiver has a fix, and [coordinateAvailability] says why. The section as a
 * whole carries its own availability through the [MonitorSection] wrapper the data source emits: the two are
 * different questions, because a receiver can be reporting satellites perfectly while still holding no fix.
 */
data class GnssSnapshot(
    val satellites: List<GnssSatellite>,
    val coordinate: GnssCoordinate?,
    val coordinateAvailability: SectionAvailability,
    val takenAtMillis: Long,
) {

    /** How many satellites the receiver can see at all. */
    val visibleCount: Int get() = satellites.size

    /** How many of them the current position was actually computed from. */
    val usedInFixCount: Int get() = satellites.count { it.usedInFix }

    /**
     * Mean C/N0 across the satellites that reported one, or null while none has.
     *
     * Satellites without a reading are excluded rather than counted as zero, so the average describes signal
     * quality instead of dropping every time a new satellite appears.
     */
    val meanCn0DbHz: Double?
        get() = satellites.mapNotNull { it.cn0DbHz }.takeIf { it.isNotEmpty() }?.average()

    companion object {

        /** The snapshot of a receiver that is reporting nothing yet. */
        fun empty(takenAtMillis: Long): GnssSnapshot = GnssSnapshot(
            satellites = emptyList(),
            coordinate = null,
            coordinateAvailability = SectionAvailability.NoNetwork,
            takenAtMillis = takenAtMillis,
        )
    }
}
