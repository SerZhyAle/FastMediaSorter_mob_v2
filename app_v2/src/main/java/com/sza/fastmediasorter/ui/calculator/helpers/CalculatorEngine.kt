package com.sza.fastmediasorter.ui.calculator.helpers

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

class CalculatorEngine {

    enum class CalculatorError {
        DIVISION_BY_ZERO,
        MATH_DOMAIN
    }

    private enum class Operator(val symbol: String) {
        ADD("+"),
        SUBTRACT("-"),
        MULTIPLY("×"),
        DIVIDE("÷"),
        INTEGER_DIVIDE("DIV"),
        POWER("^"),
        MODULO("mod");

        companion object {
            fun from(symbol: String): Operator = entries.firstOrNull { it.symbol == symbol }
                ?: throw IllegalArgumentException("Unsupported operator: $symbol")
        }
    }

    var display: String = ZERO
        private set

    var operationHistory: String = ""
        private set

    val calculationHistory: List<String>
        get() = completedHistory.toList()

    val calculationHistoryText: String
        get() = completedHistory.joinToString(separator = "\n")

    var error: CalculatorError? = null
        private set

    private var accumulator: BigDecimal? = null
    private var pendingOperator: Operator? = null
    private var repeatOperator: Operator? = null
    private var repeatOperand: BigDecimal? = null
    private var startNewInput: Boolean = true
    private val completedHistory = mutableListOf<String>()

    var memory: BigDecimal = BigDecimal.ZERO
        private set

    val memoryDisplay: String
        get() = format(memory)

    fun inputDigit(digit: Int): String {
        require(digit in 0..9) { "Digit must be between 0 and 9." }
        clearErrorIfNeeded()
        clearRepeatIfEditingStandaloneValue()
        display = when {
            startNewInput -> digit.toString()
            display == ZERO -> digit.toString()
            display == NEGATIVE_ZERO -> "-$digit"
            else -> display + digit
        }
        startNewInput = false
        normalizeNegativeZero()
        updatePendingHistory()
        return display
    }

    fun inputDecimal(): String {
        clearErrorIfNeeded()
        clearRepeatIfEditingStandaloneValue()
        if (startNewInput) {
            display = "0."
            startNewInput = false
        } else if (!display.contains('.')) {
            display += "."
        }
        updatePendingHistory()
        return display
    }

    fun inputOperator(symbol: String): String {
        val operator = Operator.from(symbol)
        if (error != null) return display
        if (pendingOperator != null && !startNewInput) {
            if (!applyPending()) return display
        } else {
            accumulator = display.toBigDecimalOrNull() ?: BigDecimal.ZERO
        }
        pendingOperator = operator
        repeatOperator = null
        repeatOperand = null
        startNewInput = true
        operationHistory = "${formatForHistory(accumulator ?: BigDecimal.ZERO)}${operator.symbol}"
        return display
    }

    fun inputEquals(): String {
        if (error != null) return display
        val operator = pendingOperator
        if (operator != null) {
            val left = accumulator ?: display.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val right = display.toBigDecimalOrNull() ?: BigDecimal.ZERO
            operationHistory = "${formatForHistory(left)}${operator.symbol}${formatForHistory(right)}="
            if (applyOperation(left, right, operator)) {
                appendCompletedHistoryEntry()
                repeatOperator = operator
                repeatOperand = right
            }
            pendingOperator = null
        } else {
            val repeatOperator = repeatOperator
            val repeatOperand = repeatOperand
            if (repeatOperator != null && repeatOperand != null) {
                val left = display.toBigDecimalOrNull() ?: BigDecimal.ZERO
                operationHistory = "${formatForHistory(left)}${repeatOperator.symbol}${formatForHistory(repeatOperand)}="
                if (applyOperation(left, repeatOperand, repeatOperator)) {
                    appendCompletedHistoryEntry()
                }
            }
        }
        startNewInput = true
        return display
    }

    fun inputPercent(): String {
        clearErrorIfNeeded()
        clearRepeatIfEditingStandaloneValue()
        val value = display.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val percentValue = when (pendingOperator) {
            Operator.ADD,
            Operator.SUBTRACT -> value
                .multiply(accumulator ?: BigDecimal.ZERO)
                .divide(BigDecimal(100), DIVIDE_SCALE, RoundingMode.HALF_UP)
            else -> value.divide(BigDecimal(100), DIVIDE_SCALE, RoundingMode.HALF_UP)
        }
        display = format(percentValue)
        startNewInput = false
        updatePendingHistory()
        return display
    }

