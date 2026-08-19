package com.sza.fastmediasorter.wear.domain.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private const val ZERO = "0"
private const val MINUS = "-"
private const val DECIMAL_POINT = "."
private const val MAX_INPUT_LENGTH = 12
private const val PRECISION_DIGITS = 16

private val MATH_CONTEXT = MathContext(PRECISION_DIGITS, RoundingMode.HALF_UP)

/**
 * The watch calculator's arithmetic, kept away from the screen so it can be tested without a device.
 *
 * Every value is a [BigDecimal]: the phone's calculator advertises exact decimal arithmetic, and a
 * `Double` would make 0.1 + 0.2 read as 0.30000000000000004 on one device and 0.3 on the other for
 * the same key sequence.
 *
 * An undefined result - division by zero, an argument outside a function's domain - puts the engine
 * into [isError] rather than throwing: a watch program that dies mid-expression loses the whole
 * session, while an error display is one key away from recoverable.
 */
class WearCalculatorEngine {

    enum class Operator(val symbol: String) {
        PLUS("+"),
        MINUS("-"),
        TIMES("×"),
        DIVIDE("÷");

        companion object {
            fun from(symbol: String): Operator? = entries.firstOrNull { it.symbol == symbol }
        }
    }

    var isError: Boolean = false
        private set

    /** The completed expression of the last `=`, for the history. Null until one is evaluated. */
    var lastExpression: String? = null
        private set

    private var display: String = ZERO
    private var accumulator: BigDecimal? = null
    private var pending: Operator? = null
    private var startsNewNumber: Boolean = true

    fun display(): String = display

    fun inputDigit(digit: Int): String {
        clearErrorState()
        val current = if (startsNewNumber) "" else display.takeIf { it != ZERO }.orEmpty()
        if (current.replace(MINUS, "").replace(DECIMAL_POINT, "").length >= MAX_INPUT_LENGTH) {
            return display
        }
        display = current + digit
        startsNewNumber = false
        return display
    }

    fun inputDecimal(): String {
        clearErrorState()
        if (startsNewNumber) {
            display = ZERO + DECIMAL_POINT
            startsNewNumber = false
        } else if (!display.contains(DECIMAL_POINT)) {
            display += DECIMAL_POINT
        }
        return display
    }

    fun inputOperator(symbol: String): String {
        val operator = Operator.from(symbol) ?: return display
        clearErrorState()
        // A pending operator is applied first, so 2 + 3 × 4 evaluates left to right the way the phone
        // does, rather than silently discarding the earlier operand.
        if (pending != null && !startsNewNumber) {
            evaluatePending()
        } else {
            accumulator = currentValue()
        }
        pending = operator
        startsNewNumber = true
        return display
    }

    fun inputEquals(): String {
        val operator = pending
        val left = accumulator
        if (operator == null || left == null) {
            lastExpression = null
            return display
        }
        val expression = "${format(left)} ${operator.symbol} $display"
        evaluatePending()
        pending = null
        startsNewNumber = true
        lastExpression = if (isError) null else expression
        return display
    }

    fun apply(function: WearCalculatorFunction): String {
        clearErrorState()
        // PI is the one entry that ignores the display: it produces a constant rather than
        // transforming what is on screen.
        val argument = if (function == WearCalculatorFunction.PI) BigDecimal.ZERO else currentValue()
        return setResult(evaluateFunction(function, argument))
    }

    fun toggleSign(): String {
        clearErrorState()
        display = when {
            display == ZERO -> display
            display.startsWith(MINUS) -> display.removePrefix(MINUS)
            else -> MINUS + display
        }
        return display
    }

    fun backspace(): String {
        clearErrorState()
        display = if (display.length <= 1 || (display.length == 2 && display.startsWith(MINUS))) {
            ZERO
        } else {
            display.dropLast(1)
        }
        startsNewNumber = display == ZERO
        return display
    }

    fun clear(): String {
        display = ZERO
        accumulator = null
        pending = null
        startsNewNumber = true
        isError = false
        lastExpression = null
        return display
    }

    fun clearEntry(): String {
        clearErrorState()
        display = ZERO
        startsNewNumber = true
        return display
    }

    /** Replaces the display with [value], or raises the error state when the value is undefined. */
    internal fun setResult(value: BigDecimal?): String {
        display = if (value == null) {
            isError = true
            ZERO
        } else {
            format(value)
        }
        startsNewNumber = true
        return display
    }

    internal fun currentValue(): BigDecimal = display.toBigDecimalOrNull() ?: BigDecimal.ZERO

    private fun evaluatePending() {
        val left = accumulator ?: BigDecimal.ZERO
        val right = currentValue()
        val result = compute(left, right, pending)
        accumulator = result
        setResult(result)
    }

    private fun compute(left: BigDecimal, right: BigDecimal, operator: Operator?): BigDecimal? =
        when (operator) {
            Operator.PLUS -> left.add(right, MATH_CONTEXT)
            Operator.MINUS -> left.subtract(right, MATH_CONTEXT)
            Operator.TIMES -> left.multiply(right, MATH_CONTEXT)
            // Division by zero has no value to show, so it becomes the error state rather than an
            // ArithmeticException that would take the program down.
            Operator.DIVIDE -> if (right.signum() == 0) null else left.divide(right, MATH_CONTEXT)
            null -> right
        }

    private fun clearErrorState() {
        if (isError) {
            clear()
        }
    }

    private fun format(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
}
