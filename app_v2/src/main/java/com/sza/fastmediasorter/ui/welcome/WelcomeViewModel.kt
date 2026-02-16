package com.sza.fastmediasorter.ui.welcome

import android.content.Context
import androidx.lifecycle.ViewModel
import com.sza.fastmediasorter.core.debug.StrictModeHelper
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
        private const val KEY_FIRST_RUN_AFTER_WELCOME = "first_run_after_welcome"
        private const val APP_PREFS_NAME = "app_prefs"
        private const val KEY_MEDIA_PERMISSIONS_GRANTED = "media_permissions_granted"
    }

    override fun getInitialState(): WelcomeState = WelcomeState()

    fun setWelcomeCompleted() {
        StrictModeHelper.allowDiskWrites {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_WELCOME_COMPLETED, true)
                .apply()
        }
    }

    fun isWelcomeCompleted(): Boolean {
        return StrictModeHelper.allowDiskReads {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_WELCOME_COMPLETED, false)
        }
    }

    /**
     * Check if this is the first app run after completing welcome screen.
     * Returns true only once - the first time after welcome completion.
     */
    fun isFirstRunAfterWelcome(): Boolean {
        return StrictModeHelper.allowDiskReads {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_FIRST_RUN_AFTER_WELCOME, true) // Default true = first run
        }
    }

    /**
     * Mark that the first run after welcome has been completed.
     * This ensures Settings opens only once after initial welcome.
     */
    fun setFirstRunCompleted() {
        StrictModeHelper.allowDiskWrites {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_FIRST_RUN_AFTER_WELCOME, false)
                .apply()
        }
    }

    fun setMediaPermissionsGranted(granted: Boolean) {
        StrictModeHelper.allowDiskWrites {
            context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MEDIA_PERMISSIONS_GRANTED, granted)
                .apply()
        }
    }
}

data class WelcomeState(val dummy: Boolean = false)
sealed class WelcomeEvent
