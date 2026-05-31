package com.sza.fastmediasorter.ui.calculator.helpers

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class CalculatorEngine {

    enum class CalculatorError {
        DIVISION_BY_ZERO
    }

    private enum class Operator(val symbol: String) {
        ADD("+"),
        SUBTRACT("-"),
        MULTIPLY("×"),
        DIVIDE("÷");

        companion object {
            fun from(symbol: String): Operator = entries.firstOrNull { it.symbol == symbol }
                ?: throw IllegalArgumentException("Unsupported operator: $symbol")
        }
    }

    private data class PastedExpression(
        val numbers: List<BigDecimal>,
        val operators: List<Operator>,
    )

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
        val expression = parsePastedExpression(text) ?: return display
        if (expression.operators.isNotEmpty()) {
            return applyPastedExpression(expression)
        }
        val value = expression.numbers.firstOrNull() ?: return display
        display = format(value)
        startNewInput = false
        updatePendingHistory()
        return display
    }

    fun canParseInput(text: String): Boolean = parsePastedExpression(text) != null

    fun roundDisplay(): String {
        clearErrorIfNeeded()
        clearRepeatIfEditingStandaloneValue()
        val value = display.toBigDecimalOrNull() ?: BigDecimal.ZERO
        display = format(value.setScale(0, RoundingMode.HALF_UP))
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
        }
        accumulator = result
        display = format(result)
        startNewInput = true
        return true
    }

    private fun updatePendingHistory() {
        val operator = pendingOperator ?: return
        val left = accumulator ?: BigDecimal.ZERO
        operationHistory = "${formatForHistory(left)}${operator.symbol}$display"
    }

    private fun applyPastedExpression(expression: PastedExpression): String {
        var result = expression.numbers.first()
        val historyBuilder = StringBuilder(formatForHistory(result))

        expression.operators.forEachIndexed { index, operator ->
            val right = expression.numbers[index + 1]
            historyBuilder.append(operator.symbol).append(formatForHistory(right))
            result = when (operator) {
                Operator.ADD -> result.add(right)
                Operator.SUBTRACT -> result.subtract(right)
                Operator.MULTIPLY -> result.multiply(right)
                Operator.DIVIDE -> {
                    if (right.compareTo(BigDecimal.ZERO) == 0) {
                        error = CalculatorError.DIVISION_BY_ZERO
                        display = ZERO
                        accumulator = null
                        pendingOperator = null
                        repeatOperator = null
                        repeatOperand = null
                        startNewInput = true
                        operationHistory = "${historyBuilder}="
                        return display
                    }
                    result.divide(right, DIVIDE_SCALE, RoundingMode.HALF_UP)
                }
            }
        }

        accumulator = result
        pendingOperator = null
        repeatOperator = expression.operators.lastOrNull()
        repeatOperand = expression.numbers.lastOrNull()
        display = format(result)
        operationHistory = "${historyBuilder}="
        appendCompletedHistoryEntry()
        startNewInput = true
        return display
    }

    private fun parsePastedExpression(text: String): PastedExpression? {
        val numbers = mutableListOf<BigDecimal>()
        val operators = mutableListOf<Operator>()
        val currentNumber = StringBuilder()
        var hasDigit = false
        var hasDecimal = false

        fun resetCurrentNumber() {
            currentNumber.clear()
            hasDigit = false
            hasDecimal = false
        }

        fun appendCurrentNumber(): Boolean {
            if (!hasDigit) {
                resetCurrentNumber()
                return false
            }
            val value = currentNumber.toString().toBigDecimalOrNull() ?: run {
                resetCurrentNumber()
                return false
            }
            numbers += value
            resetCurrentNumber()
            return true
        }

        text.forEach { char ->
            when {
                char.isDigit() -> {
                    currentNumber.append(char)
                    hasDigit = true
                }
                char == '.' || char == ',' -> {
                    if (!hasDecimal) {
                        if (currentNumber.isEmpty() || currentNumber.toString() == "-") {
                            currentNumber.append('0')
                        }
                        currentNumber.append('.')
                        hasDecimal = true
                    }
                }
                else -> {
                    val operator = operatorForPastedChar(char)
                    if (operator != null) {
                        if (appendCurrentNumber()) {
                            operators += operator
                        } else if (operator == Operator.SUBTRACT && numbers.isNotEmpty() && operators.size == numbers.size) {
                            currentNumber.append('-')
                        } else if (numbers.isNotEmpty() && operators.size == numbers.size) {
                            operators[operators.lastIndex] = operator
                        } else if (operator == Operator.SUBTRACT && numbers.isEmpty() && currentNumber.isEmpty()) {
                            currentNumber.append('-')
                        }
                    }
                }
            }
        }

        appendCurrentNumber()
        while (operators.size >= numbers.size && operators.isNotEmpty()) {
            operators.removeAt(operators.lastIndex)
        }

        return if (numbers.isEmpty()) null else PastedExpression(numbers, operators)
    }

    private fun operatorForPastedChar(char: Char): Operator? = when (char) {
        '+' -> Operator.ADD
        '-', '−', '–', '—' -> Operator.SUBTRACT
        '*', 'x', 'X', '×' -> Operator.MULTIPLY
        '/', '÷', ':' -> Operator.DIVIDE
        else -> null
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
    }
}
