package com.sza.fastmediasorter.wear.domain.stopwatch

/** One reading of the three clocks a stored measurement needs before it can be trusted again. */
data class WearStopwatchClockReading(
    val elapsedRealtimeMillis: Long,
    val wallClockMillis: Long,
    val bootCount: Int
)

/**
 * S3555: the measurement as one stored string, so it outlives the screen and the process.
 *
 * The watch face indicator, the Recents chip and the tile all survive both, and each of them opens the
 * stopwatch - a measurement that died with the process would make every one of them open a reset
 * stopwatch. The format follows `GameStateSnapshot`: control characters between fields and records,
 * plain separators inside them, and an unreadable string decodes to null rather than to a guess.
 */
object WearStopwatchSnapshot {

    const val CURRENT_VERSION = 1

    fun encode(state: WearStopwatchState, at: WearStopwatchClockReading): String {
        val header = listOf(
            CURRENT_VERSION.toLong(),
            at.elapsedRealtimeMillis,
            at.wallClockMillis,
            at.bootCount.toLong()
        )
            .joinToString(PART_SEPARATOR)
        val participants = state.participants.joinToString(RECORD_SEPARATOR) { encodeParticipant(it) }
        return listOf(header, participants).joinToString(FIELD_SEPARATOR)
    }

    /**
     * Reads back what [encode] wrote at an earlier [WearStopwatchClockReading], judged against [now].
     *
     * A start instant is stored on the monotonic clock, which restarts at every boot; when the boot count
     * moved, each running start is carried over through the wall clock so the reading keeps counting from
     * the real start instead of from a meaningless number.
     */
    fun decode(raw: String?, now: WearStopwatchClockReading): WearStopwatchState? {
        val fields = raw?.split(FIELD_SEPARATOR)?.takeIf { it.size == FIELD_COUNT }
        val saved = fields?.let { decodeHeader(it[FIELD_HEADER]) }
        val participants = fields?.let { decodeParticipants(it[FIELD_PARTICIPANTS]) }
        return if (saved == null || participants.isNullOrEmpty()) {
            null
        } else {
            WearStopwatchState(
                participants.map { if (saved.bootCount == now.bootCount) it else it.rebased(saved, now) }
            )
        }
    }

    private fun encodeParticipant(participant: WearStopwatchParticipant): String = listOf(
        participant.index.toString(),
        participant.startedAtMillis?.toString().orEmpty(),
        participant.accumulatedMillis.toString(),
        participant.laps.joinToString(LAP_SEPARATOR) { lap ->
            listOf(lap.index.toLong(), lap.totalMillis, lap.splitMillis).joinToString(LAP_PART_SEPARATOR)
        }
    ).joinToString(PART_SEPARATOR)

    private fun decodeHeader(field: String): WearStopwatchClockReading? {
        val parts = field.split(PART_SEPARATOR)
        val numbers = parts.mapNotNull { it.toLongOrNull() }
        val readable = parts.size == HEADER_PARTS && numbers.size == HEADER_PARTS
        return if (!readable || numbers[HEADER_VERSION] != CURRENT_VERSION.toLong()) {
            null
        } else {
            WearStopwatchClockReading(
                elapsedRealtimeMillis = numbers[HEADER_ELAPSED],
                wallClockMillis = numbers[HEADER_WALL],
                bootCount = numbers[HEADER_BOOT].toInt()
            )
        }
    }

    private fun decodeParticipants(field: String): List<WearStopwatchParticipant>? {
        val decoded = field.split(RECORD_SEPARATOR).map(::decodeParticipant)
        return if (decoded.any { it == null }) null else decoded.filterNotNull()
    }

    private fun decodeParticipant(record: String): WearStopwatchParticipant? {
        val parts = record.split(PART_SEPARATOR).takeIf { it.size == PARTICIPANT_PARTS } ?: return null
        val indexAndAccumulated = parts[PARTICIPANT_INDEX].toIntOrNull()?.let { index ->
            parts[PARTICIPANT_ACCUMULATED].toLongOrNull()?.let { accumulated -> index to accumulated }
        }
        val startedAt = parts[PARTICIPANT_STARTED].toLongOrNull()
        val startReadable = startedAt != null || parts[PARTICIPANT_STARTED].isEmpty()
        val laps = decodeLaps(parts[PARTICIPANT_LAPS])
        return if (indexAndAccumulated == null || laps == null || !startReadable) {
            null
        } else {
            WearStopwatchParticipant(
                index = indexAndAccumulated.first,
                startedAtMillis = startedAt,
                accumulatedMillis = indexAndAccumulated.second,
                laps = laps
            )
        }
    }

    private fun decodeLaps(field: String): List<WearStopwatchLap>? {
        val decoded = if (field.isEmpty()) emptyList() else field.split(LAP_SEPARATOR).map(::decodeLap)
        return if (decoded.any { it == null }) null else decoded.filterNotNull()
    }

    private fun decodeLap(record: String): WearStopwatchLap? {
        val parts = record.split(LAP_PART_SEPARATOR)
        val numbers = parts.mapNotNull { it.toLongOrNull() }
        return if (parts.size != LAP_PARTS || numbers.size != LAP_PARTS) {
            null
        } else {
            WearStopwatchLap(
                index = numbers[LAP_INDEX].toInt(),
                totalMillis = numbers[LAP_TOTAL],
                splitMillis = numbers[LAP_SPLIT]
            )
        }
    }

    /** The saved wall clock pins when the participant really started; that instant moves onto [now]'s boot. */
    private fun WearStopwatchParticipant.rebased(
        saved: WearStopwatchClockReading,
        now: WearStopwatchClockReading
    ): WearStopwatchParticipant {
        val started = startedAtMillis ?: return this
        val startedWall = saved.wallClockMillis - (saved.elapsedRealtimeMillis - started)
        val runningFor = (now.wallClockMillis - startedWall).coerceAtLeast(0L)
        return copy(startedAtMillis = now.elapsedRealtimeMillis - runningFor)
    }

    private const val FIELD_SEPARATOR = "\u001F"
    private const val RECORD_SEPARATOR = "\u001E"
    private const val PART_SEPARATOR = ","
    private const val LAP_SEPARATOR = ";"
    private const val LAP_PART_SEPARATOR = ":"

    private const val FIELD_COUNT = 2
    private const val FIELD_HEADER = 0
    private const val FIELD_PARTICIPANTS = 1

    private const val HEADER_PARTS = 4
    private const val HEADER_VERSION = 0
    private const val HEADER_ELAPSED = 1
    private const val HEADER_WALL = 2
    private const val HEADER_BOOT = 3

    private const val PARTICIPANT_PARTS = 4
    private const val PARTICIPANT_INDEX = 0
    private const val PARTICIPANT_STARTED = 1
    private const val PARTICIPANT_ACCUMULATED = 2
    private const val PARTICIPANT_LAPS = 3

    private const val LAP_PARTS = 3
    private const val LAP_INDEX = 0
    private const val LAP_TOTAL = 1
    private const val LAP_SPLIT = 2
}
