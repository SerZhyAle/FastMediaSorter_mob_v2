package com.sza.fastmediasorter.ui.calculator.helpers

/**
 * S1719: the calculator's own menu-item numbering, lifted out of the input manager's private
 * companion so that two callers can name the same row.
 *
 * It used to be private, which was right while the menu was the only way to reach these actions. The
 * long-press table (`CalculatorLongPressMap`) is now a second way, and it has to point at the same
 * rows - the alternative was a translation table between two numberings, which is a second thing to
 * keep in step and the exact drift this ticket's design forbids.
 *
 * Top-level and package-visible on purpose: every reference in this package stays unqualified, so
 * moving them here changed no call site.
 */

internal const val MENU_COPY = 1
internal const val MENU_PASTE = 2
internal const val MENU_ROUND = 3
internal const val MENU_SHARE_RESULT = 4
internal const val MENU_SAVE_HISTORY = 5

/** Destructive, and deliberately reachable only from the menu - never from a held key. */
internal const val MENU_CLEAR_HISTORY = 6

internal const val MENU_FUNCTION = 7

internal const val FN_SIN = 100
internal const val FN_COS = 101
internal const val FN_TAN = 102
internal const val FN_COT = 103
internal const val FN_SQRT = 104
internal const val FN_CBRT = 105
internal const val FN_SQUARE = 106
internal const val FN_POWER = 107
internal const val FN_RECIPROCAL = 108
internal const val FN_LOG10 = 109
internal const val FN_LN = 110
internal const val FN_FACTORIAL = 111
internal const val FN_PI = 112
internal const val FN_MOD = 113
internal const val FN_INTEGER_DIVIDE = 114
