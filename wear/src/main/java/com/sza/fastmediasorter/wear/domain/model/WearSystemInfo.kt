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
 */
data class WearSystemInfoSection(
    @StringRes val titleRes: Int,
    val fields: List<WearSystemInfoField>
)
