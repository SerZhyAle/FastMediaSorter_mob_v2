package com.sza.fastmediasorter.wear.domain.tourist

import timber.log.Timber

/**
 * S4146: the Tourist trip as one stored string, so it outlives the screen and the process.
 *
 * The format follows `WearStopwatchSnapshot`: a version first, control characters between fields and
 * between pressure samples, plain separators inside a sample. Every instant is wall-clock, so a reboot
 * needs no rebase. An unreadable string or another version decodes to null - the caller starts an
 * empty trip rather than a guessed one.
 */
object WearTripSnapshotCodec {

    const val CURRENT_VERSION = 1

    fun encode(snapshot: WearTripSnapshot): String = listOf(
        CURRENT_VERSION.toString(),
        snapshot.startedAtMillis?.toString().orEmpty(),
        snapshot.lastSampleAtMillis?.toString().orEmpty(),
        snapshot.movingMillis.toString(),
        snapshot.distanceMeters.toString(),
        snapshot.gainMeters.toString(),
        snapshot.lossMeters.toString(),
        snapshot.maxSpeedKmh.toString(),
        snapshot.elevationSource?.name.orEmpty(),
        snapshot.lastElevationMeters?.toString().orEmpty(),
        snapshot.pressureSamples.joinToString(RECORD_SEPARATOR) { "${it.atMillis}$PART_SEPARATOR${it.hectopascals}" },
    ).joinToString(FIELD_SEPARATOR)

    fun decode(raw: String?): WearTripSnapshot? {
        val fields = raw?.split(FIELD_SEPARATOR)
        val readable = fields != null &&
            fields.size == FIELD_COUNT &&
            fields[FIELD_VERSION].toIntOrNull() == CURRENT_VERSION
        return if (!readable) null else runCatchingFormat { decodeFields(requireNotNull(fields)) }
    }

    private fun decodeFields(fields: List<String>): WearTripSnapshot = WearTripSnapshot(
        startedAtMillis = fields[FIELD_STARTED].nullableLong(),
        lastSampleAtMillis = fields[FIELD_LAST_SAMPLE].nullableLong(),
        movingMillis = fields[FIELD_MOVING].toLong(),
        distanceMeters = fields[FIELD_DISTANCE].toDouble(),
        gainMeters = fields[FIELD_GAIN].toDouble(),
        lossMeters = fields[FIELD_LOSS].toDouble(),
        maxSpeedKmh = fields[FIELD_MAX_SPEED].toFloat(),
        elevationSource = fields[FIELD_SOURCE].takeIf { it.isNotEmpty() }?.let { WearElevationSource.valueOf(it) },
        lastElevationMeters = fields[FIELD_LAST_ELEVATION].takeIf { it.isNotEmpty() }?.toDouble(),
        pressureSamples = fields[FIELD_SAMPLES].split(RECORD_SEPARATOR)
            .filter { it.isNotEmpty() }
            .map { record ->
                val parts = record.split(PART_SEPARATOR)
                require(parts.size == SAMPLE_PARTS)
                WearPressureSample(atMillis = parts[0].toLong(), hectopascals = parts[1].toFloat())
            },
    )

    private fun String.nullableLong(): Long? = takeIf { it.isNotEmpty() }?.toLong()

    // A malformed number or an unknown enum name is a corrupt store, answered the same way as a
    // wrong version: no trip rather than a half-read one.
    private inline fun runCatchingFormat(block: () -> WearTripSnapshot): WearTripSnapshot? = try {
        block()
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "Stored Tourist trip is unreadable; starting an empty trip")
        null
    }

    private const val FIELD_SEPARATOR = "\u001F"
    private const val RECORD_SEPARATOR = "\u001E"
    private const val PART_SEPARATOR = ","
    private const val FIELD_COUNT = 11
    private const val SAMPLE_PARTS = 2
    private const val FIELD_VERSION = 0
    private const val FIELD_STARTED = 1
    private const val FIELD_LAST_SAMPLE = 2
    private const val FIELD_MOVING = 3
    private const val FIELD_DISTANCE = 4
    private const val FIELD_GAIN = 5
    private const val FIELD_LOSS = 6
    private const val FIELD_MAX_SPEED = 7
    private const val FIELD_SOURCE = 8
    private const val FIELD_LAST_ELEVATION = 9
    private const val FIELD_SAMPLES = 10
}
