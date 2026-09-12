package com.sza.fastmediasorter.wear.domain.stopwatch

/**
 * The localised words [WearStopwatchResultRenderer] needs.
 *
 * Resolved by the caller from resources so the renderer never touches a Context and can be exercised as
 * plain Kotlin, which is what makes the agreement between the screen and the result page testable.
 */
class WearStopwatchResultLabels(
    val participant: (Int) -> String,
    val lap: (Int) -> String
)

/**
 * Turns a measurement into the plain text the result page shows and the store keeps.
 *
 * One heading line per participant carrying its total, then one line per lap carrying its split and its
 * running total. No table and no characters drawn as rules: the watch reflows this text at whatever width
 * the display has, and anything aligned by spaces would break there first.
 */
object WearStopwatchResultRenderer {

    fun render(
        state: WearStopwatchState,
        nowMillis: Long,
        labels: WearStopwatchResultLabels
    ): String {
        val lines = mutableListOf<String>()
        state.participants.forEach { participant ->
            val total = WearStopwatchTimeFormatter.format(participant.elapsedAt(nowMillis))
            lines.add("${labels.participant(participant.index + 1)}: $total")
            participant.laps.forEach { lap ->
                val split = WearStopwatchTimeFormatter.format(lap.splitMillis)
                val lapTotal = WearStopwatchTimeFormatter.format(lap.totalMillis)
                lines.add("  ${labels.lap(lap.index)}: $split / $lapTotal")
            }
        }
        return lines.joinToString(separator = LINE_BREAK)
    }

    private const val LINE_BREAK = "\n"
}
