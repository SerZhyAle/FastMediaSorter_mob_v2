package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import androidx.annotation.ColorInt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the local clock gadget's display choices outside a desktop cell, so replacing or restoring a
 * cell never discards a choice made on another instance of the same gadget.
 */
@Singleton
class ClockGadgetStateStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): ClockGadgetDisplayState = ClockGadgetDisplayState(
        secondsVisible = preferences.getBoolean(KEY_SECONDS_VISIBLE, true),
        dialColor = preferences.takeIf { it.contains(KEY_DIAL_COLOR) }?.getInt(KEY_DIAL_COLOR, 0),
        dialTypefaceName = preferences.getString(KEY_DIAL_TYPEFACE, DEFAULT_DIAL_TYPEFACE)
            ?: DEFAULT_DIAL_TYPEFACE,
    )

    fun setSecondsVisible(visible: Boolean) {
        preferences.edit().putBoolean(KEY_SECONDS_VISIBLE, visible).apply()
    }

    fun setDialColor(@ColorInt color: Int?) {
        preferences.edit().apply {
            if (color == null) {
                remove(KEY_DIAL_COLOR)
            } else {
                putInt(KEY_DIAL_COLOR, color)
            }
            apply()
        }
    }

    fun setDialTypefaceName(typefaceName: String) {
        preferences.edit().putString(KEY_DIAL_TYPEFACE, typefaceName).apply()
    }

    internal companion object {
        const val PREFERENCES_NAME = "launcher_clock_gadget"
        const val KEY_SECONDS_VISIBLE = "seconds_visible"
        const val KEY_DIAL_COLOR = "dial_color"
        const val KEY_DIAL_TYPEFACE = "dial_typeface"
        const val DEFAULT_DIAL_TYPEFACE = "default"
    }
}

data class ClockGadgetDisplayState(
    val secondsVisible: Boolean,
    @ColorInt val dialColor: Int?,
    val dialTypefaceName: String,
)