    fun inputNumber(text: String): String {
        clearErrorIfNeeded()
        clearRepeatIfEditingStandaloneValue()
        return when (val result = CalculatorExpressionEvaluator.evaluate(text)) {
            is CalculatorExpressionEvaluator.Result.Success -> applyEvaluated(result)
            is CalculatorExpressionEvaluator.Result.Failure -> applyEvaluationFailure(result.kind)
        }
    }

    fun canParseInput(text: String): Boolean =
        CalculatorExpressionEvaluator.evaluate(text) is CalculatorExpressionEvaluator.Result.Success

    private fun applyEvaluated(result: CalculatorExpressionEvaluator.Result.Success): String {
        if (!result.isExpression) {
            // Single bare number: behave like a fresh entry within any pending operation.
            display = format(result.value)
            startNewInput = false
            updatePendingHistory()
            return display
        }
        display = format(result.value)
        accumulator = result.value
        pendingOperator = null
        repeatOperator = null
        repeatOperand = null
        operationHistory = "${result.canonical}="
        appendCompletedHistoryEntry()
        startNewInput = true
        return display
    }

    private fun applyEvaluationFailure(kind: CalculatorExpressionEvaluator.Result.Kind): String {
        when (kind) {
            CalculatorExpressionEvaluator.Result.Kind.DIVIDE_BY_ZERO -> {
                error = CalculatorError.DIVISION_BY_ZERO
                resetAfterError()
            }
            CalculatorExpressionEvaluator.Result.Kind.DOMAIN -> {
                error = CalculatorError.MATH_DOMAIN
                resetAfterError()
            }
            CalculatorExpressionEvaluator.Result.Kind.PARSE -> Unit
        }
        return display
    }

    private fun resetAfterError() {
        display = ZERO
        accumulator = null
        pendingOperator = null
        repeatOperator = null
        repeatOperand = null
        startNewInput = true
        operationHistory = ""
    }

    fun roundDisplay(): String {
        clearErrorIfNeeded()
        clearRepeatIfEditingStandaloneValue()
        val value = display.toBigDecimalOrNull() ?: BigDecimal.ZERO
        display = format(value.setScale(0, RoundingMode.HALF_UP))
        startNewInput = false
        updatePendingHistory()
        return display
    }

    fun sine(): String = applyUnary({ arg -> "sin($arg)" }) { deg -> degTrig(deg, Math::sin) }

    fun cosine(): String = applyUnary({ arg -> "cos($arg)" }) { deg -> degTrig(deg, Math::cos) }

    fun tangent(): String = applyUnary({ arg -> "tg($arg)" }) { deg ->
        val rad = Math.toRadians(deg.toDouble())
        if (Math.abs(Math.cos(rad)) < TRIG_EPSILON) null else BigDecimal.valueOf(Math.tan(rad))
    }

    fun cotangent(): String = applyUnary({ arg -> "ctg($arg)" }) { deg ->
        val rad = Math.toRadians(deg.toDouble())
        if (Math.abs(Math.sin(rad)) < TRIG_EPSILON) null else BigDecimal.valueOf(Math.cos(rad) / Math.sin(rad))
    }

    fun squareRoot(): String = applyUnary({ arg -> "√($arg)" }) { value ->
        if (value.signum() < 0) null else BigDecimal.valueOf(Math.sqrt(value.toDouble()))
    }

    fun cubeRoot(): String = applyUnary({ arg -> "∛($arg)" }) { value ->
        BigDecimal.valueOf(Math.cbrt(value.toDouble()))
    }

    fun square(): String = applyUnary({ arg -> "$arg²" }) { value -> value.multiply(value) }

    fun reciprocal(): String = applyUnary({ arg -> "1/($arg)" }) { value ->
        if (value.signum() == 0) null else BigDecimal.ONE.divide(value, DIVIDE_SCALE, RoundingMode.HALF_UP)
    }

    fun log10(): String = applyUnary({ arg -> "log($arg)" }) { value ->
        if (value.signum() <= 0) null else BigDecimal.valueOf(Math.log10(value.toDouble()))
    }

