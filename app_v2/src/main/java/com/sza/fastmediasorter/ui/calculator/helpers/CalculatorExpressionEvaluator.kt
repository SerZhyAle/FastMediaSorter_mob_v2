package com.sza.fastmediasorter.ui.calculator.helpers

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Evaluates arbitrary selected/pasted text as a math expression.
 *
 * Handles parentheses, operator precedence (`^` > unary `-`/`√` > `*` `/` `DIV` `mod` > `+` `-`),
 * implicit summation of whitespace-separated bare numbers, and locale-mixed decimal
 * separators. Non-math noise is stripped; a stream of malformed operators is sanitized
 * the same way the legacy paste parser tolerated it.
 */
object CalculatorExpressionEvaluator {

    sealed interface Result {
        /** [isExpression] is false for a single bare number paste (legacy "just set the entry" behavior). */
        data class Success(val value: BigDecimal, val canonical: String, val isExpression: Boolean) : Result
        data class Failure(val kind: Kind) : Result

        enum class Kind { PARSE, DIVIDE_BY_ZERO, DOMAIN }
    }

    private sealed interface Tok
    private data class Num(val value: BigDecimal) : Tok
    private data class Sym(val s: String) : Tok

    private val BINARY = setOf("+", "-", "*", "/", "^", "div", "mod")

    fun evaluate(text: String): Result {
        val lexed = lex(text)
        if (lexed.isEmpty()) return Result.Failure(Result.Kind.PARSE)
        val sanitized = sanitize(lexed)
        if (sanitized.isEmpty()) return Result.Failure(Result.Kind.PARSE)
        val tokens = insertImplicitPlus(sanitized)
        val numberCount = tokens.count { it is Num }
        if (numberCount == 0) return Result.Failure(Result.Kind.PARSE)
        val isExpression = tokens.size > 1
        return try {
            val value = Parser(tokens).parse()
            Result.Success(value, buildCanonical(tokens), isExpression)
        } catch (_: DivideByZeroException) {
            Result.Failure(Result.Kind.DIVIDE_BY_ZERO)
        } catch (_: DomainException) {
            Result.Failure(Result.Kind.DOMAIN)
        } catch (_: ParseException) {
            Result.Failure(Result.Kind.PARSE)
        }
    }

