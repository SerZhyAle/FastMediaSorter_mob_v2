package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.ColorInt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
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

    private val preferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun read(): ClockGadgetDisplayState = ClockGadgetDisplayState(
        secondsVisible = preferences.getBoolean(KEY_SECONDS_VISIBLE, true),
        dialColor = preferences.takeIf { it.contains(KEY_DIAL_COLOR) }?.getInt(KEY_DIAL_COLOR, 0),
        dialTypefaceName = preferences.getString(KEY_DIAL_TYPEFACE, DEFAULT_DIAL_TYPEFACE)
            ?: DEFAULT_DIAL_TYPEFACE,
    )

    /**
     * S3557: the current state on collect and after every write, so the watch face follows a dial
     * gesture without the gadget knowing a watch exists.
     *
     * The listener is referenced from the flow's own closure: SharedPreferences keeps listeners only
     * weakly, and a listener held nowhere else would be collected and fall silent.
     */
    fun changes(): Flow<ClockGadgetDisplayState> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        send(read())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.flowOn(Dispatchers.IO)

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