    fun naturalLog(): String = applyUnary({ arg -> "ln($arg)" }) { value ->
        if (value.signum() <= 0) null else BigDecimal.valueOf(Math.log(value.toDouble()))
    }

    fun factorial(): String = applyUnary({ arg -> "$arg!" }) { value ->
        val stripped = value.stripTrailingZeros()
        if (value.signum() < 0 || stripped.scale() > 0) {
            null
        } else {
            val n = stripped.toBigIntegerExact()
            if (n > BigInteger.valueOf(MAX_FACTORIAL)) {
                null
            } else {
                var acc = BigInteger.ONE
                var i = BigInteger.valueOf(2)
                while (i <= n) {
                    acc = acc.multiply(i)
                    i = i.add(BigInteger.ONE)
                }
                acc.toBigDecimal()
            }
        }
    }

    fun inputPi(): String {
        clearErrorIfNeeded()
        clearRepeatIfEditingStandaloneValue()
        display = format(BigDecimal.valueOf(Math.PI))
        startNewInput = false
        updatePendingHistory()
        return display
    }

    fun toggleSign(): String {
        clearErrorIfNeeded()
        clearRepeatIfEditingStandaloneValue()
        display = when {
            display == ZERO || display == "0." -> display
            display.startsWith("-") -> display.drop(1)
            else -> "-$display"
        }
        normalizeNegativeZero()
        updatePendingHistory()
        return display
    }

    fun clear(): String {
        accumulator = null
        pendingOperator = null
        repeatOperator = null
        repeatOperand = null
        error = null
        display = ZERO
        operationHistory = ""
        startNewInput = true
        return display
    }

    fun clearEntry(): String {
        error = null
        display = ZERO
        startNewInput = false
        operationHistory = pendingOperator?.let { operator ->
            "${formatForHistory(accumulator ?: BigDecimal.ZERO)}${operator.symbol}"
        }.orEmpty()
        return display
    }

    fun clearHistory() {
        completedHistory.clear()
        operationHistory = ""
    }

    /** Seed the in-memory completed history with previously persisted entries (display/accumulator untouched). */
    fun restoreHistory(entries: List<String>) {
        completedHistory.clear()
        completedHistory.addAll(entries)
    }

