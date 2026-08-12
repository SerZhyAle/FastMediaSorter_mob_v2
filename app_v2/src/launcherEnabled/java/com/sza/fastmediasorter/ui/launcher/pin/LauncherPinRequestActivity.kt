package com.sza.fastmediasorter.ui.launcher.pin

import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherPinRequestManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherPinRequestOutcome
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S1205 - transparent, no-UI host for `LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT`: the request another
 * app makes when the user picks "add to home screen" for a place, a route or a person.
 *
 * There is no confirmation dialog by owner ruling, but the activity itself is not optional - Android
 * delivers this action to the default launcher as an activity, and a pin cannot be accepted without
 * one. So it draws nothing: it hands the request to [LauncherPinRequestManager], says which of the
 * three outcomes happened and finishes. Mirrors the trampoline recipe of
 * [com.sza.fastmediasorter.widget.CameraLaunchActivity] (Rule 3 - no business logic here).
 *
 * The component ships enabled unlike the HOME activity beside it: the system routes this action only
 * to the launcher currently holding the home role, so it is inert until that is us.
 */
@AndroidEntryPoint
class LauncherPinRequestActivity : AppCompatActivity() {

    @Inject
    lateinit var pinRequestManager: LauncherPinRequestManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        lifecycleScope.launch {
            val outcome = pinRequestManager.handle(intent, isLandscape, System.currentTimeMillis())
            // Silence would read as success; a desktop that could not take the cell must say so.
            messageFor(outcome)?.let { message ->
                // Application context: the Toast outlives this activity, which ends on the next line.
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    /** Null for a request that was never ours - nothing was promised, so nothing is reported. */
    @StringRes
    private fun messageFor(outcome: LauncherPinRequestOutcome): Int? = when (outcome) {
        LauncherPinRequestOutcome.NOT_OURS -> null
        LauncherPinRequestOutcome.PLACED -> R.string.launcher_widget_placed
        LauncherPinRequestOutcome.NOT_PLACED -> R.string.launcher_widget_no_room
    }
}
