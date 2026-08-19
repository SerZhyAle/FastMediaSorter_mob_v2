package com.sza.fastmediasorter.wear.domain.calculator

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

private const val PRECISION_DIGITS = 16
private const val RESULT_SCALE = 10
private const val PERCENT_DIVISOR = 100L
private const val MAX_FACTORIAL_ARGUMENT = 200

private val MATH_CONTEXT = MathContext(PRECISION_DIGITS, RoundingMode.HALF_UP)

/**
 * Everything the watch calculator can do beyond counting.
 *
 * The owner ruled on 2026-08-19 that the watch carries the phone's whole function set; what changed
 * is where they are reached from, not how many there are. So this enum mirrors the phone's list and
 * a missing entry here is a defect, not a size decision.
 */
enum class WearCalculatorFunction {
    SINE,
    COSINE,
    TANGENT,
    COTANGENT,
    SQUARE_ROOT,
    CUBE_ROOT,
    SQUARE,
    RECIPROCAL,
    LOG10,
    NATURAL_LOG,
    FACTORIAL,
    PI,
    PERCENT,
    ROUND
}

/**
 * Applies [function] to [value], answering null where the result is undefined.
 *
 * Null rather than an exception, because the caller turns it into the engine's error state and the
 * user gets a display they can clear instead of a program that ended.
 */
internal fun evaluateFunction(function: WearCalculatorFunction, value: BigDecimal): BigDecimal? =
    when (function) {
        WearCalculatorFunction.SINE,
        WearCalculatorFunction.COSINE,
        WearCalculatorFunction.TANGENT,
        WearCalculatorFunction.COTANGENT -> evaluateTrigonometry(function, value)

        WearCalculatorFunction.SQUARE_ROOT,
        WearCalculatorFunction.CUBE_ROOT,
        WearCalculatorFunction.SQUARE,
        WearCalculatorFunction.RECIPROCAL -> evaluatePower(function, value)

        else -> evaluateRemaining(function, value)
    }

/** Degrees, like the phone: a watch user reading "sin 90 = 1" expects degrees, not radians. */
private fun evaluateTrigonometry(
    function: WearCalculatorFunction,
    value: BigDecimal
): BigDecimal? {
    val radians = Math.toRadians(value.toDouble())
    val raw = when (function) {
        WearCalculatorFunction.SINE -> Math.sin(radians)
        WearCalculatorFunction.COSINE -> Math.cos(radians)
        WearCalculatorFunction.TANGENT -> Math.tan(radians)
        else -> 1.0 / Math.tan(radians)
    }
    return normalize(raw)
}

private fun evaluatePower(function: WearCalculatorFunction, value: BigDecimal): BigDecimal? =
    when (function) {
        WearCalculatorFunction.SQUARE_ROOT ->
            if (value.signum() < 0) null else normalize(Math.sqrt(value.toDouble()))

        WearCalculatorFunction.CUBE_ROOT -> normalize(Math.cbrt(value.toDouble()))
        WearCalculatorFunction.SQUARE -> value.multiply(value, MATH_CONTEXT)
        else -> if (value.signum() == 0) null else BigDecimal.ONE.divide(value, MATH_CONTEXT)
    }

private fun evaluateRemaining(function: WearCalculatorFunction, value: BigDecimal): BigDecimal? =
    when (function) {
        WearCalculatorFunction.LOG10 ->
            if (value.signum() <= 0) null else normalize(Math.log10(value.toDouble()))

        WearCalculatorFunction.NATURAL_LOG ->
            if (value.signum() <= 0) null else normalize(Math.log(value.toDouble()))

        WearCalculatorFunction.FACTORIAL -> factorial(value)
        WearCalculatorFunction.PI -> normalize(Math.PI)
        WearCalculatorFunction.PERCENT -> value.divide(BigDecimal.valueOf(PERCENT_DIVISOR), MATH_CONTEXT)
        else -> value.setScale(0, RoundingMode.HALF_UP)
    }

/**
 * Defined for whole, non-negative arguments only, and capped.
 *
 * The cap is not a precision limit but a time one: the factorial of an arbitrary entered number would
 * otherwise block the watch's main work for as long as the user is willing to wait.
 */
private fun factorial(value: BigDecimal): BigDecimal? {
    val whole = runCatching { value.toBigIntegerExact() }.getOrNull()
    if (whole == null || whole.signum() < 0 || whole > BigInteger.valueOf(MAX_FACTORIAL_ARGUMENT.toLong())) {
        return null
    }
    var result = BigInteger.ONE
    var multiplier = BigInteger.TWO
    while (multiplier <= whole) {
        result = result.multiply(multiplier)
        multiplier = multiplier.inc()
    }
    return BigDecimal(result)
}

/** Trims the noise a double leaves behind, so sin 180 reads as 0 rather than 1.2E-16. */
private fun normalize(raw: Double): BigDecimal? {
    if (raw.isNaN() || raw.isInfinite()) {
        return null
    }
    return BigDecimal.valueOf(raw).setScale(RESULT_SCALE, RoundingMode.HALF_UP).stripTrailingZeros()
}