    fun memoryAdd(): String {
        memory = memory.add(display.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        return display
    }

    fun memorySubtract(): String {
        memory = memory.subtract(display.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        return display
    }

    fun memoryRecall(): String {
        clearErrorIfNeeded()
        clearRepeatIfEditingStandaloneValue()
        display = format(memory)
        startNewInput = false
        updatePendingHistory()
        return display
    }

    fun memoryClear(): String {
        memory = BigDecimal.ZERO
        return display
    }

    /** Seed the memory register from a persisted value (display/accumulator untouched). */
    fun restoreMemory(value: BigDecimal) {
        memory = value
    }

    fun backspace(): String {
        clearErrorIfNeeded()
        clearRepeatIfEditingStandaloneValue()
        if (startNewInput || display.length <= 1 || display == NEGATIVE_ZERO) {
            display = ZERO
            startNewInput = false
            return display
        }
        display = display.dropLast(1)
        if (display == "-" || display.isEmpty()) display = ZERO
        normalizeNegativeZero()
        updatePendingHistory()
        return display
    }

    private fun applyPending(): Boolean {
        val right = display.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val operator = pendingOperator ?: return true
        val left = accumulator ?: display.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return applyOperation(left, right, operator)
    }

    private fun applyOperation(left: BigDecimal, right: BigDecimal, operator: Operator): Boolean {
        val result = when (operator) {
            Operator.ADD -> left.add(right)
            Operator.SUBTRACT -> left.subtract(right)
            Operator.MULTIPLY -> left.multiply(right)
            Operator.DIVIDE -> {
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    error = CalculatorError.DIVISION_BY_ZERO
                    display = ZERO
                    accumulator = null
                    repeatOperator = null
                    repeatOperand = null
                    startNewInput = true
                    return false
                }
                left.divide(right, DIVIDE_SCALE, RoundingMode.HALF_UP)
            }
            Operator.INTEGER_DIVIDE -> {
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    error = CalculatorError.DIVISION_BY_ZERO
                    display = ZERO
                    accumulator = null
                    repeatOperator = null
                    repeatOperand = null
                    startNewInput = true
                    return false
                }
                left.divideToIntegralValue(right)
            }
            Operator.POWER -> {
                val powered = power(left, right) ?: run {
                    error = CalculatorError.MATH_DOMAIN
                    display = ZERO
                    accumulator = null
                    repeatOperator = null
                    repeatOperand = null
                    startNewInput = true
                    return false
                }
                powered
            }
            Operator.MODULO -> {
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    error = CalculatorError.DIVISION_BY_ZERO
                    display = ZERO
                    accumulator = null
                    repeatOperator = null
                    repeatOperand = null
                    startNewInput = true
                    return false
                }
                left.remainder(right)
            }
        }
        accumulator = result
        display = format(result)
        startNewInput = true
        return true
    }

    private fun applyUnary(
        label: (String) -> String,
        compute: (BigDecimal) -> BigDecimal?,
    ): String {
        clearErrorIfNeeded()
        clearRepeatIfEditingStandaloneValue()
        val value = display.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val argText = formatForHistory(value)
        val result = compute(value)
        if (result == null) {
            error = CalculatorError.MATH_DOMAIN
            display = ZERO
            accumulator = null
            pendingOperator = null
            repeatOperator = null
            repeatOperand = null
            startNewInput = true
            operationHistory = ""
            return display
        }
        display = format(result)
        operationHistory = "${label(argText)}="
        appendCompletedHistoryEntry()
        accumulator = result
        pendingOperator = null
        repeatOperator = null
        repeatOperand = null
        startNewInput = true
        return display
    }

    private fun degTrig(deg: BigDecimal, fn: (Double) -> Double): BigDecimal =
        BigDecimal.valueOf(fn(Math.toRadians(deg.toDouble())))

    private fun power(base: BigDecimal, exponent: BigDecimal): BigDecimal? {
        val strippedExponent = exponent.stripTrailingZeros()
        if (strippedExponent.scale() <= 0) {
            val intExponent = strippedExponent.toBigIntegerExact()
            if (intExponent.abs() <= BigInteger.valueOf(MAX_POWER_EXPONENT)) {
                return if (intExponent.signum() >= 0) {
                    base.pow(intExponent.toInt())
                } else {
                    if (base.compareTo(BigDecimal.ZERO) == 0) {
                        null
                    } else {
                        BigDecimal.ONE.divide(base.pow(-intExponent.toInt()), DIVIDE_SCALE, RoundingMode.HALF_UP)
                    }
                }
            }
        }
        val result = Math.pow(base.toDouble(), exponent.toDouble())
        return if (result.isNaN() || result.isInfinite()) null else BigDecimal.valueOf(result)
    }

    private fun updatePendingHistory() {
        val operator = pendingOperator ?: return
        val left = accumulator ?: BigDecimal.ZERO
        operationHistory = "${formatForHistory(left)}${operator.symbol}$display"
    }

    private fun appendCompletedHistoryEntry() {
        completedHistory += "$operationHistory$display"
    }

    private fun clearRepeatIfEditingStandaloneValue() {
        if (pendingOperator == null && startNewInput) {
            // Once the user starts editing a fresh value, repeat-equals no longer matches the visible input.
            repeatOperator = null
            repeatOperand = null
            operationHistory = ""
        }
    }

    private fun clearErrorIfNeeded() {
        if (error != null) {
            clear()
        }
    }

    private fun normalizeNegativeZero() {
        if ((display.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 0) && !display.endsWith(".")) {
            display = ZERO
        }
    }

    private fun format(value: BigDecimal): String {
        val rounded = value.round(MathContext(DISPLAY_PRECISION, RoundingMode.HALF_UP))
        val stripped = rounded.stripTrailingZeros()
        return if (stripped.compareTo(BigDecimal.ZERO) == 0) ZERO else stripped.toPlainString()
    }

    private fun formatForHistory(value: BigDecimal): String = format(value)

    private companion object {
        const val ZERO = "0"
        const val NEGATIVE_ZERO = "-0"
        const val DIVIDE_SCALE = 10
        const val DISPLAY_PRECISION = 12
        const val TRIG_EPSILON = 1e-12
        const val MAX_FACTORIAL = 1000L
        const val MAX_POWER_EXPONENT = 1000L
    }
}
