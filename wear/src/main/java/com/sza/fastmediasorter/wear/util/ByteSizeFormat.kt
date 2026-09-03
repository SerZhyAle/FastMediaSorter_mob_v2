package com.sza.fastmediasorter.wear.util

import timber.log.Timber
import java.util.Locale

private const val BYTES_PER_UNIT = 1024.0

/** The binary size units this module prints, ordered by how many 1024 steps separate them from bytes. */
internal enum class ByteSizeUnit(val suffix: String) {
    BYTES("B"),
    KILOBYTES("KB"),
    MEGABYTES("MB"),
    GIGABYTES("GB")
}

/** A byte count already reduced to the number to print and the unit that names it. */
internal data class ByteSizeAmount(val amount: Double, val unit: ByteSizeUnit)

/**
 * How one surface prints a size: the narrowest and widest unit it is willing to show, and how many
 * decimals each unit gets.
 *
 * S2433 ADR-1: the ladder is shared but the precision is not. S2439 ADR (owner, 2026-09-03): the
 * four profiles stay separate on purpose - each surface measures a different quantity, and no single
 * ladder reads correctly on all of them, so collapsing them would change what the user reads on at
 * least two screens for no gain in accuracy.
 */
internal data class ByteSizeStyle(
    val minUnit: ByteSizeUnit,
    val maxUnit: ByteSizeUnit,
    val decimals: Map<ByteSizeUnit, Int>
) {
    fun decimalsFor(unit: ByteSizeUnit): Int = decimals[unit] ?: 0
}

/**
 * Reduce [bytes] to an amount within [minUnit]..[maxUnit].
 *
 * [minUnit] exists for the system-info report, which reads in megabytes even for a value a kilobyte
 * wide, because a free-memory figure below a megabyte says nothing worth a unit of its own.
 */
internal fun byteSizeAmount(
    bytes: Long,
    minUnit: ByteSizeUnit,
    maxUnit: ByteSizeUnit
): ByteSizeAmount {
    var amount = bytes.toDouble()
    var index = ByteSizeUnit.BYTES.ordinal
    while (index < minUnit.ordinal) {
        amount /= BYTES_PER_UNIT
        index++
    }
    while (amount >= BYTES_PER_UNIT && index < maxUnit.ordinal) {
        amount /= BYTES_PER_UNIT
        index++
    }
    return ByteSizeAmount(amount, ByteSizeUnit.entries[index])
}

/**
 * A finished `<number> <unit>` string.
 *
 * [Locale.US] is the default because a size printed next to a measurement is a reading rather than
 * prose, and letting the decimal separator follow the watch would make the same value print two ways
 * on two watches. A surface whose size sits inside translated text passes its own locale.
 */
internal fun formatByteSize(
    bytes: Long,
    style: ByteSizeStyle,
    locale: Locale = Locale.US
): String {
    val (amount, unit) = byteSizeAmount(bytes, style.minUnit, style.maxUnit)
    val formatted = String.format(locale, "%.${style.decimalsFor(unit)}f %s", amount, unit.suffix)
    Timber.d("S2433: shared ladder formatted %d bytes as %s", bytes, formatted)
    return formatted
}
