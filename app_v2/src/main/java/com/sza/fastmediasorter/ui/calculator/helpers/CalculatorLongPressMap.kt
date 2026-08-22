package com.sza.fastmediasorter.ui.calculator.helpers

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R

/**
 * S1719: the one place that says which key carries which long-press action.
 *
 * The assignment is the owner's, made on 2026-08-21 and written out in the strategic spec's key map;
 * it is mnemonic rather than frequency-based, because the app measures no usage frequency and a digit
 * that hints at its own function is remembered without reading the hint.
 *
 * Declarative on purpose. It names *what* should happen, never *how*: the keypad's host translates an
 * entry into the same call the corresponding menu row already makes, so a key and its menu twin can
 * never drift into two implementations of one function.
 *
 * A key absent from this table keeps exactly today's behaviour and gains no second action - the
 * backspace key is deliberately such a key, and so is every key not listed.
 */
internal object CalculatorLongPressMap {

    /**
     * What a long press asks for.
     *
     * Function and menu row are separate types even though both carry a menu id and both reach the
     * same dispatch: the distinction is what stops a reader from moving a destructive row onto a key
     * by mistaking one for the other.
     */
    sealed interface Action {

        /** What the key shows about this action, in small type under its own symbol. */
        val hint: Hint

        /** One of the functions offered by the function chooser. */
        data class Function(val itemId: Int, override val hint: Hint) : Action

        /** One row of the calculator's own action menu. */
        data class MenuCommand(val itemId: Int, override val hint: Hint) : Action

        /**
         * Type three zeroes at once. Neither a function nor a menu row - it is a typing shortcut, and
         * folding it into either of the other two would make the host guess which.
         */
        data object TripleZero : Action {
            override val hint: Hint = Hint.Notation("000")
        }
    }

    /**
     * A hint is either notation or a word, and the difference decides translation.
     *
     * `sin`, `n!` and `x²` are mathematical notation: the same in every language, and translating
     * them would make them wrong. `Copy` is a word and must be translated, so it arrives as a string
     * resource. Keeping both in one type is what lets a key render its hint without knowing which
     * kind it carries.
     */
    sealed interface Hint {
        data class Notation(val text: String) : Hint
        data class Word(@param:StringRes val res: Int) : Hint
    }

    /**
     * Key to action, exactly as the owner assigned it.
     *
     * Digits carry the functions, by mnemonic: 1 is one-over-x, 2 is squared, 3 is the cube root, and
     * so on. Operators carry the menu rows. Clearing the history is reachable only through the menu -
     * losing the history must not be one held finger away.
     */
    val entries: Map<Int, Action> = mapOf(
        R.id.btnCalculatorOne to Action.Function(FN_RECIPROCAL, Hint.Notation("1/x")),
        R.id.btnCalculatorTwo to Action.Function(FN_SQUARE, Hint.Notation("x²")),
        R.id.btnCalculatorThree to Action.Function(FN_CBRT, Hint.Notation("∛x")),
        R.id.btnCalculatorFour to Action.Function(FN_SQRT, Hint.Notation("√x")),
        R.id.btnCalculatorFive to Action.Function(FN_POWER, Hint.Notation("xʸ")),
        R.id.btnCalculatorSix to Action.Function(FN_FACTORIAL, Hint.Notation("n!")),
        R.id.btnCalculatorSeven to Action.Function(FN_SIN, Hint.Notation("sin")),
        R.id.btnCalculatorEight to Action.Function(FN_COS, Hint.Notation("cos")),
        R.id.btnCalculatorNine to Action.Function(FN_TAN, Hint.Notation("tg")),
        R.id.btnCalculatorToggleSign to Action.Function(FN_COT, Hint.Notation("ctg")),
        R.id.btnCalculatorDecimal to Action.Function(FN_PI, Hint.Notation("π")),
        R.id.btnCalculatorZero to Action.TripleZero,
        R.id.btnCalculatorAdd to Action.MenuCommand(MENU_COPY, Hint.Word(R.string.calculator_hint_copy)),
        R.id.btnCalculatorSubtract to Action.MenuCommand(MENU_PASTE, Hint.Word(R.string.calculator_hint_paste)),
        R.id.btnCalculatorMultiply to Action.MenuCommand(MENU_SHARE_RESULT, Hint.Word(R.string.calculator_hint_share)),
        R.id.btnCalculatorDivide to Action.MenuCommand(MENU_ROUND, Hint.Word(R.string.calculator_hint_round)),
        R.id.btnCalculatorPercent to Action.MenuCommand(MENU_SAVE_HISTORY, Hint.Word(R.string.calculator_hint_save)),
    )

    /** The action assigned to [viewId], or null when that key carries none. */
    fun actionFor(viewId: Int): Action? = entries[viewId]
}
