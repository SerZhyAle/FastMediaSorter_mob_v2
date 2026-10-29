package com.sza.fastmediasorter.domain.model

/**
 * S4405: the one place that decides which custom broadcast link password is accepted. The rule moves together
 * with the LIVE-BROADCAST contract, so no caller restates it.
 *
 * No complexity rule exists by the owner's decision; only what breaks the password on the wire is refused.
 * Length is counted in code points, so a supplementary character counts once, and nothing is trimmed - a
 * space the person typed is part of the password.
 */
object BroadcastLinkPasswordRules {

    const val MAX_CODE_POINTS = 64

    // Below this the screen shows a one-line hint; the password is still accepted.
    private const val WEAK_BELOW_CODE_POINTS = 8

    enum class Verdict { OK, EMPTY, TOO_LONG, CONTROL_CHARACTER }

    fun validate(text: String): Verdict = when {
        text.isEmpty() -> Verdict.EMPTY
        text.codePointCount(0, text.length) > MAX_CODE_POINTS -> Verdict.TOO_LONG
        hasControlCharacter(text) -> Verdict.CONTROL_CHARACTER
        else -> Verdict.OK
    }

    fun isWeak(text: String): Boolean = text.codePointCount(0, text.length) < WEAK_BELOW_CODE_POINTS

    // String.codePoints() needs API 24 and the legacy flavor runs on 23.
    private fun hasControlCharacter(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (Character.isISOControl(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }
}
