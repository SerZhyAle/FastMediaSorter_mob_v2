package com.sza.fastmediasorter.wear.domain.model

import androidx.annotation.StringRes

/**
 * The right-hand side of one report line.
 *
 * Two shapes, because the two kinds of value are produced in different places. A measured fact is
 * already formatted by the collector, which is the only code that knows what unit a number carries. A
 * fixed word like "Connected" cannot be formatted there at all: the collector holds no Context, and
 * holding one would make it untestable on the plain JVM this module runs its unit tests on.
 */
sealed interface WearSystemInfoValue {

    data class Text(val text: String) : WearSystemInfoValue

    data class Label(@StringRes val res: Int) : WearSystemInfoValue

    /**
     * A set the user counts more often than reads - the sensors this watch carries, the capabilities
     * of the pair.
     *
     * Collapsed to its size and expanded on tap. Poured into the report whole it would defeat the
     * two-column layout the report is built on: a modern watch answers several dozen sensors, and
     * every one of them is longer than the fourteen characters that still fit half a row, so the whole
     * report would degenerate into one very long column (S2165 §5.1 pillar D).
     *
     * The collapsed form is the entry count and nothing else, so it needs no plural and no format
     * string: the field's own label already names what is being counted.
     */
    data class Enumerated(val entries: List<String>) : WearSystemInfoValue
}

/** One report line: the name the user reads and the value it names. */
data class WearSystemInfoField(
    @StringRes val labelRes: Int,
    val value: WearSystemInfoValue
)

/**
 * One titled group of [WearSystemInfoField]s.
 *
 * The phone shows its own report in this same shape - a title, then name-value pairs - so a user who has
 * read one screen reads the other without learning anything new. The shape is shared, the code is not:
 * this module cannot see the phone's, and the two devices are worth asking different questions.
 *
 * @param emptyReasonRes why this section has nothing to show, when it has nothing to show. A section
 * that cannot be filled on this watch states the cause instead of disappearing (S2165, taking the form
 * S2156 settled for the network monitor and S2130 established on this watch before it): a panel whose
 * length varies per device is harder to trust than a line saying why a reading is missing. A section
 * with neither fields nor a reason is a collector bug, and the collector drops it rather than drawing an
 * empty heading. A single missing FIELD still just vanishes - explaining every unread line would bury
 * the report in apologies.
 */
data class WearSystemInfoSection(
    @StringRes val titleRes: Int,
    val fields: List<WearSystemInfoField>,
    @StringRes val emptyReasonRes: Int? = null
)
