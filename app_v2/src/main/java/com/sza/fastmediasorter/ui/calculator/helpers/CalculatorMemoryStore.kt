package com.sza.fastmediasorter.ui.calculator.helpers

import android.content.Context

/**
 * Small scalar store for the calculator memory register and the memory-row collapsed/expanded flag.
 *
 * Backed by a dedicated SharedPreferences file. Memory survives between sessions (strategic S0331 ADR-1);
 * the row-expanded flag defaults to collapsed (ADR-3).
 */
class CalculatorMemoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadMemory(): String? = prefs.getString(KEY_MEMORY, null)

    fun saveMemory(value: String) {
        prefs.edit().putString(KEY_MEMORY, value).apply()
    }

    fun loadRowExpanded(): Boolean = prefs.getBoolean(KEY_ROW_EXPANDED, false)

    fun saveRowExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_ROW_EXPANDED, expanded).apply()
    }

    private companion object {
        const val PREFS_NAME = "calculator_memory"
        const val KEY_MEMORY = "memory_value"
        const val KEY_ROW_EXPANDED = "memory_row_expanded"
    }
}
