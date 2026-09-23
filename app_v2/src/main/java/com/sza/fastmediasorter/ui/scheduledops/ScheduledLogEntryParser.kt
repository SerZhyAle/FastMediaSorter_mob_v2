package com.sza.fastmediasorter.ui.scheduledops

/**
 * One parsed run-history line. [raw] always carries the original line, so an entry the parser could
 * not fully read is still shown rather than dropped.
 */
data class ScheduledLogEntry(
    val timestamp: String?,
    val operation: String?,
    val source: String?,
    val target: String?,
    val message: String,
    val isError: Boolean,
    val raw: String,
)

/**
 * Turns the persisted run log (ADR-4: no schema change) into displayable entries. The line format is
 * the one [com.sza.fastmediasorter.domain.usecase.ExecuteScheduledOperationUseCase] writes:
 * `yyyy-MM-dd HH:mm | OP | source → target | message` - the stamp is Locale.US by contract (S2598),
 * and the message itself starts with `OK` or `ERROR` so success never rides on color alone. Lines
 * that do not match the shape come back as raw entries with [ScheduledLogEntry.isError] false.
 */
object ScheduledLogEntryParser {

    private const val FIELD_SEPARATOR = " | "
    private const val SIDE_SEPARATOR = " → "
    private const val MIN_FIELD_COUNT = 4
    private const val SIDE_FIELD_COUNT = 2
    private const val MESSAGE_FIELD_INDEX = 3

    fun parse(text: String): List<ScheduledLogEntry> =
        text.lines()
            .filter { it.isNotBlank() }
            .map { parseLine(it) }

    private fun parseLine(line: String): ScheduledLogEntry {
        val fields = line.split(FIELD_SEPARATOR)
        val timestamp = fields.getOrNull(0)?.takeIf { TS_REGEX.matches(it) }
        val operation = fields.getOrNull(1)?.takeIf { it in OPERATIONS }
        val sides = fields.getOrNull(2)?.split(SIDE_SEPARATOR)
        val message = fields.drop(MESSAGE_FIELD_INDEX).joinToString(FIELD_SEPARATOR)
        val wellFormed = timestamp != null && operation != null &&
            sides != null && sides.size >= SIDE_FIELD_COUNT && fields.size >= MIN_FIELD_COUNT
        return if (!wellFormed) {
            rawEntry(line)
        } else {
            ScheduledLogEntry(
                timestamp = timestamp,
                operation = operation,
                source = sides?.getOrNull(0),
                target = sides?.getOrNull(1),
                message = message,
                isError = message.startsWith("ERROR"),
                raw = line,
            )
        }
    }

    private fun rawEntry(line: String) = ScheduledLogEntry(
        timestamp = null,
        operation = null,
        source = null,
        target = null,
        message = line,
        isError = false,
        raw = line,
    )

    private val OPERATIONS = setOf("COPY", "MOVE", "DELETE")
    private val TS_REGEX = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")
}
