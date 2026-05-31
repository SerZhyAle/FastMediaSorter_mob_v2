package com.sza.fastmediasorter.core.game

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.ui.game.GameActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity

object GameLaunchIntents {
    fun game(context: Context): Intent = Intent(context, GameActivity::class.java)

    fun settingsGameToggle(context: Context): Intent = Intent(context, SettingsActivity::class.java).apply {
        putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_PLAYBACK)
        putExtra(SettingsActivity.EXTRA_HIGHLIGHT_SETTING, SettingsActivity.HIGHLIGHT_EMBEDDED_GAME)
    }
}