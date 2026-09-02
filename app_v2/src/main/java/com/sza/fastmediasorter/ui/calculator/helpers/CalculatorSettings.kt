package com.sza.fastmediasorter.ui.calculator.helpers

/**
 * Keypad geometry chosen by the user (strategic S2024 §0 points 9-10).
 *
 * NORMAL leaves the layout as declared in XML. LARGE collapses the history and lets the six button
 * rows divide the remaining height. COMPACT pins a fixed-size keypad to the bottom-end corner for
 * one-handed use.
 */
enum class CalculatorKeypadMode {
    NORMAL,
    LARGE,
    COMPACT,
    ;

    companion object {
        /** Unknown persisted values read back as NORMAL rather than throwing on a downgrade. */
        fun fromNameOrDefault(name: String?): CalculatorKeypadMode =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/**
 * The four calculator display options carried as one unit so the settings dialog can edit a draft
 * and apply it in a single write (strategic S2024 ADR-2).
 */
data class CalculatorSettings(
    val groupThousands: Boolean,
    val displayTextSizeSp: Int,
    val keypadMode: CalculatorKeypadMode,
) {

    /** Clamps a persisted or dialog-supplied text size into the range the display can actually render. */
    fun coerced(): CalculatorSettings =
        copy(displayTextSizeSp = displayTextSizeSp.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP))

    companion object {
        const val MIN_TEXT_SIZE_SP = 16
        const val MAX_TEXT_SIZE_SP = 72

        /** Matches R.dimen.calculator_display_text_size, the size the display used before S2024. */
        const val DEFAULT_TEXT_SIZE_SP = 40

        val DEFAULT = CalculatorSettings(
            groupThousands = true,
            displayTextSizeSp = DEFAULT_TEXT_SIZE_SP,
            keypadMode = CalculatorKeypadMode.NORMAL,
        )
    }
}
