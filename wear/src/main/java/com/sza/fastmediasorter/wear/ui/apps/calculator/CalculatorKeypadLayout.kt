package com.sza.fastmediasorter.wear.ui.apps.calculator

import androidx.compose.ui.Alignment
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorEngine

/**
 * S2152: one cell of the keypad - the key it carries, how many columns it occupies, and where its
 * label is drawn inside it.
 *
 * The alignment belongs to the position in the row and not to the key (ADR-1). The owner stated the
 * rule through the shape of the glass and through a column number, so pinning it to two named keys
 * would record the consequence rather than the cause, and the next rearrangement of the keypad would
 * bring the problem back with nobody having touched this rule.
 */
internal data class CalculatorCell(
    val key: CalculatorKey,
    val span: Int = SINGLE_COLUMN,
    val labelAlignment: Alignment = Alignment.Center
)

/** A cell that occupies exactly one keypad column, which is what every key but one does. */
internal const val SINGLE_COLUMN = 1

/**
 * S2152: the round display crops the outer part of an end-column key, and the label sits in exactly
 * the part that is cropped.
 *
 * So the first column draws its label against its own end edge and the last column against its start
 * edge - both of which point at the middle of the glass - while every column between them keeps the
 * centre it already had, because nothing crops them.
 */
private fun labelAlignmentFor(index: Int, lastIndex: Int): Alignment = when (index) {
    0 -> Alignment.CenterEnd
    lastIndex -> Alignment.CenterStart
    else -> Alignment.Center
}

private fun CalculatorKey.cell(span: Int = SINGLE_COLUMN): CalculatorCell =
    CalculatorCell(key = this, span = span)

private fun List<CalculatorCell>.hugged(): List<CalculatorCell> =
    mapIndexed { index, cell -> cell.copy(labelAlignment = labelAlignmentFor(index, lastIndex)) }

// S1966: the numbers here ARE the digits the keys carry, so naming each one as a constant would
// rename 7 to SEVEN and explain nothing. This is the case suppression exists for.
@Suppress("MagicNumber")
internal fun keypadRows(): List<List<CalculatorCell>> = listOf(
    listOf(
        CalculatorKey.Digit(7).cell(),
        CalculatorKey.Digit(8).cell(),
        CalculatorKey.Digit(9).cell(),
        CalculatorKey.Operator(WearCalculatorEngine.Operator.DIVIDE).cell()
    ),
    listOf(
        CalculatorKey.Digit(4).cell(),
        CalculatorKey.Digit(5).cell(),
        CalculatorKey.Digit(6).cell(),
        CalculatorKey.Operator(WearCalculatorEngine.Operator.TIMES).cell()
    ),
    listOf(
        CalculatorKey.Digit(1).cell(),
        CalculatorKey.Digit(2).cell(),
        CalculatorKey.Digit(3).cell(),
        CalculatorKey.Operator(WearCalculatorEngine.Operator.MINUS).cell()
    ),
    listOf(
        CalculatorKey.Sign.cell(),
        CalculatorKey.Digit(0).cell(),
        CalculatorKey.Decimal.cell(),
        CalculatorKey.Operator(WearCalculatorEngine.Operator.PLUS).cell()
    ),
    // S2152: clear has left this row, so backspace takes the freed first position, the menu shifts
    // left, and equals spends the remaining two columns on one large target. S2493 then put clear back
    // under the keypad in a row of its own - not here, because the gap that keeps a missed `=` off it
    // is vertical.
    listOf(
        CalculatorKey.Backspace.cell(),
        CalculatorKey.Menu.cell(),
        CalculatorKey.Equals.cell(span = 2)
    )
).map { row -> row.hugged() }
