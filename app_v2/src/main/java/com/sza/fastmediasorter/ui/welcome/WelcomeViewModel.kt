package com.sza.fastmediasorter.ui.welcome

import android.content.Context
import androidx.lifecycle.ViewModel
import com.sza.fastmediasorter.core.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : BaseViewModel<WelcomeState, WelcomeEvent>() {

    companion object {
        private const val PREFS_NAME = "welcome_prefs"
        private const val KEY_WELCOME_COMPLETED = "welcome_completed"
    }

    override fun getInitialState(): WelcomeState = WelcomeState()

    fun setWelcomeCompleted() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WELCOME_COMPLETED, true)
            .apply()
    }

    fun isWelcomeCompleted(): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WELCOME_COMPLETED, false)
    }
}

data class WelcomeState(val dummy: Boolean = false)
sealed class WelcomeEvent
