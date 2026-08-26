package com.sza.fastmediasorter.ui.calculator.helpers

import android.content.Context

/**
 * Screen-local persistence for [CalculatorSettings].
 *
 * Backed by its own SharedPreferences file, matching [CalculatorMemoryStore] and
 * CalculatorHistoryScaleManager rather than the app-wide settings store: these four options are
 * reachable only from the calculator's own menu (strategic S2024 ADR-2).
 */
class CalculatorSettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): CalculatorSettings = CalculatorSettings(
        groupThousands = prefs.getBoolean(KEY_GROUP_THOUSANDS, CalculatorSettings.DEFAULT.groupThousands),
        displayTextSizeSp = prefs.getInt(KEY_TEXT_SIZE_SP, CalculatorSettings.DEFAULT.displayTextSizeSp),
        keypadMode = CalculatorKeypadMode.fromNameOrDefault(prefs.getString(KEY_KEYPAD_MODE, null)),
    ).coerced()

    fun save(settings: CalculatorSettings) {
        val safe = settings.coerced()
        prefs.edit()
            .putBoolean(KEY_GROUP_THOUSANDS, safe.groupThousands)
            .putInt(KEY_TEXT_SIZE_SP, safe.displayTextSizeSp)
            .putString(KEY_KEYPAD_MODE, safe.keypadMode.name)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "calculator_settings"
        const val KEY_GROUP_THOUSANDS = "group_thousands"
        const val KEY_TEXT_SIZE_SP = "display_text_size_sp"
        const val KEY_KEYPAD_MODE = "keypad_mode"
    }
}
