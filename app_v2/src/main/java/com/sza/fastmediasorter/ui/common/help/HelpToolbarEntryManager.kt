package com.sza.fastmediasorter.ui.common.help

import android.os.SystemClock
import android.view.MenuItem
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.common.widget.StandardToolbar

/**
 * S4328: the "?" header action of a StandardToolbar screen whose host has no options menu of its
 * own (contract `CONTEXT-HELP` rule 2). [install] does nothing for a null [surfaceId] - the id
 * comes from the host's `BaseActivity.getHelpSurfaceId()`, which is null exactly when the help map
 * gives the surface no page - so the map, not the screen, decides whether the action exists.
 * Hosts that call `setSupportActionBar` take the options-menu path instead and route the click
 * through `BaseActivity`.
 */
object HelpToolbarEntryManager {

    fun install(
        toolbar: StandardToolbar,
        activity: FragmentActivity,
        surfaceId: String?,
        uiSurface: UiSurface?,
    ) {
        if (surfaceId == null) return
        toolbar.inflateMenu(R.menu.menu_help_action)
        var lastOpenAt = 0L
        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId != R.id.action_help) return@setOnMenuItemClickListener false
            val now = SystemClock.uptimeMillis()
            if (now - lastOpenAt < DOUBLE_TAP_WINDOW_MS) return@setOnMenuItemClickListener true
            lastOpenAt = now
            HelpOpenManager.open(activity, surfaceId, uiSurface)
            true
        }
    }

    private const val DOUBLE_TAP_WINDOW_MS = 600L
}
