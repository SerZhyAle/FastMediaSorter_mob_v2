package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import timber.log.Timber

/**
 * S2392: builds the long-press menu rows for an App Functions cell - the fourth command kind the
 * desktop's long press serves, after the installed app, the resource and the channel.
 *
 * Not injected: like its siblings it is built by its host and bound to that host's lifetime, so no
 * toast it raises can outlive the surface behind it.
 */
class LauncherFeatureActionManager(
    private val activity: AppCompatActivity,
    private val runCommand: (LauncherCellCommand) -> Unit,
    private val settingsIntentOf: (routeKey: String) -> Intent?,
    private val pinToTaskbar: (LauncherCellCommand) -> Unit,
    private val removeDesktopCell: (cellId: Long) -> Unit,
) {

    /**
     * Rows in the owner's order (strategic ADR-2), which puts the destructive one last rather than
     * second as the neighbouring app menu does.
     *
     * A route with no settings screen contributes no row at all rather than a greyed one - the same
     * rule that keeps "App info" out of the app menu on a device that cannot show it (strategic 5.1.1).
     */
    fun rowsFor(command: LauncherCellCommand.Feature, cellId: Long): List<LauncherAppMenuRow> {
        val rows = mutableListOf<LauncherAppMenuRow>()
        rows += action(R.string.launcher_app_action_launch, R.drawable.ic_open_in_browse) {
            runCommand(command)
        }
        settingsIntentOf(command.routeKey)?.let { intent ->
            rows += action(R.string.launcher_feature_action_configure, R.drawable.ic_settings) {
                startSettings(intent)
            }
        }
        rows += action(R.string.launcher_app_action_pin_taskbar, R.drawable.ic_pin) {
            pinToTaskbar(command)
        }
        rows += action(R.string.remove_action, R.drawable.ic_delete) {
            removeDesktopCell(cellId)
        }
        Timber.d("S2392: feature menu built rows=%d route=%s", rows.size, command.routeKey)
        return rows
    }

    private fun action(labelRes: Int, iconRes: Int, onSelected: () -> Unit) =
        LauncherAppMenuRow.Action(activity.getString(labelRes), iconRes, onSelected)

    /**
     * The intent was resolved when the row was built, but the screen behind it can be gone by the time
     * the row is tapped, so the open still has to survive a missing target.
     */
    private fun startSettings(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.i(e, "Feature settings screen %s has no handler any more", intent.action)
            Toast.makeText(
                activity,
                R.string.launcher_app_shortcut_start_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
