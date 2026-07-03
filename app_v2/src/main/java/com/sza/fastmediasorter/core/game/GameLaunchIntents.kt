package com.sza.fastmediasorter.core.game

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.ui.game.GameActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity

object GameLaunchIntents {
    fun game(context: Context): Intent = Intent(context, GameActivity::class.java)

    // S0829: the embedded-game toggle lives in the Operations tab's Additional Programs group, so reuse
    // the S0780 deep-link that opens that tab and expands + scrolls to the group (the prior TAB_PLAYBACK
    // + EXTRA_HIGHLIGHT_SETTING pair opened the wrong tab and the highlight extra was never consumed).
    fun settingsGameToggle(context: Context): Intent = SettingsActivity.openProgramsSectionIntent(context)
}