    private fun lex(text: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' || c == ',' -> {
                    val sb = StringBuilder()
                    while (i < n && (text[i].isDigit() || text[i] == '.' || text[i] == ',')) {
                        sb.append(text[i])
                        i++
                    }
                    val value = normalizeNumber(sb.toString())
                    if (value != null) out += Num(value)
                }
                c == '+' -> { out += Sym("+"); i++ }
                c == '-' || c == '−' || c == '–' || c == '—' -> { out += Sym("-"); i++ }
                c == '*' || c == 'x' || c == 'X' || c == '×' -> { out += Sym("*"); i++ }
                c == '/' || c == '÷' || c == ':' -> { out += Sym("/"); i++ }
                c == '^' -> { out += Sym("^"); i++ }
                c == '(' -> { out += Sym("("); i++ }
                c == ')' -> { out += Sym(")"); i++ }
                c == '√' -> { out += Sym("√"); i++ }
                text.regionMatches(i, "sqrt", 0, 4, ignoreCase = true) -> { out += Sym("√"); i += 4 }
                text.regionMatches(i, "div", 0, 3, ignoreCase = true) -> { out += Sym("div"); i += 3 }
                text.regionMatches(i, "mod", 0, 3, ignoreCase = true) -> { out += Sym("mod"); i += 3 }
                else -> i++
            }
        }
        return out
    }

    /** Per-number decimal normalization - strategic ADR-3. */
    private fun normalizeNumber(raw: String): BigDecimal? {
        val hasDot = raw.contains('.')
        val hasComma = raw.contains(',')
        val decimal: Char
        var s = raw
        when {
            hasDot && hasComma -> { s = s.replace(",", ""); decimal = '.' }
            hasComma -> decimal = ','
            else -> decimal = '.'
        }
        val lastIdx = s.lastIndexOf(decimal)
        if (lastIdx >= 0) {
            val intPart = s.substring(0, lastIdx).replace(decimal.toString(), "")
            val fracPart = s.substring(lastIdx + 1)
            s = if (fracPart.isEmpty()) intPart else "$intPart.$fracPart"
        }
        if (s.isEmpty() || s == ".") return null
        return s.toBigDecimalOrNull()
    }

    /** Collapse consecutive binary operators (keep last), drop leading/trailing noise operators. */
    private fun sanitize(toks: List<Tok>): List<Tok> {
        val out = mutableListOf<Tok>()
        for (tok in toks) {
            if (tok is Sym && tok.s in BINARY) {
                val last = out.lastOrNull()
                when {
                    last == null -> if (tok.s == "-") out += tok
                    last is Sym && last.s in BINARY ->
                        if (tok.s == "-") out += tok else out[out.lastIndex] = tok
                    last is Sym && last.s == "(" -> if (tok.s == "-") out += tok
                    else -> out += tok
                }
            } else {
                out += tok
            }
        }
        while (out.isNotEmpty()) {
            val last = out.last()
            if (last is Sym && last.s in BINARY) out.removeAt(out.lastIndex) else break
        }
        return out
    }

    private fun insertImplicitPlus(toks: List<Tok>): List<Tok> {
        val out = mutableListOf<Tok>()
        var prev: Tok? = null
        for (tok in toks) {
            if (prev != null && isValueEnd(prev) && isValueStart(tok)) out += Sym("+")
            out += tok
            prev = tok
        }
        return out
    }

    private fun isValueEnd(tok: Tok): Boolean = tok is Num || (tok is Sym && tok.s == ")")

    private fun isValueStart(tok: Tok): Boolean =
        tok is Num || (tok is Sym && (tok.s == "(" || tok.s == "√"))

    private fun buildCanonical(toks: List<Tok>): String {
        val sb = StringBuilder()
        for (tok in toks) {
            when (tok) {
                is Num -> sb.append(format(tok.value))
                is Sym -> sb.append(
                    when (tok.s) {
                        "*" -> "×"
                        "/" -> "÷"
                        "div" -> "DIV"
                        else -> tok.s
                    }
                )
            }
        }
        return sb.toString()
    }

    private class ParseException : Exception()
    private class DivideByZeroException : Exception()
    private class DomainException : Exception()

    private class Parser(private val toks: List<Tok>) {
        private var pos = 0

        fun parse(): BigDecimal {
            val value = expr()
            if (pos != toks.size) throw ParseException()
            return value
        }

        private fun peekSym(): String? = (toks.getOrNull(pos) as? Sym)?.s

        private fun expr(): BigDecimal {
            var value = term()
            while (peekSym() == "+" || peekSym() == "-") {
                val op = peekSym(); pos++
                val rhs = term()
                value = if (op == "+") value.add(rhs) else value.subtract(rhs)
            }
            return value
        }

        private fun term(): BigDecimal {
            var value = unary()
            while (peekSym() == "*" || peekSym() == "/" || peekSym() == "div" || peekSym() == "mod") {
                val op = peekSym(); pos++
                val rhs = unary()
                value = if (op == "*") {
                    value.multiply(rhs)
                } else if (op == "/" || op == "div") {
                    if (rhs.compareTo(BigDecimal.ZERO) == 0) throw DivideByZeroException()
                    if (op == "/") {
                        value.divide(rhs, DIVIDE_SCALE, RoundingMode.HALF_UP)
                    } else {
                        value.divideToIntegralValue(rhs)
                    }
                } else {
                    if (rhs.compareTo(BigDecimal.ZERO) == 0) throw DivideByZeroException()
                    value.remainder(rhs)
                }
            }
            return value
        }

        private fun unary(): BigDecimal = when (peekSym()) {
            "-" -> { pos++; unary().negate() }
            "+" -> { pos++; unary() }
            "√" -> {
                pos++
                val operand = unary()
                if (operand.signum() < 0) throw DomainException()
                BigDecimal.valueOf(Math.sqrt(operand.toDouble()))
            }
            else -> powerExpr()
        }

        private fun powerExpr(): BigDecimal {
            val base = primary()
            if (peekSym() == "^") {
                pos++
                val exponent = unary()
                return power(base, exponent) ?: throw DomainException()
            }
            return base
        }

        private fun primary(): BigDecimal {
            val tok = toks.getOrNull(pos) ?: throw ParseException()
            return when {
                tok is Num -> { pos++; tok.value }
                tok is Sym && tok.s == "(" -> {
                    pos++
                    val value = expr()
                    if (peekSym() != ")") throw ParseException()
                    pos++
                    value
                }
                else -> throw ParseException()
            }
        }
    }

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

    private fun format(value: BigDecimal): String {
        val rounded = value.round(MathContext(DISPLAY_PRECISION, RoundingMode.HALF_UP))
        val stripped = rounded.stripTrailingZeros()
        return if (stripped.compareTo(BigDecimal.ZERO) == 0) "0" else stripped.toPlainString()
    }

    private const val DIVIDE_SCALE = 10
    private const val DISPLAY_PRECISION = 12
    private const val MAX_POWER_EXPONENT = 1000L
}
