package com.sza.fastmediasorter.ui.stopwatch.helpers

import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchParticipant
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchScreenState

/**
 * The localised words [StopwatchResultRenderer] needs.
 *
 * Resolved by the caller from resources so the renderer itself never touches a Context and can be
 * exercised as plain Kotlin, which is what makes the screen-versus-file agreement testable at all.
 */
class StopwatchResultLabels(
    val participant: (Int) -> String,
    val laps: String,
    val measuredAt: ((Long) -> String)? = null,
)

/**
 * Turns a measurement into the plain ungrouped text of S1411 §2.7.
 *
 * The description and the note come first and are omitted entirely when empty rather than left as blank
 * lines, then one line per visible participant carrying its total and its splits. No table, no rules
 * drawn from characters, no grouping - §5.3 keeps the format textual while leaving the field set free to
 * change, so the field list lives here instead of at each call site.
 *
 * Every time is produced by [StopwatchTimeFormatter], the same object the screen paints with, so the
 * exported file cannot disagree with the reading the user exported it from (§11.7).
 */
object StopwatchResultRenderer {

    fun render(
        state: StopwatchScreenState,
        nowMillis: Long,
        description: String,
        note: String,
        labels: StopwatchResultLabels,
    ): String {
        val lines = mutableListOf<String>()
        description.trim().takeIf { it.isNotEmpty() }?.let { lines.add(it) }
        note.trim().takeIf { it.isNotEmpty() }?.let { lines.add(it) }
        // The earliest visible start is the session's stamp: with Start all every participant began
        // together, and with staggered starts the first beginning is when the measurement was taken
        // (S2792).
        val stamp = state.visibleParticipants.mapNotNull { it.startedAtEpochMillis }.minOrNull()
        if (stamp != null) {
            labels.measuredAt?.invoke(stamp)?.let { lines.add(it) }
        }
        state.visibleParticipants.forEachIndexed { index, participant ->
            lines.add(participantLine(participant, index, nowMillis, labels))
        }
        return lines.joinToString(separator = LINE_BREAK)
    }

    private fun participantLine(
        participant: StopwatchParticipant,
        index: Int,
        nowMillis: Long,
        labels: StopwatchResultLabels,
    ): String {
        val total = StopwatchTimeFormatter.format(participant.elapsedAt(nowMillis))
        val head = "${labels.participant(index)}: $total"
        if (participant.laps.isEmpty()) return head
        val laps = participant.laps.joinToString(separator = LAP_SEPARATOR) { lap ->
            StopwatchTimeFormatter.format(lap.atElapsedMillis)
        }
        return "$head (${labels.laps} $laps)"
    }

    private const val LINE_BREAK = "\n"
    private const val LAP_SEPARATOR = ", "
}
