package com.sza.fastmediasorter.ui.browse.helpers

import android.app.Activity
import android.content.Intent
import com.sza.fastmediasorter.ui.main.MainActivity

/** Keeps launcher resource tasks from falling back to the application's main menu. */
object BrowseExitManager {
    private const val EXTRA_RETURN_TARGET = "browseReturnTarget"
    private const val LAUNCHER = "launcher"
    private const val HOME = "home"

    fun fromLauncher(intent: Intent): Intent = intent.putExtra(EXTRA_RETURN_TARGET, LAUNCHER)

    fun fromHomeShortcut(intent: Intent): Intent = intent.putExtra(EXTRA_RETURN_TARGET, HOME)

    fun finish(activity: Activity) {
        // Already pinned shortcuts keep their old intent after an application update.
        val returnTarget = activity.intent.getStringExtra(EXTRA_RETURN_TARGET)
            ?: if (activity.intent.action == Intent.ACTION_VIEW) HOME else null
        when (returnTarget) {
            HOME -> activity.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            // Launcher commands create a separate task; closing it reveals the desktop underneath.
            LAUNCHER -> Unit
            else -> if (activity.isTaskRoot) {
                activity.startActivity(
                    Intent(activity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
        }
        activity.finish()
    }
}
