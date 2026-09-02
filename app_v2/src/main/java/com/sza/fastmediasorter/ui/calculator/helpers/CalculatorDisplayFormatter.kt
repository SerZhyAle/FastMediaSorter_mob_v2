package com.sza.fastmediasorter.ui.calculator.helpers

/**
 * Turns the engine's raw display string into the text drawn on screen.
 *
 * Grouping lives here and never in [CalculatorEngine.display], because that string is also parsed on
 * input, saved in instance state, folded into the history line and handed to the launcher
 * integration (strategic S2024 ADR-1). DecimalFormat is deliberately not used: it groups the integer
 * part only, and the captured request asks for groups on both sides of the decimal point.
 */
class CalculatorDisplayFormatter {

    // Reused across keypresses; formatting runs on every render, so a per-call allocation would be
    // one throwaway buffer per digit typed.
    private val buffer = StringBuilder()

    fun format(raw: String, groupThousands: Boolean): String {
        if (!groupThousands || raw.isEmpty()) return raw

        val negative = raw.startsWith(MINUS_SIGN)
        val body = if (negative) raw.substring(1) else raw
        return if (body.isEmpty() || !isPlainNumber(body)) raw else grouped(body, negative)
    }

    private fun grouped(body: String, negative: Boolean): String {
        val separatorIndex = body.indexOf(DECIMAL_SEPARATOR)
        val hasFraction = separatorIndex >= 0
        val integerPart = if (hasFraction) body.substring(0, separatorIndex) else body

        buffer.setLength(0)
        if (negative) buffer.append(MINUS_SIGN)
        appendGroupedFromRight(integerPart)
        if (hasFraction) {
            buffer.append(DECIMAL_SEPARATOR)
            appendGroupedFromLeft(body.substring(separatorIndex + 1))
        }
        return buffer.toString()
    }

    private fun appendGroupedFromRight(digits: String) {
        val leading = digits.length % GROUP_SIZE
        for (index in digits.indices) {
            if (index > 0 && (index - leading) % GROUP_SIZE == 0) buffer.append(GROUP_SEPARATOR)
            buffer.append(digits[index])
        }
    }

    private fun appendGroupedFromLeft(digits: String) {
        for (index in digits.indices) {
            if (index > 0 && index % GROUP_SIZE == 0) buffer.append(GROUP_SEPARATOR)
            buffer.append(digits[index])
        }
    }

    // An error message or any other non-numeric text is returned untouched rather than mangled.
    private fun isPlainNumber(body: String): Boolean {
        var separators = 0
        for (character in body) {
            when {
                character.isDigit() -> Unit
                character == DECIMAL_SEPARATOR -> separators++
                else -> return false
            }
        }
        return separators <= 1
    }

    private companion object {
        const val GROUP_SIZE = 3
        const val GROUP_SEPARATOR = ','
        const val DECIMAL_SEPARATOR = '.'
        const val MINUS_SIGN = '-'
    }
}
